const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const migration = fs.readFileSync(
  path.join(__dirname, '../src/db/migrations/20260725000027_create_stay_business.js'),
  'utf8'
);
const removeMattressMigration = fs.readFileSync(
  path.join(__dirname, '../src/db/migrations/20260801000031_remove_stay_mattress.js'),
  'utf8'
);
const bookingFeedMigration = fs.readFileSync(
  path.join(__dirname, '../src/db/migrations/20260726000029_create_user_booking_feed.js'),
  'utf8'
);
const stayService = fs.readFileSync(
  path.join(__dirname, '../src/services/stayService.js'),
  'utf8'
);

test('Stay migration uses aggregate inventory and immutable rate history', () => {
  assert.match(migration, /total_inventory/);
  assert.match(migration, /stay_rate_history/);
  assert.doesNotMatch(migration, /createTable\('stay_units'/);
  assert.doesNotMatch(migration, /stay_booking_unit_assignments/);
});

test('Stay mattress storage and feed fields are removed by a forward migration', () => {
  assert.match(removeMattressMigration, /dropColumns/);
  assert.match(removeMattressMigration, /mattress_quantity/);
  assert.match(removeMattressMigration, /mattress_nightly_rate/);
  assert.match(removeMattressMigration, /mattress_total/);
  assert.doesNotMatch(bookingFeedMigration, /mattressQuantity/);
  assert.doesNotMatch(bookingFeedMigration, /mattressTotal/);
});

test('Stay migration preserves existing Bus, Pooja, and Event admins', () => {
  assert.match(migration, /admin_type: 'bus_admin'/);
  assert.match(migration, /'bus_admin', 'stay_admin', 'super_admin'/);
});

test('Stay migration retains booking status and role history indefinitely', () => {
  assert.match(migration, /stay_booking_status_history/);
  assert.doesNotMatch(migration, /stay_admin_actions/);
  assert.doesNotMatch(stayService, /stay_admin_actions/);
  assert.doesNotMatch(stayService, /logAction\(/);
  assert.match(migration, /admin_role_history/);
});
