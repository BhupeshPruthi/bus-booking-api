const { db } = require('../config/database');
const { NotFoundError, ValidationError, ConflictError } = require('../utils/errors');

const DEFAULT_POOJA_CITY = 'Delhi - NCR';
const POOJA_ACTIVE_WINDOW_HOURS = 8;
const POOJA_ACTIVE_WINDOW_MS = POOJA_ACTIVE_WINDOW_HOURS * 60 * 60 * 1000;
const BOOKING_STATUS = {
  NOT_STARTED: 'not_started',
  OPEN: 'open',
  FULL: 'full',
  EXPIRED: 'expired',
};

class PoojaService {
  // ============ ADMIN ============

  async createPooja(adminUserId, data) {
    const [pooja] = await db('poojas')
      .insert({
        scheduled_at: data.scheduledAt,
        place: data.place,
        total_tokens: data.totalTokens,
        status: 'scheduled',
        created_by: adminUserId,
      })
      .returning('*');

    return this.getPoojaById(pooja.id);
  }

  async getAdminUpcomingPoojas() {
    return this.getUpcomingPoojas();
  }

  async getAdminPoojaById(poojaId) {
    const pooja = await db('poojas').where('id', poojaId).first();
    if (!pooja) throw new NotFoundError('Pooja');

    const bookedTokens = await this.getBookedTokensCount(poojaId);

    const bookings = await db('pooja_bookings as pb')
      .leftJoin('users as u', 'pb.user_id', 'u.id')
      .select(
        'pb.*',
        'u.mobile as user_mobile',
        'u.name as user_name'
      )
      .where('pb.pooja_id', poojaId)
      .orderBy('pb.created_at', 'desc');

    return {
      ...this.formatPooja(pooja, bookedTokens),
      bookings: bookings.map((b) => this.formatAdminBooking(b)),
    };
  }

  // ============ CONSUMER / PUBLIC ============

  async getUpcomingPoojas() {
    const now = new Date();
    const expiryCutoff = new Date(now.getTime() - POOJA_ACTIVE_WINDOW_MS);
    const poojas = await db('poojas')
      .where('status', 'scheduled')
      .andWhere('scheduled_at', '>', expiryCutoff)
      .orderBy('scheduled_at', 'asc');

    const ids = poojas.map((p) => p.id);
    const bookedMap = await this.getBookedTokensCountForPoojas(ids);

    return poojas.map((p) => this.formatPooja(p, bookedMap[p.id] || 0));
  }

  async getPoojaById(poojaId) {
    const pooja = await db('poojas').where('id', poojaId).first();
    if (!pooja) throw new NotFoundError('Pooja');

    const bookedTokens = await this.getBookedTokensCount(poojaId);
    return this.formatPooja(pooja, bookedTokens);
  }

  async bookToken(poojaId, userId, data) {
    return db.transaction(async (trx) => {
      const memberCount = parseInt(data.memberCount ?? 1, 10);
      const city = typeof data.city === 'string' && data.city.trim()
        ? data.city.trim()
        : DEFAULT_POOJA_CITY;

      if (!Number.isInteger(memberCount) || memberCount < 1 || memberCount > 10) {
        throw new ValidationError('Members must be between 1 and 10');
      }

      const pooja = await trx('poojas')
        .where('id', poojaId)
        .forUpdate()
        .first();

      if (!pooja) throw new NotFoundError('Pooja');

      if (pooja.status !== 'scheduled') {
        throw new ValidationError('This pooja is not available for booking');
      }

      const bookingWindow = this.getBookingWindow(pooja, 0);
      if (bookingWindow.bookingStatus === BOOKING_STATUS.NOT_STARTED) {
        throw new ValidationError('Pooja booking has not started yet');
      }
      if (bookingWindow.bookingStatus === BOOKING_STATUS.EXPIRED) {
        throw new ValidationError('Cannot book a pooja after it has expired');
      }

      const countRow = await trx('pooja_bookings')
        .where('pooja_id', poojaId)
        .where('status', 'confirmed')
        .count('id as count')
        .first();
      const bookedTokens = parseInt(countRow?.count || 0, 10);
      const currentBookingWindow = this.getBookingWindow(pooja, bookedTokens);
      if (currentBookingWindow.bookingStatus === BOOKING_STATUS.NOT_STARTED) {
        throw new ValidationError('Pooja booking has not started yet');
      }
      if (currentBookingWindow.bookingStatus === BOOKING_STATUS.EXPIRED) {
        throw new ValidationError('Cannot book a pooja after it has expired');
      }

      const existingUserBooking = await trx('pooja_bookings')
        .where('pooja_id', poojaId)
        .where('user_id', userId)
        .where('status', 'confirmed')
        .first();

      if (existingUserBooking) {
        throw new ConflictError('You already have a token for this pooja');
      }

      if (bookedTokens + 1 > pooja.total_tokens) {
        throw new ConflictError('No tokens available for this pooja');
      }

      const confirmedBookings = await trx('pooja_bookings')
        .where('pooja_id', poojaId)
        .where('status', 'confirmed');
      const reservedTokenNumbers = new Set(
        confirmedBookings
          .map((booking) => parseInt(booking.token_number, 10))
          .filter((tokenNumber) => Number.isInteger(tokenNumber))
      );
      let tokenNumber = null;
      for (let candidate = 1; candidate <= pooja.total_tokens; candidate += 1) {
        if (!reservedTokenNumbers.has(candidate)) {
          tokenNumber = candidate;
          break;
        }
      }

      if (!tokenNumber) {
        throw new ConflictError('No tokens available for this pooja');
      }

      let booking;
      try {
        [booking] = await trx('pooja_bookings')
          .insert({
            pooja_id: poojaId,
            user_id: userId,
            name: data.name,
            phone: data.phone,
            member_count: memberCount,
            city,
            token_number: tokenNumber,
            status: 'confirmed',
          })
          .returning('*');
      } catch (error) {
        const uniqueTarget = String(error?.constraint || error?.message || '');
        if (error?.code === '23505' && uniqueTarget.includes('pooja_bookings_confirmed_user_unique')) {
          throw new ConflictError('You already have a token for this pooja');
        }
        throw error;
      }

      return this.formatBooking(booking);
    });
  }

