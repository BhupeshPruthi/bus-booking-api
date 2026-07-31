const { db } = require('../config/database');
const { NotFoundError, ValidationError } = require('../utils/errors');

const BOOKING_TYPES = ['bus', 'stay', 'pooja'];
const BUCKETS = ['upcoming', 'past', 'failed'];
const FAILED_STATUSES = ['cancelled', 'rejected', 'expired'];

function encodeCursor(row) {
  return Buffer.from(JSON.stringify({
    startsAt: new Date(row.starts_at).toISOString(),
    bookingType: row.booking_type,
    bookingId: row.booking_id,
  })).toString('base64url');
}

function decodeCursor(cursor) {
  if (!cursor) return null;
  try {
    const decoded = JSON.parse(Buffer.from(cursor, 'base64url').toString('utf8'));
    if (!decoded.startsAt || !BOOKING_TYPES.includes(decoded.bookingType) || !decoded.bookingId) {
      throw new Error('Invalid cursor fields');
    }
    const startsAt = new Date(decoded.startsAt);
    if (Number.isNaN(startsAt.getTime())) throw new Error('Invalid cursor date');
    return { ...decoded, startsAt };
  } catch (_error) {
    throw new ValidationError('Invalid booking cursor');
  }
}

function normalizedTypes(value) {
  if (!value) return BOOKING_TYPES;
  const types = String(value).split(',').map((type) => type.trim()).filter(Boolean);
  return [...new Set(types)];
}

function applyBucket(query, bucket) {
  if (bucket === 'failed') {
    return query.whereIn('normalized_status', FAILED_STATUSES);
  }
  if (bucket === 'past') {
    return query
      .whereNotIn('normalized_status', FAILED_STATUSES)
      .andWhere((builder) => builder
        .where('normalized_status', 'completed')
        .orWhere('ends_at', '<=', db.fn.now()));
  }
  return query
    .whereNotIn('normalized_status', [...FAILED_STATUSES, 'completed'])
    .andWhere('ends_at', '>', db.fn.now());
}

function applyCursor(query, bucket, cursor) {
  if (!cursor) return query;
  return query.andWhere((builder) => {
    if (bucket === 'upcoming') {
      builder.where('starts_at', '>', cursor.startsAt);
    } else {
      builder.where('starts_at', '<', cursor.startsAt);
    }
    builder.orWhere((sameTime) => sameTime
      .where('starts_at', cursor.startsAt)
      .andWhere((sameType) => sameType
        .where('booking_type', '>', cursor.bookingType)
        .orWhere((sameBookingType) => sameBookingType
          .where('booking_type', cursor.bookingType)
          .andWhere('booking_id', '>', cursor.bookingId))));
  });
}

function availableActions(row) {
  if (row.starts_at && new Date(row.starts_at) <= new Date()) return ['view'];
  if (row.booking_type === 'bus' &&
      ['pending', 'payment_uploaded', 'confirmed'].includes(row.raw_status)) {
    return ['view', 'request_cancellation'];
  }
  if (row.booking_type === 'stay' &&
      ['pending', 'confirmed'].includes(row.raw_status)) {
    return ['view', 'request_cancellation'];
  }
  return ['view'];
}

function formatItem(row) {
  return {
    id: `${row.booking_type}:${row.booking_id}`,
    bookingType: row.booking_type,
    bookingId: row.booking_id,
    reference: row.reference,
    rawStatus: row.raw_status,
    normalizedStatus: row.normalized_status,
    startsAt: row.starts_at,
    endsAt: row.ends_at,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    totalAmount: row.total_amount == null ? null : Number(row.total_amount),
    currency: row.currency,
    title: row.title,
    subtitle: row.subtitle,
    details: row.details || {},
    availableActions: availableActions(row),
  };
}

async function bucketCounts(userId, types) {
  const rows = await db('user_booking_feed')
    .select(db.raw(`
      CASE
        WHEN normalized_status IN ('cancelled', 'rejected', 'expired') THEN 'failed'
        WHEN normalized_status = 'completed' OR ends_at <= CURRENT_TIMESTAMP THEN 'past'
        ELSE 'upcoming'
      END AS bucket
    `))
    .count('* as count')
    .where('user_id', userId)
    .whereIn('booking_type', types)
    .groupBy('bucket');
  const counts = { upcoming: 0, past: 0, failed: 0 };
  rows.forEach((row) => {
    if (BUCKETS.includes(row.bucket)) counts[row.bucket] = Number(row.count);
  });
  return counts;
}

class UnifiedBookingService {
  async list(userId, filters = {}) {
    const bucket = filters.bucket || 'upcoming';
    const types = normalizedTypes(filters.types);
    const limit = Math.min(50, Math.max(1, Number(filters.limit) || 20));
    const cursor = decodeCursor(filters.cursor);
    let query = db('user_booking_feed')
      .select('*')
      .where('user_id', userId)
      .whereIn('booking_type', types);
    query = applyBucket(query, bucket);
    query = applyCursor(query, bucket, cursor);
    query = bucket === 'upcoming'
      ? query.orderBy('starts_at', 'asc')
      : query.orderBy('starts_at', 'desc');
    const rows = await query
      .orderBy('booking_type', 'asc')
      .orderBy('booking_id', 'asc')
      .limit(limit + 1);
    const hasMore = rows.length > limit;
    const pageRows = rows.slice(0, limit);
    return {
      items: pageRows.map(formatItem),
      counts: await bucketCounts(userId, types),
      nextCursor: hasMore && pageRows.length ? encodeCursor(pageRows[pageRows.length - 1]) : null,
      serverTime: new Date().toISOString(),
    };
  }

  async getById(userId, bookingType, bookingId) {
    if (!BOOKING_TYPES.includes(bookingType)) {
      throw new ValidationError('Unknown booking type');
    }
    const row = await db('user_booking_feed')
      .where({
        user_id: userId,
        booking_type: bookingType,
        booking_id: bookingId,
      })
      .first();
    if (!row) throw new NotFoundError('Booking');
    const result = formatItem(row);
    if (bookingType === 'stay') {
      const pricing = await db('stay_bookings')
        .select('subtotal_amount', 'discount_amount', 'coupon_code')
        .where({ id: bookingId, user_id: userId })
        .first();
      if (pricing) {
        result.details = {
          ...result.details,
          subtotalAmount: Number(pricing.subtotal_amount),
          discountAmount: Number(pricing.discount_amount || 0),
          couponCode: pricing.coupon_code || null,
        };
      }
    }
    return result;
  }
}

module.exports = new UnifiedBookingService();
module.exports.UnifiedBookingService = UnifiedBookingService;
module.exports.constants = { BOOKING_TYPES, BUCKETS, FAILED_STATUSES };
module.exports.helpers = {
  encodeCursor,
  decodeCursor,
  normalizedTypes,
  availableActions,
  formatItem,
};
