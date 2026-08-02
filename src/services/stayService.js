const crypto = require('node:crypto');
const { db } = require('../config/database');
const stayCouponService = require('./stayCouponService');
const {
  NotFoundError,
  ValidationError,
  ConflictError,
} = require('../utils/errors');

const REFUND_CUTOFF_HOURS = 48;
const CHECK_IN_HOUR_IST = 12;
const CHECK_OUT_HOUR_IST = 11;
const MAX_NIGHTS = 7;
const UNIT_TYPE_CODES = ['three_bed_room', 'four_bed_room', 'five_bed_room', 'hall'];
const INVENTORY_HOLDING_STATUSES = ['confirmed', 'cancellation_requested'];
const TERMINAL_STATUSES = ['rejected', 'cancelled', 'completed'];
const STAY_INVENTORY_ADVISORY_LOCK = 20260725;
const DEFAULT_OCCUPANCY_DAYS = 30;
const MAX_OCCUPANCY_DAYS = 90;

function dateOnly(value, fieldName) {
  const text = String(value || '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) {
    throw new ValidationError(`${fieldName} must use YYYY-MM-DD`);
  }
  const timestamp = Date.parse(`${text}T00:00:00.000Z`);
  if (!Number.isFinite(timestamp) || new Date(timestamp).toISOString().slice(0, 10) !== text) {
    throw new ValidationError(`${fieldName} is invalid`);
  }
  return text;
}

