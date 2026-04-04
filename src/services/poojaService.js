const { db } = require('../config/database');
const { NotFoundError, ValidationError, ConflictError } = require('../utils/errors');

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
    const poojas = await db('poojas')
      .where('status', 'scheduled')
      .andWhere('scheduled_at', '>', now)
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
      const pooja = await trx('poojas')
        .where('id', poojaId)
        .forUpdate()
        .first();

      if (!pooja) throw new NotFoundError('Pooja');

      if (pooja.status !== 'scheduled') {
        throw new ValidationError('This pooja is not available for booking');
      }

      if (new Date(pooja.scheduled_at) <= new Date()) {
        throw new ValidationError('Cannot book a pooja that has already started');
      }

      const countRow = await trx('pooja_bookings')
        .where('pooja_id', poojaId)
        .where('status', 'confirmed')
        .count('id as count')
        .first();
      const bookedTokens = parseInt(countRow?.count || 0, 10);

      if (bookedTokens >= pooja.total_tokens) {
        throw new ConflictError('No tokens available for this pooja');
      }

      const [booking] = await trx('pooja_bookings')
        .insert({
          pooja_id: poojaId,
          user_id: userId,
          name: data.name,
          phone: data.phone,
          status: 'confirmed',
        })
        .returning('*');

      return this.formatBooking(booking);
    });
  }

  // ============ HELPERS ============

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

    return {
      id: pooja.id,
      scheduledAt: pooja.scheduled_at,
      place: pooja.place,
      totalTokens: totalTokens,
      bookedTokens,
      availableTokens,
      status: pooja.status,
      createdAt: pooja.created_at,
    };
  }

  formatBooking(booking) {
    return {
      id: booking.id,
      poojaId: booking.pooja_id,
      userId: booking.user_id,
      name: booking.name,
      phone: booking.phone,
      status: booking.status,
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
      status: row.status,
      createdAt: row.created_at,
      user: row.user_mobile
        ? { mobile: row.user_mobile, name: row.user_name }
        : undefined,
    };
  }
}

module.exports = new PoojaService();

