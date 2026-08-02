const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const {
  indiaDateOnly,
  dailyOccupancyRange,
  formatDailyOccupancy,
} = require('../src/services/stayService').helpers;
const { stayDailyOccupancySchema } = require('../src/validators/schemas');

test('rolling occupancy defaults to 30 India calendar days including today', () => {
  const now = new Date('2026-08-01T20:00:00.000Z');
  assert.equal(indiaDateOnly(now), '2026-08-02');
  assert.deepEqual(dailyOccupancyRange({}, now), {
    fromDate: '2026-08-02',
    toDate: '2026-08-31',
    dayCount: 30,
  });
});

test('rolling occupancy accepts a future start and caps the report at 90 days', () => {
  const now = new Date('2026-08-02T00:00:00.000Z');
  assert.deepEqual(dailyOccupancyRange({ fromDate: '2026-08-10', days: 2 }, now), {
    fromDate: '2026-08-10',
    toDate: '2026-08-11',
    dayCount: 2,
  });
  assert.throws(() => dailyOccupancyRange({ days: 91 }, now), /between 1 and 90/);
  assert.throws(
    () => dailyOccupancyRange({ fromDate: '2026-08-01' }, now),
    /cannot be before today/
  );
});

test('daily occupancy formats every category with booked and available quantities', () => {
  const report = formatDailyOccupancy('2026-08-02', '2026-08-03', [
    {
      occupancy_date: '2026-08-02',
      booking_count: 2,
      code: 'three_bed_room',
      display_name: '3 Bed Room',
      total_inventory: 13,
      booked_units: 2,
    },
    {
      occupancy_date: '2026-08-02',
      booking_count: 2,
      code: 'hall',
      display_name: 'Hall',
      total_inventory: 3,
      booked_units: 1,
    },
    {
      occupancy_date: '2026-08-03',
      booking_count: 0,
      code: 'three_bed_room',
      display_name: '3 Bed Room',
      total_inventory: 13,
      booked_units: 0,
    },
  ]);

  assert.equal(report.days.length, 2);
  assert.equal(report.days[0].bookingCount, 2);
  assert.deepEqual(report.days[0].unitTypes[0], {
    code: 'three_bed_room',
    displayName: '3 Bed Room',
    bookedUnits: 2,
    totalUnits: 13,
    availableUnits: 11,
  });
  assert.equal(report.days[1].unitTypes[0].availableUnits, 13);
});

test('daily occupancy query validation supplies the default and rejects oversized ranges', () => {
  assert.equal(stayDailyOccupancySchema.validate({}).value.days, 30);
  assert.match(
    stayDailyOccupancySchema.validate({ days: 91 }).error?.message || '',
    /less than or equal to 90/
  );
});

test('occupancy index covers only confirmed inventory-holding bookings', () => {
  const source = fs.readFileSync(
    path.join(
      __dirname,
      '../src/db/migrations/20260802000004_add_stay_occupancy_index.js'
    ),
    'utf8'
  );
  assert.match(source, /confirmed_at IS NOT NULL/);
  assert.match(source, /'confirmed', 'cancellation_requested'/);
});
