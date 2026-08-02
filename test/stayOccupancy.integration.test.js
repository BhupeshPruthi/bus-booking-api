const test = require('node:test');
const assert = require('node:assert/strict');

const hasTestDatabase = Boolean(process.env.TEST_DATABASE_URL);
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
  await db.schema.dropTableIfExists('stay_booking_items');
  await db.schema.dropTableIfExists('stay_bookings');
  await db.schema.dropTableIfExists('stay_unit_types');

  await db.schema.createTable('stay_unit_types', (table) => {
    table.uuid('id').primary();
    table.string('code', 40).notNullable();
    table.string('display_name', 100).notNullable();
    table.integer('total_inventory').notNullable();
    table.integer('display_order').notNullable();
    table.boolean('is_active').notNullable();
  });
  await db.schema.createTable('stay_bookings', (table) => {
    table.uuid('id').primary();
    table.string('status', 40).notNullable();
    table.date('check_in_date').notNullable();
    table.date('check_out_date').notNullable();
    table.timestamp('confirmed_at', { useTz: true }).nullable();
    table.timestamp('completed_at', { useTz: true }).nullable();
    table.timestamp('updated_at', { useTz: true }).nullable();
  });
  await db.schema.createTable('stay_booking_items', (table) => {
    table.uuid('id').primary();
    table.uuid('booking_id').notNullable();
    table.uuid('unit_type_id').notNullable();
    table.integer('quantity').notNullable();
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
      { id: TYPE_IDS.threeBed, code: 'three_bed_room', display_name: '3 Bed Room', total_inventory: 13, display_order: 1, is_active: true },
      { id: TYPE_IDS.fourBed, code: 'four_bed_room', display_name: '4 Bed Room', total_inventory: 1, display_order: 2, is_active: true },
      { id: TYPE_IDS.fiveBed, code: 'five_bed_room', display_name: '5 Bed Room', total_inventory: 1, display_order: 3, is_active: true },
      { id: TYPE_IDS.hall, code: 'hall', display_name: 'Hall', total_inventory: 3, display_order: 4, is_active: false },
    ]);
    await db('stay_bookings').insert([
      { id: '00000000-0000-0000-0000-000000000101', status: 'confirmed', check_in_date: fromDate, check_out_date: dayThree, confirmed_at: now },
      { id: '00000000-0000-0000-0000-000000000102', status: 'cancellation_requested', check_in_date: dayTwo, check_out_date: dayFour, confirmed_at: now },
      { id: '00000000-0000-0000-0000-000000000103', status: 'cancelled', check_in_date: dayTwo, check_out_date: dayFour, confirmed_at: now },
      { id: '00000000-0000-0000-0000-000000000104', status: 'confirmed', check_in_date: fromDate, check_out_date: dayTwo, confirmed_at: now },
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
    assert.deepEqual(
      dayOne.unitTypes.map((unit) => [unit.code, unit.bookedUnits]),
      [['three_bed_room', 2], ['four_bed_room', 0], ['five_bed_room', 0], ['hall', 1]]
    );
    assert.equal(dayTwoReport.bookingCount, 2);
    assert.deepEqual(
      dayTwoReport.unitTypes.map((unit) => [unit.code, unit.bookedUnits]),
      [['three_bed_room', 2], ['four_bed_room', 1], ['five_bed_room', 0], ['hall', 0]]
    );
    assert.equal(dayThreeReport.bookingCount, 1);
    assert.equal(dayThreeReport.unitTypes.find((unit) => unit.code === 'four_bed_room').bookedUnits, 1);
  } finally {
    await db.schema.dropTableIfExists('stay_booking_items');
    await db.schema.dropTableIfExists('stay_bookings');
    await db.schema.dropTableIfExists('stay_unit_types');
  }
});
