const test = require('node:test');
const assert = require('node:assert/strict');

const testDatabaseUrl = process.env.TEST_DATABASE_URL;
// This test deliberately creates and drops tables. Make its declared test URL the
// only database connection it can use before the Knex singleton is initialized.
if (testDatabaseUrl) process.env.DATABASE_URL = testDatabaseUrl;
const hasTestDatabase = Boolean(testDatabaseUrl);
const { db } = require('../src/config/database');
const { StayService } = require('../src/services/stayService');
const { indiaDateOnly, addCalendarDays } = require('../src/services/stayService').helpers;

const TYPE_IDS = {
  threeBed: '00000000-0000-0000-0000-000000000001',
  fourBed: '00000000-0000-0000-0000-000000000002',
  fiveBed: '00000000-0000-0000-0000-000000000003',
  hall: '00000000-0000-0000-0000-000000000004',
};

async function resetStayOccupancyTables() {
  await db.schema.dropTableIfExists('stay_cancellation_requests');
  await db.schema.dropTableIfExists('stay_booking_items');
  await db.schema.dropTableIfExists('stay_bookings');
  await db.schema.dropTableIfExists('stay_unit_types');

  await db.schema.createTable('stay_unit_types', (table) => {
    table.uuid('id').primary();
    table.string('code', 40).notNullable();
    table.string('display_name', 100).notNullable();
    table.integer('capacity').nullable();
    table.integer('total_inventory').notNullable();
    table.decimal('nightly_rate', 12, 2).notNullable().defaultTo(0);
    table.integer('display_order').notNullable();
    table.boolean('is_active').notNullable();
  });
  await db.schema.createTable('stay_bookings', (table) => {
    table.uuid('id').primary().defaultTo(db.raw('gen_random_uuid()'));
    table.string('reference', 40).nullable();
    table.uuid('user_id').nullable();
    table.string('booking_source', 20).notNullable().defaultTo('customer');
    table.string('status', 40).notNullable();
    table.date('check_in_date').notNullable();
    table.date('check_out_date').notNullable();
    table.integer('night_count').nullable();
    table.integer('guest_count').nullable();
    table.string('contact_name', 200).nullable();
    table.string('contact_email', 320).nullable();
    table.string('contact_phone', 20).nullable();
    table.decimal('subtotal_amount', 12, 2).nullable();
    table.decimal('discount_amount', 12, 2).nullable();
    table.uuid('coupon_id').nullable();
    table.string('coupon_code', 50).nullable();
    table.decimal('total_amount', 12, 2).nullable();
    table.text('customer_note').nullable();
    table.boolean('cancellation_policy_accepted').nullable();
    table.uuid('confirmed_by').nullable();
    table.timestamp('confirmed_at', { useTz: true }).nullable();
    table.uuid('rejected_by').nullable();
    table.timestamp('rejected_at', { useTz: true }).nullable();
    table.text('rejection_reason').nullable();
    table.timestamp('completed_at', { useTz: true }).nullable();
    table.timestamp('created_at', { useTz: true }).notNullable().defaultTo(db.fn.now());
    table.timestamp('updated_at', { useTz: true }).notNullable().defaultTo(db.fn.now());
  });
  await db.schema.createTable('stay_booking_items', (table) => {
    table.uuid('id').primary().defaultTo(db.raw('gen_random_uuid()'));
    table.uuid('booking_id').notNullable();
    table.uuid('unit_type_id').notNullable();
    table.string('unit_type_code', 40).nullable();
    table.string('unit_type_name', 100).nullable();
    table.integer('quantity').notNullable();
    table.decimal('nightly_rate', 12, 2).nullable();
    table.integer('night_count').nullable();
    table.decimal('line_total', 12, 2).nullable();
    table.timestamp('created_at', { useTz: true }).notNullable().defaultTo(db.fn.now());
  });
  await db.schema.createTable('stay_cancellation_requests', (table) => {
    table.uuid('id').primary().defaultTo(db.raw('gen_random_uuid()'));
    table.uuid('booking_id').notNullable();
    table.string('status', 40).notNullable();
    table.string('previous_booking_status', 40).notNullable();
    table.text('reason').nullable();
    table.timestamp('requested_at', { useTz: true }).notNullable();
    table.boolean('standard_full_refund_eligible').notNullable();
    table.decimal('hours_before_check_in', 12, 2).notNullable();
    table.string('refund_decision', 20).nullable();
    table.decimal('refund_amount', 12, 2).nullable();
    table.text('decision_reason').nullable();
    table.uuid('decided_by').nullable();
    table.timestamp('decided_at', { useTz: true }).nullable();
    table.timestamp('created_at', { useTz: true }).notNullable().defaultTo(db.fn.now());
    table.timestamp('updated_at', { useTz: true }).notNullable().defaultTo(db.fn.now());
  });
}