  async cancelBookingAsAdmin(poojaId, bookingId, adminUserId) {
    return db.transaction(async (trx) => {
      const pooja = await trx('poojas')
        .where('id', poojaId)
        .forUpdate()
        .first();

      if (!pooja) throw new NotFoundError('Pooja');

      if (this.isPoojaExpired(pooja.scheduled_at)) {
        throw new ValidationError('Cannot cancel a token after the pooja has expired');
      }

      const booking = await trx('pooja_bookings')
        .where('id', bookingId)
        .where('pooja_id', poojaId)
        .forUpdate()
        .first();

      if (!booking) throw new NotFoundError('Pooja booking');

      if (booking.status !== 'confirmed') {
        throw new ValidationError(`Cannot cancel token with status: ${booking.status}`);
      }

      const [updated] = await trx('pooja_bookings')
        .where('id', bookingId)
        .update({
          status: 'cancelled',
          cancelled_at: new Date(),
          cancelled_by: adminUserId,
          updated_at: new Date(),
        })
        .returning('*');

      return this.formatBooking(updated);
    });
  }

  // ============ HELPERS ============

  getBookingWindow(pooja, bookedTokens, now = new Date()) {
    const scheduledTime = new Date(pooja.scheduled_at).getTime();
    const totalTokens = pooja.total_tokens;
    const availableTokens = Math.max(0, totalTokens - bookedTokens);

    if (Number.isNaN(scheduledTime)) {
      return {
        bookingStatus: BOOKING_STATUS.EXPIRED,
        bookingOpensAt: pooja.scheduled_at,
        bookingClosesAt: pooja.scheduled_at,
        canBook: false,
      };
    }

    const bookingClosesAt = new Date(scheduledTime + POOJA_ACTIVE_WINDOW_MS);
    const nowTime = now.getTime();
    let bookingStatus = BOOKING_STATUS.OPEN;

    if (nowTime < scheduledTime) {
      bookingStatus = BOOKING_STATUS.NOT_STARTED;
    } else if (nowTime >= bookingClosesAt.getTime()) {
      bookingStatus = BOOKING_STATUS.EXPIRED;
    } else if (availableTokens <= 0) {
      bookingStatus = BOOKING_STATUS.FULL;
    }

    return {
      bookingStatus,
      bookingOpensAt: pooja.scheduled_at,
      bookingClosesAt,
      canBook: bookingStatus === BOOKING_STATUS.OPEN,
    };
  }

  isPoojaExpired(scheduledAt, now = new Date()) {
    const scheduledTime = new Date(scheduledAt).getTime();
    if (Number.isNaN(scheduledTime)) return true;
    return scheduledTime + POOJA_ACTIVE_WINDOW_MS <= now.getTime();
  }

  async getBookedTokensCount(poojaId) {
    const row = await db('pooja_bookings')
      .where('pooja_id', poojaId)
      .where('status', 'confirmed')
      .count('id as count')
      .first();
    return parseInt(row?.count || 0, 10);
  }

  async getBookedTokensCountForPoojas(poojaIds) {
    if (!poojaIds || poojaIds.length === 0) return {};

    const rows = await db('pooja_bookings')
      .select('pooja_id')
      .count('id as count')
      .whereIn('pooja_id', poojaIds)
      .where('status', 'confirmed')
      .groupBy('pooja_id');

    return rows.reduce((acc, r) => {
      acc[r.pooja_id] = parseInt(r.count, 10);
      return acc;
    }, {});
  }

  formatPooja(pooja, bookedTokens) {
    const totalTokens = pooja.total_tokens;
    const availableTokens = Math.max(0, totalTokens - bookedTokens);
    const bookingWindow = this.getBookingWindow(pooja, bookedTokens);

    return {
      id: pooja.id,
      scheduledAt: pooja.scheduled_at,
      place: pooja.place,
      totalTokens: totalTokens,
      bookedTokens,
      availableTokens,
      status: pooja.status,
      createdAt: pooja.created_at,
      bookingStatus: bookingWindow.bookingStatus,
      bookingOpensAt: bookingWindow.bookingOpensAt,
      bookingClosesAt: bookingWindow.bookingClosesAt,
      canBook: bookingWindow.canBook,
    };
  }

  formatBooking(booking) {
    return {
      id: booking.id,
      poojaId: booking.pooja_id,
      userId: booking.user_id,
      name: booking.name,
      phone: booking.phone,
      memberCount: booking.member_count || 1,
      city: booking.city || DEFAULT_POOJA_CITY,
      tokenNumber: booking.token_number,
      status: booking.status,
      cancelledAt: booking.cancelled_at,
      createdAt: booking.created_at,
    };
  }

  formatAdminBooking(row) {
    return {
      id: row.id,
      poojaId: row.pooja_id,
      userId: row.user_id,
      name: row.name,
      phone: row.phone,
      memberCount: row.member_count || 1,
      city: row.city || DEFAULT_POOJA_CITY,
      tokenNumber: row.token_number,
      status: row.status,
      cancelledAt: row.cancelled_at,
      createdAt: row.created_at,
      user: row.user_mobile
        ? { mobile: row.user_mobile, name: row.user_name }
        : undefined,
    };
  }
}

module.exports = new PoojaService();
