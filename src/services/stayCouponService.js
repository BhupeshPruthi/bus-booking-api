const { db } = require('../config/database');
const { NotFoundError, ValidationError, ConflictError } = require('../utils/errors');

const INDIA_TIMEZONE = 'Asia/Kolkata';

function money(value) {
  return Number(Number(value).toFixed(2));
}

function normalizeCouponCode(value) {
  return String(value || '').trim().toUpperCase();
}

function databaseDateOnly(value) {
  if (value instanceof Date && Number.isFinite(value.getTime())) {
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, '0');
    const day = String(value.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
  return String(value || '').slice(0, 10);
}

function indiaDate(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: INDIA_TIMEZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(now);
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}`;
}

function couponStatus(coupon, today = indiaDate()) {
  if (!coupon.is_active) return 'deactivated';
  if (today < databaseDateOnly(coupon.start_date)) return 'upcoming';
  if (today > databaseDateOnly(coupon.end_date)) return 'expired';
  return 'active';
}

function calculateDiscount(discountAmount, subtotalAmount) {
  return money(Math.min(Number(discountAmount), Number(subtotalAmount)));
}

function formatCoupon(row, today = indiaDate()) {
  return {
    id: row.id,
    code: row.code,
    discountAmount: money(row.discount_amount),
    startDate: databaseDateOnly(row.start_date),
    endDate: databaseDateOnly(row.end_date),
    isActive: row.is_active,
    status: couponStatus(row, today),
    createdAt: row.created_at,
  };
}

class StayCouponService {
  async apply(code, subtotalAmount, trx = db, now = new Date()) {
    const normalizedCode = normalizeCouponCode(code);
    if (!normalizedCode) {
      return {
        couponId: null,
        couponCode: null,
        discountAmount: 0,
        totalAmount: money(subtotalAmount),
      };
    }

    const coupon = await trx('stay_coupons').where('code', normalizedCode).first();
    if (!coupon) throw new ValidationError('Coupon code is invalid');

    const status = couponStatus(coupon, indiaDate(now));
    if (status === 'deactivated') throw new ValidationError('Coupon is no longer active');
    if (status === 'upcoming') throw new ValidationError('Coupon is not active yet');
    if (status === 'expired') throw new ValidationError('Coupon has expired');

    const discountAmount = calculateDiscount(coupon.discount_amount, subtotalAmount);
    return {
      couponId: coupon.id,
      couponCode: coupon.code,
      discountAmount,
      totalAmount: money(Number(subtotalAmount) - discountAmount),
    };
  }

  async create(adminId, data) {
    const code = normalizeCouponCode(data.code);
    try {
      const [coupon] = await db('stay_coupons').insert({
        code,
        discount_amount: money(data.discountAmount),
        start_date: data.startDate,
        end_date: data.endDate,
        is_active: true,
        created_by: adminId,
      }).returning('*');
      return formatCoupon(coupon);
    } catch (error) {
      if (error?.code === '23505') {
        throw new ConflictError(`Coupon ${code} already exists`);
      }
      throw error;
    }
  }

  async list() {
    const rows = await db('stay_coupons').select('*')
      .orderBy('created_at', 'desc');
    const today = indiaDate();
    return rows.map((row) => formatCoupon(row, today));
  }

  async deactivate(id) {
    const [coupon] = await db('stay_coupons')
      .where('id', id)
      .where('is_active', true)
      .update({ is_active: false, updated_at: new Date() })
      .returning('*');
    if (!coupon) {
      const existing = await db('stay_coupons').where('id', id).first();
      if (!existing) throw new NotFoundError('Stay coupon');
      throw new ValidationError('Coupon is already deactivated');
    }
    return formatCoupon(coupon);
  }
}

module.exports = new StayCouponService();
module.exports.StayCouponService = StayCouponService;
module.exports.helpers = {
  normalizeCouponCode,
  indiaDate,
  couponStatus,
  calculateDiscount,
  formatCoupon,
};