test('daily occupancy executes against PostgreSQL with checkout-exclusive status-aware totals', {
  skip: !hasTestDatabase && 'TEST_DATABASE_URL is required for PostgreSQL integration tests',
}, async () => {
  const fromDate = indiaDateOnly();
  const dayTwo = addCalendarDays(fromDate, 1);
  const dayThree = addCalendarDays(fromDate, 2);
  const dayFour = addCalendarDays(fromDate, 3);
  const now = new Date();

  await resetStayOccupancyTables();
  try {
    await db('stay_unit_types').insert([
      { id: TYPE_IDS.threeBed, code: 'three_bed_room', display_name: '3 Bed Room', capacity: 3, total_inventory: 13, nightly_rate: 1200, display_order: 1, is_active: true },
      { id: TYPE_IDS.fourBed, code: 'four_bed_room', display_name: '4 Bed Room', capacity: 4, total_inventory: 1, nightly_rate: 1500, display_order: 2, is_active: true },
      { id: TYPE_IDS.fiveBed, code: 'five_bed_room', display_name: '5 Bed Room', capacity: 5, total_inventory: 1, nightly_rate: 1600, display_order: 3, is_active: true },
      { id: TYPE_IDS.hall, code: 'hall', display_name: 'Hall', capacity: 25, total_inventory: 3, nightly_rate: 3500, display_order: 4, is_active: false },
    ]);
    await db('stay_bookings').insert([
      { id: '00000000-0000-0000-0000-000000000101', status: 'confirmed', check_in_date: fromDate, check_out_date: dayThree, night_count: 2, total_amount: 2400, confirmed_at: now },
      { id: '00000000-0000-0000-0000-000000000102', status: 'cancellation_requested', check_in_date: dayTwo, check_out_date: dayFour, night_count: 2, total_amount: 3000, confirmed_at: now },
      { id: '00000000-0000-0000-0000-000000000103', status: 'cancelled', check_in_date: dayTwo, check_out_date: dayFour, night_count: 2, total_amount: 10500, confirmed_at: now },
      { id: '00000000-0000-0000-0000-000000000104', status: 'confirmed', check_in_date: fromDate, check_out_date: dayTwo, night_count: 1, total_amount: 3500, confirmed_at: now },
    ]);
    await db('stay_booking_items').insert([
      { id: '00000000-0000-0000-0000-000000000201', booking_id: '00000000-0000-0000-0000-000000000101', unit_type_id: TYPE_IDS.threeBed, quantity: 2 },
      { id: '00000000-0000-0000-0000-000000000202', booking_id: '00000000-0000-0000-0000-000000000102', unit_type_id: TYPE_IDS.fourBed, quantity: 1 },
      { id: '00000000-0000-0000-0000-000000000203', booking_id: '00000000-0000-0000-0000-000000000103', unit_type_id: TYPE_IDS.hall, quantity: 3 },
      { id: '00000000-0000-0000-0000-000000000204', booking_id: '00000000-0000-0000-0000-000000000104', unit_type_id: TYPE_IDS.hall, quantity: 1 },
    ]);

    const report = await new StayService().getDailyOccupancy({ fromDate, days: 3 });
    const dayOne = report.days[0];
    const dayTwoReport = report.days[1];
    const dayThreeReport = report.days[2];

    assert.equal(dayOne.bookingCount, 2);
    assert.equal(dayOne.totalEarnings, 4700);
    assert.deepEqual(
      dayOne.unitTypes.map((unit) => [unit.code, unit.bookedUnits]),
      [['three_bed_room', 2], ['four_bed_room', 0], ['five_bed_room', 0], ['hall', 1]]
    );
    assert.equal(dayTwoReport.bookingCount, 2);
    assert.equal(dayTwoReport.totalEarnings, 1200);
    assert.deepEqual(
      dayTwoReport.unitTypes.map((unit) => [unit.code, unit.bookedUnits]),
      [['three_bed_room', 2], ['four_bed_room', 1], ['five_bed_room', 0], ['hall', 0]]
    );
    assert.equal(dayThreeReport.bookingCount, 1);
    assert.equal(dayThreeReport.totalEarnings, 0);
    assert.equal(dayThreeReport.unitTypes.find((unit) => unit.code === 'four_bed_room').bookedUnits, 1);
  } finally {
    await db.schema.dropTableIfExists('stay_cancellation_requests');
    await db.schema.dropTableIfExists('stay_booking_items');
    await db.schema.dropTableIfExists('stay_bookings');
    await db.schema.dropTableIfExists('stay_unit_types');
  }
});

