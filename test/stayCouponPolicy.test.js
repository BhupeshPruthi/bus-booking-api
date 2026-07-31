const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const { StayCouponService, helpers } = require('../src/services/stayCouponService');
const { StayService } = require('../src/services/stayService');
const {
  stayQuoteSchema,
  createStayCouponSchema,
} = require('../src/validators/schemas');

const migrationSource = fs.readFileSync(
  path.join(__dirname, '../src/db/migrations/20260802000003_create_stay_coupons.js'),
  'utf8'
);

test('coupon codes normalize to uppercase and Stay quotes accept one optional code', () => {
  assert.equal(helpers.normalizeCouponCode('  welcome_500 '), 'WELCOME_500');
  const result = stayQuoteSchema.validate({
    checkInDate: '2026-08-10',
    checkOutDate: '2026-08-11',
    items: [{ unitTypeCode: 'three_bed_room', quantity: 1 }],
    couponCode: ' welcome_500 ',
  });
  assert.equal(result.error, undefined);
  assert.equal(result.value.couponCode, 'WELCOME_500');
});

test('coupon creation requires a positive fixed amount and ordered dates', () => {
  const valid = createStayCouponSchema.validate({
    code: 'save500',
    discountAmount: 500,
    startDate: '2026-08-01',
    endDate: '2026-08-31',
  });
  assert.equal(valid.error, undefined);
  assert.equal(valid.value.code, 'SAVE500');

  assert.ok(createStayCouponSchema.validate({
    code: 'SAVE500',
    discountAmount: 0,
    startDate: '2026-08-01',
    endDate: '2026-08-31',
  }).error);
  assert.match(createStayCouponSchema.validate({
    code: 'SAVE500',
    discountAmount: 500,
    startDate: '2026-08-31',
    endDate: '2026-08-01',
  }).error?.message || '', /End date cannot be before start date/);
});

test('coupon validity uses inclusive India calendar dates', () => {
  assert.equal(helpers.indiaDate(new Date('2026-08-01T18:29:59.000Z')), '2026-08-01');
  assert.equal(helpers.indiaDate(new Date('2026-08-01T18:30:00.000Z')), '2026-08-02');
  const coupon = {
    is_active: true,
    start_date: '2026-08-01',
    end_date: '2026-08-31',
  };
  assert.equal(helpers.couponStatus(coupon, '2026-07-31'), 'upcoming');
  assert.equal(helpers.couponStatus(coupon, '2026-08-01'), 'active');
  assert.equal(helpers.couponStatus(coupon, '2026-08-31'), 'active');
  assert.equal(helpers.couponStatus(coupon, '2026-09-01'), 'expired');
  assert.equal(helpers.couponStatus({ ...coupon, is_active: false }, '2026-08-10'), 'deactivated');
});

test('fixed discounts are capped at the Stay subtotal', () => {
  assert.equal(helpers.calculateDiscount(500, 1200), 500);
  assert.equal(helpers.calculateDiscount(1500, 1200), 1200);
});

test('applying a coupon returns server-calculated pricing', async () => {
  const coupon = {
    id: 'coupon-id',
    code: 'SAVE500',
    discount_amount: '500.00',
    start_date: '2026-08-01',
    end_date: '2026-08-31',
    is_active: true,
  };
  const query = {
    where(column, value) {
      assert.equal(column, 'code');
      assert.equal(value, 'SAVE500');
      return this;
    },
    async first() {
      return coupon;
    },
  };
  const database = (tableName) => {
    assert.equal(tableName, 'stay_coupons');
    return query;
  };

  const result = await new StayCouponService().apply(
    'save500',
    1200,
    database,
    new Date('2026-08-15T06:30:00.000Z')
  );
  assert.deepEqual(result, {
    couponId: 'coupon-id',
    couponCode: 'SAVE500',
    discountAmount: 500,
    totalAmount: 700,
  });
});

test('coupon application rejects upcoming, expired, and deactivated codes', async () => {
  const service = new StayCouponService();
  const atMidMonth = new Date('2026-08-15T06:30:00.000Z');
  const databaseFor = (coupon) => () => ({
    where() { return this; },
    async first() { return coupon; },
  });

  await assert.rejects(
    service.apply('LATER', 1200, databaseFor({
      code: 'LATER', discount_amount: 100, is_active: true,
      start_date: '2026-08-16', end_date: '2026-08-31',
    }), atMidMonth),
    /not active yet/
  );
  await assert.rejects(
    service.apply('OLD', 1200, databaseFor({
      code: 'OLD', discount_amount: 100, is_active: true,
      start_date: '2026-08-01', end_date: '2026-08-14',
    }), atMidMonth),
    /expired/
  );
  await assert.rejects(
    service.apply('STOPPED', 1200, databaseFor({
      code: 'STOPPED', discount_amount: 100, is_active: false,
      start_date: '2026-08-01', end_date: '2026-08-31',
    }), atMidMonth),
    /no longer active/
  );
});

test('a missing coupon leaves the subtotal unchanged without querying', async () => {
  const result = await new StayCouponService().apply('', 1200, () => {
    throw new Error('Database should not be queried');
  });
  assert.deepEqual(result, {
    couponId: null,
    couponCode: null,
    discountAmount: 0,
    totalAmount: 1200,
  });
});

test('booking responses preserve immutable coupon pricing snapshots', () => {
  const result = new StayService().formatBooking({
    id: 'booking-id',
    reference: 'STAY-1',
    user_id: 'user-id',
    status: 'pending',
    check_in_date: '2026-08-10',
    check_out_date: '2026-08-11',
    night_count: 1,
    guest_count: 2,
    contact_name: 'Guest',
    contact_email: 'guest@example.com',
    contact_phone: '9999999999',
    subtotal_amount: '1200.00',
    discount_amount: '500.00',
    coupon_code: 'SAVE500',
    total_amount: '700.00',
  });
  assert.equal(result.subtotalAmount, 1200);
  assert.equal(result.discountAmount, 500);
  assert.equal(result.couponCode, 'SAVE500');
  assert.equal(result.totalAmount, 700);
});

test('coupon migration adds one coupon table and booking pricing snapshots', () => {
  assert.match(migrationSource, /createTable\('stay_coupons'/);
  assert.match(migrationSource, /subtotal_amount/);
  assert.match(migrationSource, /discount_amount/);
  assert.match(migrationSource, /coupon_code/);
  assert.match(migrationSource, /total_amount = subtotal_amount - discount_amount/);
});