function databaseDateOnly(value, fieldName) {
  if (value instanceof Date && Number.isFinite(value.getTime())) {
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, '0');
    const day = String(value.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  const text = String(value || '');
  const datePrefix = text.match(/^(\d{4}-\d{2}-\d{2})(?:$|T|\s)/)?.[1];
  return dateOnly(datePrefix || text, fieldName);
}

function nightsBetween(checkInDate, checkOutDate) {
  const start = Date.parse(`${checkInDate}T00:00:00.000Z`);
  const end = Date.parse(`${checkOutDate}T00:00:00.000Z`);
  const nights = Math.round((end - start) / 86400000);
  if (!Number.isInteger(nights) || nights < 1) {
    throw new ValidationError('Check-out date must be after check-in date');
  }
  if (nights > MAX_NIGHTS) {
    throw new ValidationError(`A Stay booking cannot exceed ${MAX_NIGHTS} nights`);
  }
  return nights;
}

function stayBoundaryInstant(date, hour) {
  return new Date(`${date}T${String(hour).padStart(2, '0')}:00:00+05:30`);
}

function checkInInstant(checkInDate) {
  return stayBoundaryInstant(checkInDate, CHECK_IN_HOUR_IST);
}

function checkoutInstant(checkOutDate) {
  return stayBoundaryInstant(checkOutDate, CHECK_OUT_HOUR_IST);
}

function money(value) {
  return Number(Number(value).toFixed(2));
}

function calculateLineTotal(nightlyRate, quantity, nightCount) {
  return money(Number(nightlyRate) * Number(quantity) * Number(nightCount));
}

function refundEligibility(checkInDate, requestedAt = new Date()) {
  const hoursBeforeCheckIn =
    (checkInInstant(checkInDate).getTime() - requestedAt.getTime()) / 3600000;
  return {
    hoursBeforeCheckIn: money(hoursBeforeCheckIn),
    standardFullRefundEligible: hoursBeforeCheckIn >= REFUND_CUTOFF_HOURS,
  };
}

function cancellationTransition(status) {
  if (status === 'pending') {
    return { bookingStatus: 'cancelled', requestStatus: 'approved' };
  }
  if (status === 'confirmed') {
    return { bookingStatus: 'cancellation_requested', requestStatus: 'pending' };
  }
  return null;
}

function indiaDateOnly(now = new Date()) {
  return new Date(now.getTime() + (330 * 60 * 1000)).toISOString().slice(0, 10);
}

function addCalendarDays(date, days) {
  const instant = new Date(`${date}T00:00:00.000Z`);
  instant.setUTCDate(instant.getUTCDate() + days);
  return instant.toISOString().slice(0, 10);
}

function dailyOccupancyRange(filters = {}, now = new Date()) {
  const today = indiaDateOnly(now);
  const fromDate = dateOnly(filters.fromDate || today, 'fromDate');
  if (fromDate < today) {
    throw new ValidationError('fromDate cannot be before today in India');
  }

  const dayCount = Number(filters.days ?? DEFAULT_OCCUPANCY_DAYS);
  if (!Number.isInteger(dayCount) || dayCount < 1 || dayCount > MAX_OCCUPANCY_DAYS) {
    throw new ValidationError(`days must be between 1 and ${MAX_OCCUPANCY_DAYS}`);
  }

  return {
    fromDate,
    toDate: addCalendarDays(fromDate, dayCount - 1),
    dayCount,
  };
}

function formatDailyOccupancy(fromDate, toDate, rows) {
  const days = [];
  const byDate = new Map();

  for (const row of rows) {
    const date = databaseDateOnly(row.occupancy_date, 'occupancy_date');
    let day = byDate.get(date);
    if (!day) {
      day = {
        date,
        bookingCount: Number(row.booking_count || 0),
        totalEarnings: money(row.total_earnings || 0),
        unitTypes: [],
      };
      byDate.set(date, day);
      days.push(day);
    }

    const bookedUnits = Number(row.booked_units || 0);
    const totalUnits = Number(row.total_inventory || 0);
    day.unitTypes.push({
      code: row.code,
      displayName: row.display_name,
      bookedUnits,
      totalUnits,
      availableUnits: Math.max(0, totalUnits - bookedUnits),
    });
  }

  return { fromDate, toDate, days };
}

function bookingReference(now = new Date()) {
  const day = now.toISOString().slice(0, 10).replaceAll('-', '');
  return `STAY-${day}-${crypto.randomBytes(6).toString('hex').toUpperCase()}`;
}

class StayService {
  async currentTypes(trx = db, codes = UNIT_TYPE_CODES) {
    return trx('stay_unit_types')
      .whereIn('code', codes)
      .where('is_active', true)
      .orderBy('display_order', 'asc');
  }

  async getCatalog() {
    const types = await this.currentTypes();
    return {
      checkInTime: '12:00',
      checkOutTime: '11:00',
      timezone: 'Asia/Kolkata',
      currency: 'INR',
      pricesIncludeTaxes: true,
      refundCutoffHours: REFUND_CUTOFF_HOURS,
      maxNights: MAX_NIGHTS,
      unitTypes: types.map((type) => this.formatUnitType(type)),
    };
  }

  async quote(data) {
    await this.completePastBookings();
    return this.buildQuote(data);
  }

  async buildQuote(data, trx = db) {
    const checkInDate = dateOnly(data.checkInDate, 'checkInDate');
    const checkOutDate = dateOnly(data.checkOutDate, 'checkOutDate');
    const nightCount = nightsBetween(checkInDate, checkOutDate);
    const requestedItems = this.normalizeRequestedItems(data.items, { allowEmpty: true });
    const types = await this.currentTypes(trx);
    const typeResults = [];
    let totalAmount = 0;
    let totalCapacity = 0;

    for (const type of types) {
      const requested = requestedItems.find((item) => item.unitTypeCode === type.code)?.quantity || 0;
      const reserved = await this.reservedQuantity(
        trx,
        type.id,
        checkInDate,
        checkOutDate
      );
      const available = Math.max(0, Number(type.total_inventory) - reserved);
      const lineTotal = calculateLineTotal(type.nightly_rate, requested, nightCount);
      totalAmount += lineTotal;
      totalCapacity += Number(type.capacity || 0) * requested;
      typeResults.push({
        ...this.formatUnitType(type),
        availableUnits: available,
        requestedQuantity: requested,
        lineTotal,
        canFulfill: requested <= available,
      });
    }

    const unknown = requestedItems.find(
      (item) => !types.some((type) => type.code === item.unitTypeCode)
    );
    if (unknown) throw new ValidationError(`Unknown or inactive unit type: ${unknown.unitTypeCode}`);

    const subtotalAmount = money(totalAmount);
    const couponPricing = await stayCouponService.apply(
      data.couponCode,
      subtotalAmount,
      trx
    );

    return {
      checkInDate,
      checkOutDate,
      nightCount,
      accommodationAmount: subtotalAmount,
      subtotalAmount,
      discountAmount: couponPricing.discountAmount,
      couponCode: couponPricing.couponCode,
      totalAmount: couponPricing.totalAmount,
      totalCapacity,
      currency: 'INR',
      pricesIncludeTaxes: true,
      canFulfill: typeResults.every((item) => item.canFulfill),
      availabilityIsInformational: true,
      unitTypes: typeResults,
    };
  }

  async createCustomerBooking(userId, data) {
    await this.completePastBookings();
    const bookingId = await db.transaction(async (trx) => {
      const checkInDate = dateOnly(data.checkInDate, 'checkInDate');
      const checkOutDate = dateOnly(data.checkOutDate, 'checkOutDate');
      const nightCount = nightsBetween(checkInDate, checkOutDate);
      const now = new Date();
      if (checkInInstant(checkInDate) <= now) {
        throw new ValidationError('Check-in must be in the future');
      }
      if (data.cancellationPolicyAccepted !== true) {
        throw new ValidationError('Cancellation policy must be accepted');
      }
      const requestedItems = this.normalizeRequestedItems(data.items);
      const types = await this.currentTypes(
        trx,
        requestedItems.map((item) => item.unitTypeCode)
      );
      if (types.length !== requestedItems.length) {
        throw new ValidationError('One or more requested accommodation types are unavailable');
      }

      let totalAmount = 0;
      const items = requestedItems.map((requested) => {
        const type = types.find((candidate) => candidate.code === requested.unitTypeCode);
        if (requested.quantity > Number(type.total_inventory)) {
          throw new ValidationError(
            `${type.display_name} quantity cannot exceed ${type.total_inventory}`
          );
        }
        const lineTotal = calculateLineTotal(type.nightly_rate, requested.quantity, nightCount);
        totalAmount += lineTotal;
        return {
          unit_type_id: type.id,
          unit_type_code: type.code,
          unit_type_name: type.display_name,
          quantity: requested.quantity,
          nightly_rate: type.nightly_rate,
          night_count: nightCount,
          line_total: lineTotal,
        };
      });
      const guestCount = Number(data.guestCount);
      const subtotalAmount = money(totalAmount);
      const couponPricing = await stayCouponService.apply(
        data.couponCode,
        subtotalAmount,
        trx,
        now
      );

      const [booking] = await trx('stay_bookings').insert({
        reference: bookingReference(now),
        user_id: userId,
        status: 'pending',
        check_in_date: checkInDate,
        check_out_date: checkOutDate,
        night_count: nightCount,
        guest_count: guestCount,
        contact_name: String(data.contactName).trim(),
        contact_email: String(data.contactEmail).trim(),
        contact_phone: String(data.contactPhone).trim(),
        subtotal_amount: subtotalAmount,
        discount_amount: couponPricing.discountAmount,
        coupon_id: couponPricing.couponId,
        coupon_code: couponPricing.couponCode,
        total_amount: couponPricing.totalAmount,
        customer_note: String(data.customerNote || '').trim() || null,
        cancellation_policy_accepted: true,
      }).returning('*');
      await trx('stay_booking_items').insert(
        items.map((item) => ({ ...item, booking_id: booking.id }))
      );
      return booking.id;
    });
    return this.getBookingById(bookingId, userId);
  }

  // Admin-created bookings are confirmed immediately, after rechecking inventory inside
  // the same transaction. The booking remains owned by its creating admin so it does not
  // become a booking in the guest's My Stays feed.
  async createAdminBooking(adminId, data) {
    await this.completePastBookings();
    const bookingId = await db.transaction(async (trx) => {
      await trx.raw('SELECT pg_advisory_xact_lock(?)', [STAY_INVENTORY_ADVISORY_LOCK]);
      const checkInDate = dateOnly(data.checkInDate, 'checkInDate');
      const checkOutDate = dateOnly(data.checkOutDate, 'checkOutDate');
      const nightCount = nightsBetween(checkInDate, checkOutDate);
      const now = new Date();
      if (checkInInstant(checkInDate) <= now) {
        throw new ValidationError('Check-in must be in the future');
      }
      if (data.cancellationPolicyAccepted !== true) {
        throw new ValidationError('Cancellation policy must be accepted');
      }
      const requestedItems = this.normalizeRequestedItems(data.items);
      const types = await this.currentTypes(
        trx,
        requestedItems.map((item) => item.unitTypeCode)
      );
      if (types.length !== requestedItems.length) {
        throw new ValidationError('One or more requested accommodation types are unavailable');
      }

      let totalAmount = 0;
      const items = [];
      for (const requested of requestedItems) {
        const type = types.find((candidate) => candidate.code === requested.unitTypeCode);
        if (requested.quantity > Number(type.total_inventory)) {
          throw new ValidationError(
            `${type.display_name} quantity cannot exceed ${type.total_inventory}`
          );
        }
        const reserved = await this.reservedQuantity(
          trx,
          type.id,
          checkInDate,
          checkOutDate
        );
        const available = Number(type.total_inventory) - reserved;
        if (requested.quantity > available) {
          throw new ConflictError(
            `Only ${Math.max(0, available)} ${type.display_name}(s) remain available`
          );
        }
        const lineTotal = calculateLineTotal(type.nightly_rate, requested.quantity, nightCount);
        totalAmount += lineTotal;
        items.push({
          unit_type_id: type.id,
          unit_type_code: type.code,
          unit_type_name: type.display_name,
          quantity: requested.quantity,
          nightly_rate: type.nightly_rate,
          night_count: nightCount,
          line_total: lineTotal,
        });
      }

      const subtotalAmount = money(totalAmount);
      const couponPricing = await stayCouponService.apply(
        data.couponCode,
        subtotalAmount,
        trx,
        now
      );
      const [booking] = await trx('stay_bookings').insert({
        reference: bookingReference(now),
        user_id: adminId,
        booking_source: 'admin',
        status: 'confirmed',
        check_in_date: checkInDate,
        check_out_date: checkOutDate,
        night_count: nightCount,
        guest_count: Number(data.guestCount),
        contact_name: String(data.contactName).trim(),
        contact_email: String(data.contactEmail).trim(),
        contact_phone: String(data.contactPhone).trim(),
        subtotal_amount: subtotalAmount,
        discount_amount: couponPricing.discountAmount,
        coupon_id: couponPricing.couponId,
        coupon_code: couponPricing.couponCode,
        total_amount: couponPricing.totalAmount,
        customer_note: String(data.customerNote || '').trim() || null,
        cancellation_policy_accepted: true,
        confirmed_by: adminId,
        confirmed_at: now,
      }).returning('*');
      await trx('stay_booking_items').insert(
        items.map((item) => ({ ...item, booking_id: booking.id }))
      );
      return booking.id;
    });
    return this.getBookingById(bookingId);
  }

  async confirmBooking(bookingId, adminId) {
    await this.completePastBookings();
    await db.transaction(async (trx) => {
      await trx.raw('SELECT pg_advisory_xact_lock(?)', [STAY_INVENTORY_ADVISORY_LOCK]);
      const booking = await trx('stay_bookings').where('id', bookingId).forUpdate().first();
      if (!booking) throw new NotFoundError('Stay booking');
      if (booking.status !== 'pending') {
        throw new ValidationError(`Cannot confirm booking with status: ${booking.status}`);
      }
      const checkInDate = databaseDateOnly(booking.check_in_date, 'check_in_date');
      const checkOutDate = databaseDateOnly(booking.check_out_date, 'check_out_date');
      if (checkInInstant(checkInDate) <= new Date()) {
        throw new ValidationError('Cannot confirm a booking after check-in has started');
      }
      const items = await trx('stay_booking_items').where('booking_id', bookingId);
      for (const item of items) {
        const type = await trx('stay_unit_types').where('id', item.unit_type_id).first();
        const reserved = await this.reservedQuantity(
          trx,
          item.unit_type_id,
          checkInDate,
          checkOutDate,
          booking.id
        );
        const available = Number(type.total_inventory) - reserved;
        if (Number(item.quantity) > available) {
          throw new ConflictError(
            `Only ${Math.max(0, available)} ${type.display_name}(s) remain available`
          );
        }
      }
      const now = new Date();
      await trx('stay_bookings').where('id', bookingId).update({
        status: 'confirmed',
        confirmed_by: adminId,
        confirmed_at: now,
        updated_at: now,
      });
    });
    return this.getBookingById(bookingId);
  }

  async rejectBooking(bookingId, adminId, reason) {
    await db.transaction(async (trx) => {
      const booking = await trx('stay_bookings').where('id', bookingId).forUpdate().first();
      if (!booking) throw new NotFoundError('Stay booking');
      if (booking.status !== 'pending') {
        throw new ValidationError(`Cannot reject booking with status: ${booking.status}`);
      }
      const now = new Date();
      await trx('stay_bookings').where('id', bookingId).update({
        status: 'rejected',
        rejected_by: adminId,
        rejected_at: now,
        rejection_reason: String(reason).trim(),
        updated_at: now,
      });
    });
    return this.getBookingById(bookingId);
  }

  async requestCancellation(bookingId, userId, reason = null) {
    const cancellationId = await db.transaction(async (trx) => {
      const booking = await trx('stay_bookings')
        .where('id', bookingId)
        .where('user_id', userId)
        .forUpdate()
        .first();
      if (!booking) throw new NotFoundError('Stay booking');
      const transition = cancellationTransition(booking.status);
      if (!transition) {
        throw new ValidationError(`Cannot request cancellation with status: ${booking.status}`);
      }
      const checkInDate = databaseDateOnly(booking.check_in_date, 'check_in_date');
      const now = new Date();
      if (checkInInstant(checkInDate) <= now) {
        throw new ValidationError('Cancellation cannot be requested after check-in has started');
      }
      const eligibility = refundEligibility(checkInDate, now);
      const isImmediate = transition.bookingStatus === 'cancelled';
      const [request] = await trx('stay_cancellation_requests').insert({
        booking_id: booking.id,
        status: transition.requestStatus,
        previous_booking_status: booking.status,
        reason: String(reason || '').trim() || null,
        requested_at: now,
        standard_full_refund_eligible: eligibility.standardFullRefundEligible,
        hours_before_check_in: eligibility.hoursBeforeCheckIn,
        refund_decision: isImmediate ? 'none' : null,
        refund_amount: isImmediate ? 0 : null,
        decision_reason: isImmediate ? 'Cancelled before admin approval' : null,
        decided_at: isImmediate ? now : null,
      }).returning('*');
      await trx('stay_bookings').where('id', booking.id).update({
        status: transition.bookingStatus,
        updated_at: now,
      });
      return request.id;
    });
    return this.getCancellationById(cancellationId);
  }

  async decideCancellation(cancellationId, adminId, data) {
    let bookingId;
    await db.transaction(async (trx) => {
      const request = await trx('stay_cancellation_requests')
        .where('id', cancellationId)
        .forUpdate()
        .first();
      if (!request) throw new NotFoundError('Stay cancellation request');
      if (request.status !== 'pending') {
        throw new ValidationError(`Cancellation request is already ${request.status}`);
      }
      const booking = await trx('stay_bookings').where('id', request.booking_id).forUpdate().first();
      if (!booking || booking.status !== 'cancellation_requested') {
        throw new ValidationError('Booking is no longer awaiting a cancellation decision');
      }
      bookingId = booking.id;
      const now = new Date();

      if (data.action === 'reject') {
        await trx('stay_cancellation_requests').where('id', request.id).update({
          status: 'rejected',
          decision_reason: String(data.reason).trim(),
          decided_by: adminId,
          decided_at: now,
          updated_at: now,
        });
        await trx('stay_bookings').where('id', booking.id).update({
          status: request.previous_booking_status,
          updated_at: now,
        });
        return;
      }

      const total = money(booking.total_amount);
      const decision = data.refundDecision;
      let refundAmount = 0;
      if (decision === 'full') refundAmount = total;
      if (decision === 'partial') refundAmount = money(data.refundAmount);
      if (decision === 'none') refundAmount = 0;
      if (refundAmount < 0 || refundAmount > total) {
        throw new ValidationError(`Refund amount must be between ₹0 and ₹${total}`);
      }
      if (request.standard_full_refund_eligible && decision !== 'full') {
        throw new ValidationError('This request is entitled to a full refund');
      }
      if (decision !== 'full' && !String(data.reason || '').trim()) {
        throw new ValidationError('A reason is required when a full refund is not granted');
      }

      await trx('stay_cancellation_requests').where('id', request.id).update({
        status: 'approved',
        refund_decision: decision,
        refund_amount: refundAmount,
        decision_reason: String(data.reason || '').trim() || null,
        decided_by: adminId,
        decided_at: now,
        updated_at: now,
      });
      await trx('stay_bookings').where('id', booking.id).update({
        status: 'cancelled',
        updated_at: now,
      });
    });
    return this.getBookingById(bookingId);
  }

  // This is deliberately limited to bookings explicitly created through the admin
  // flow. Customer cancellations continue through the normal request-and-decision
  // workflow, even when another Stay Admin is on duty.
  async cancelAdminBooking(bookingId, adminId, data) {
    await this.completePastBookings();
    await db.transaction(async (trx) => {
      const booking = await trx('stay_bookings')
        .where('id', bookingId)
        .forUpdate()
        .first();
      if (!booking) throw new NotFoundError('Admin Stay booking');
      if (booking.booking_source !== 'admin') {
        throw new ValidationError('Only admin-created Stay bookings can be cancelled immediately');
      }
      if (booking.status !== 'confirmed') {
        throw new ValidationError(`Cannot cancel booking with status: ${booking.status}`);
      }
      const checkInDate = databaseDateOnly(booking.check_in_date, 'check_in_date');
      const now = new Date();
      if (checkInInstant(checkInDate) <= now) {
        throw new ValidationError('Cancellation cannot be requested after check-in has started');
      }
      const eligibility = refundEligibility(checkInDate, now);
      const total = money(booking.total_amount);
      const decision = data.refundDecision;
      const refundAmount = decision === 'full'
        ? total
        : decision === 'partial'
          ? money(data.refundAmount)
          : 0;
      if (refundAmount < 0 || refundAmount > total) {
        throw new ValidationError(`Refund amount must be between ₹0 and ₹${total}`);
      }
      if (eligibility.standardFullRefundEligible && decision !== 'full') {
        throw new ValidationError('This request is entitled to a full refund');
      }
      const reason = String(data.reason || '').trim() || null;
      if (decision !== 'full' && !reason) {
        throw new ValidationError('A reason is required when a full refund is not granted');
      }
      await trx('stay_cancellation_requests').insert({
        booking_id: booking.id,
        status: 'approved',
        previous_booking_status: booking.status,
        reason,
        requested_at: now,
        standard_full_refund_eligible: eligibility.standardFullRefundEligible,
        hours_before_check_in: eligibility.hoursBeforeCheckIn,
        refund_decision: decision,
        refund_amount: refundAmount,
        decision_reason: reason,
        decided_by: adminId,
        decided_at: now,
      });
      await trx('stay_bookings').where('id', booking.id).update({
        status: 'cancelled',
        updated_at: now,
      });
    });
    return this.getBookingById(bookingId);
  }

  async completePastBookings(now = new Date()) {
    const candidates = await db('stay_bookings')
      .where('status', 'confirmed')
      .where('check_out_date', '<=', now.toISOString().slice(0, 10));
    let count = 0;
    for (const booking of candidates) {
      const checkOutDate = databaseDateOnly(booking.check_out_date, 'check_out_date');
      if (checkoutInstant(checkOutDate) > now) continue;
      await db.transaction(async (trx) => {
        const current = await trx('stay_bookings').where('id', booking.id).forUpdate().first();
        if (!current || current.status !== 'confirmed') return;
        await trx('stay_bookings').where('id', booking.id).update({
          status: 'completed',
          completed_at: now,
          updated_at: now,
        });
        count += 1;
      });
    }
    return count;
  }

  async getBookingById(bookingId, userId = null) {
    let query = db('stay_bookings').where('id', bookingId);
    if (userId) query = query.where('user_id', userId);
    const booking = await query.first();
    if (!booking) throw new NotFoundError('Stay booking');
    const [items, cancellation] = await Promise.all([
      db('stay_booking_items').where('booking_id', booking.id).orderBy('created_at', 'asc'),
      db('stay_cancellation_requests')
        .where('booking_id', booking.id)
        .orderBy('requested_at', 'desc')
        .first(),
    ]);
    return this.formatBooking(booking, items, cancellation);
  }

  async getUserBookings(userId, filters = {}) {
    return this.listBookings({ ...filters, userId });
  }

  async getAdminBookings(filters = {}) {
    await this.completePastBookings();
    return this.listBookings(filters);
  }

  async getDailyOccupancy(filters = {}) {
    await this.completePastBookings();
    const { fromDate, toDate } = dailyOccupancyRange(filters);
    const result = await db.raw(`
      WITH days AS (
        SELECT generate_series(?::date, ?::date, INTERVAL '1 day')::date AS occupancy_date
      ),
      occupied_units AS (
        SELECT
          day.occupancy_date,
          item.unit_type_id,
          SUM(item.quantity)::integer AS booked_units
        FROM days day
        JOIN stay_bookings booking
          ON booking.check_in_date <= day.occupancy_date
         AND booking.check_out_date > day.occupancy_date
         AND booking.confirmed_at IS NOT NULL
         AND booking.status IN ('confirmed', 'cancellation_requested')
        JOIN stay_booking_items item ON item.booking_id = booking.id
        GROUP BY day.occupancy_date, item.unit_type_id
      ),
      daily_booking_counts AS (
        SELECT
          day.occupancy_date,
          COUNT(DISTINCT booking.id)::integer AS booking_count
        FROM days day
        LEFT JOIN stay_bookings booking
          ON booking.check_in_date <= day.occupancy_date
         AND booking.check_out_date > day.occupancy_date
         AND booking.confirmed_at IS NOT NULL
         AND booking.status IN ('confirmed', 'cancellation_requested')
        GROUP BY day.occupancy_date
      ),
      daily_earnings AS (
        SELECT
          day.occupancy_date,
          COALESCE(
            SUM(booking.total_amount / NULLIF(booking.night_count, 0)),
            0
          )::numeric(12, 2) AS total_earnings
        FROM days day
        LEFT JOIN stay_bookings booking
          ON booking.check_in_date <= day.occupancy_date
         AND booking.check_out_date > day.occupancy_date
         AND booking.confirmed_at IS NOT NULL
         AND booking.status = 'confirmed'
        GROUP BY day.occupancy_date
      ),
      report_unit_types AS (
        SELECT type.*
        FROM stay_unit_types type
        WHERE type.is_active = true
           OR EXISTS (
             SELECT 1
             FROM occupied_units occupied
             WHERE occupied.unit_type_id = type.id
           )
      )
      SELECT
        day.occupancy_date,
        type.code,
        type.display_name,
        type.total_inventory,
        COALESCE(occupied.booked_units, 0)::integer AS booked_units,
        counts.booking_count,
        earnings.total_earnings
      FROM days day
      CROSS JOIN report_unit_types type
      LEFT JOIN occupied_units occupied
        ON occupied.occupancy_date = day.occupancy_date
       AND occupied.unit_type_id = type.id
      JOIN daily_booking_counts counts
        ON counts.occupancy_date = day.occupancy_date
      JOIN daily_earnings earnings
        ON earnings.occupancy_date = day.occupancy_date
      ORDER BY day.occupancy_date ASC, type.display_order ASC, type.code ASC
    `, [fromDate, toDate]);

    return formatDailyOccupancy(fromDate, toDate, result.rows);
  }

  async listBookings(filters = {}) {
    let base = db('stay_bookings as b');
    if (filters.userId) base = base.where('b.user_id', filters.userId);
    if (filters.status) base = base.where('b.status', filters.status);
    if (filters.fromDate) base = base.where('b.check_out_date', '>', filters.fromDate);
    if (filters.toDate) base = base.where('b.check_in_date', '<', filters.toDate);
    if (filters.search) {
      const term = `%${String(filters.search).trim()}%`;
      base = base.where((builder) => builder
        .whereILike('b.reference', term)
        .orWhereILike('b.contact_name', term)
        .orWhereILike('b.contact_email', term)
        .orWhereILike('b.contact_phone', term));
    }
    const page = Math.max(1, Number(filters.page) || 1);
    const limit = Math.min(100, Math.max(1, Number(filters.limit) || 20));
    const countRow = await base.clone().count('b.id as count').first();
    const rows = await base.clone().select('b.*')
      .orderBy('b.created_at', 'desc')
      .offset((page - 1) * limit)
      .limit(limit);
    const ids = rows.map((row) => row.id);
    const items = ids.length
      ? await db('stay_booking_items').whereIn('booking_id', ids).orderBy('created_at', 'asc')
      : [];
    return {
      items: rows.map((row) => this.formatBooking(
        row,
        items.filter((item) => item.booking_id === row.id),
        null
      )),
      page,
      limit,
      total: Number(countRow?.count || 0),
    };
  }

  async getAdminCancellations(filters = {}) {
    let base = db('stay_cancellation_requests as c')
      .join('stay_bookings as b', 'c.booking_id', 'b.id');
    if (filters.status) base = base.where('c.status', filters.status);
    const page = Math.max(1, Number(filters.page) || 1);
    const limit = Math.min(100, Math.max(1, Number(filters.limit) || 20));
    const countRow = await base.clone().count('c.id as count').first();
    const rows = await base.clone().select(
      'c.*',
      'b.reference',
      'b.contact_name',
      'b.contact_phone',
      'b.contact_email',
      'b.check_in_date',
      'b.check_out_date',
      'b.total_amount'
    ).orderBy('c.requested_at', 'desc')
      .offset((page - 1) * limit)
      .limit(limit);
    return {
      items: rows.map((row) => this.formatCancellation(row)),
      page,
      limit,
      total: Number(countRow?.count || 0),
    };
  }

  async getCancellationById(id) {
    const row = await db('stay_cancellation_requests as c')
      .join('stay_bookings as b', 'c.booking_id', 'b.id')
      .select(
        'c.*',
        'b.reference',
        'b.contact_name',
        'b.contact_phone',
        'b.contact_email',
        'b.check_in_date',
        'b.check_out_date',
        'b.total_amount'
      )
      .where('c.id', id)
      .first();
    if (!row) throw new NotFoundError('Stay cancellation request');
    return this.formatCancellation(row);
  }

  normalizeRequestedItems(items, { allowEmpty = false } = {}) {
    if (!Array.isArray(items) || (!allowEmpty && items.length === 0)) {
      throw new ValidationError('At least one room or hall must be selected');
    }
    const normalized = items.map((item) => ({
      unitTypeCode: String(item.unitTypeCode || '').trim(),
      quantity: Number(item.quantity),
    })).filter((item) => item.quantity > 0);
    if (!allowEmpty && !normalized.length) {
      throw new ValidationError('At least one room or hall must be selected');
    }
    const seen = new Set();
    for (const item of normalized) {
      if (!UNIT_TYPE_CODES.includes(item.unitTypeCode)) {
        throw new ValidationError(`Unknown unit type: ${item.unitTypeCode}`);
      }
      if (!Number.isInteger(item.quantity) || item.quantity < 1 || item.quantity > 13) {
        throw new ValidationError('Each quantity must be between 1 and 13');
      }
      if (seen.has(item.unitTypeCode)) {
        throw new ValidationError(`Duplicate unit type: ${item.unitTypeCode}`);
      }
      seen.add(item.unitTypeCode);
    }
    return normalized.sort(
      (left, right) =>
        UNIT_TYPE_CODES.indexOf(left.unitTypeCode) - UNIT_TYPE_CODES.indexOf(right.unitTypeCode)
    );
  }

  async reservedQuantity(trx, typeId, checkInDate, checkOutDate, excludeBookingId = null) {
    let query = trx('stay_booking_items as i')
      .join('stay_bookings as b', 'i.booking_id', 'b.id')
      .where('i.unit_type_id', typeId)
      .whereIn('b.status', INVENTORY_HOLDING_STATUSES)
      .whereNotNull('b.confirmed_at')
      .where('b.check_in_date', '<', checkOutDate)
      .where('b.check_out_date', '>', checkInDate);
    if (excludeBookingId) query = query.whereNot('b.id', excludeBookingId);
    const row = await query.sum('i.quantity as quantity').first();
    return Number(row?.quantity || 0);
  }

  formatUnitType(row) {
    return {
      id: row.id,
      code: row.code,
      displayName: row.display_name,
      capacity: row.capacity === null ? null : Number(row.capacity),
      nightlyRate: money(row.nightly_rate),
      totalUnits: Number(row.total_inventory),
      isActive: row.is_active,
    };
  }

  formatBooking(row, items = [], cancellation = null) {
    return {
      id: row.id,
      reference: row.reference,
      userId: row.user_id,
      bookingSource: row.booking_source,
      status: row.status,
      checkInDate: databaseDateOnly(row.check_in_date, 'check_in_date'),
      checkOutDate: databaseDateOnly(row.check_out_date, 'check_out_date'),
      nightCount: Number(row.night_count),
      guestCount: Number(row.guest_count),
      contactName: row.contact_name,
      contactEmail: row.contact_email,
      contactPhone: row.contact_phone,
      subtotalAmount: money(row.subtotal_amount ?? row.total_amount),
      discountAmount: money(row.discount_amount || 0),
      couponCode: row.coupon_code || null,
      totalAmount: money(row.total_amount),
      customerNote: row.customer_note,
      rejectionReason: row.rejection_reason,
      confirmedAt: row.confirmed_at,
      completedAt: row.completed_at,
      createdAt: row.created_at,
      updatedAt: row.updated_at,
      items: items.map((item) => ({
        id: item.id,
        unitTypeCode: item.unit_type_code,
        unitTypeName: item.unit_type_name,
        quantity: Number(item.quantity),
        nightlyRate: money(item.nightly_rate),
        nightCount: Number(item.night_count),
        lineTotal: money(item.line_total),
      })),
      cancellation: cancellation ? this.formatCancellation(cancellation) : null,
    };
  }

  formatCancellation(row) {
    return {
      id: row.id,
      bookingId: row.booking_id,
      reference: row.reference,
      status: row.status,
      previousBookingStatus: row.previous_booking_status,
      reason: row.reason,
      requestedAt: row.requested_at,
      standardFullRefundEligible: row.standard_full_refund_eligible,
      hoursBeforeCheckIn: Number(row.hours_before_check_in),
      refundDecision: row.refund_decision,
      refundAmount: row.refund_amount === null ? null : money(row.refund_amount),
      decisionReason: row.decision_reason,
      decidedAt: row.decided_at,
      contactName: row.contact_name,
      contactPhone: row.contact_phone,
      contactEmail: row.contact_email,
      checkInDate: row.check_in_date
        ? databaseDateOnly(row.check_in_date, 'check_in_date')
        : undefined,
      checkOutDate: row.check_out_date
        ? databaseDateOnly(row.check_out_date, 'check_out_date')
        : undefined,
      totalAmount: row.total_amount === undefined ? undefined : money(row.total_amount),
    };
  }
}

module.exports = new StayService();
module.exports.StayService = StayService;
module.exports.constants = {
  REFUND_CUTOFF_HOURS,
  CHECK_IN_HOUR_IST,
  CHECK_OUT_HOUR_IST,
  MAX_NIGHTS,
  UNIT_TYPE_CODES,
  INVENTORY_HOLDING_STATUSES,
  TERMINAL_STATUSES,
  DEFAULT_OCCUPANCY_DAYS,
  MAX_OCCUPANCY_DAYS,
};
module.exports.helpers = {
  dateOnly,
  databaseDateOnly,
  nightsBetween,
  checkInInstant,
  checkoutInstant,
  money,
  calculateLineTotal,
  cancellationTransition,
  refundEligibility,
  indiaDateOnly,
  addCalendarDays,
  dailyOccupancyRange,
  formatDailyOccupancy,
};