test('admin Stay booking is immediately confirmed and its immediate cancellation is auditable', {
  skip: !hasTestDatabase && 'TEST_DATABASE_URL is required for PostgreSQL integration tests',
}, async () => {
  const checkInDate = addCalendarDays(indiaDateOnly(), 2);
  const checkOutDate = addCalendarDays(checkInDate, 2);
  const adminId = '00000000-0000-0000-0000-000000000901';

  await resetStayOccupancyTables();
  try {
    await db('stay_unit_types').insert({
      id: TYPE_IDS.threeBed,
      code: 'three_bed_room',
      display_name: '3 Bed Room',
      capacity: 3,
      total_inventory: 13,
      nightly_rate: 1200,
      display_order: 1,
      is_active: true,
    });
    const service = new StayService();
    const booking = await service.createAdminBooking(adminId, {
      checkInDate,
      checkOutDate,
      items: [{ unitTypeCode: 'three_bed_room', quantity: 1 }],
      guestCount: 3,
      contactName: 'Walk-in Guest',
      contactEmail: 'walkin@example.com',
      contactPhone: '9999999999',
      cancellationPolicyAccepted: true,
    });

    assert.equal(booking.status, 'confirmed');
    assert.equal(booking.userId, adminId);
    assert.equal(booking.bookingSource, 'admin');
    assert.equal(booking.totalAmount, 2400);
    assert.ok(booking.confirmedAt);

    const cancelled = await service.cancelAdminBooking(booking.id, adminId, {
      refundDecision: 'full',
    });
    assert.equal(cancelled.status, 'cancelled');
    assert.equal(cancelled.cancellation.status, 'approved');
    assert.equal(cancelled.cancellation.refundDecision, 'full');
    assert.equal(cancelled.cancellation.refundAmount, 2400);
    const cancellation = await db('stay_cancellation_requests').where('booking_id', booking.id).first();
    assert.equal(cancellation.decided_by, adminId);
  } finally {
    await db.schema.dropTableIfExists('stay_cancellation_requests');
    await db.schema.dropTableIfExists('stay_booking_items');
    await db.schema.dropTableIfExists('stay_bookings');
    await db.schema.dropTableIfExists('stay_unit_types');
  }
});
