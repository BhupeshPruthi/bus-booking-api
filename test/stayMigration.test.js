const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const migration = fs.readFileSync(
  path.join(__dirname, '../src/db/migrations/20260725000027_create_stay_business.js'),
  'utf8'
);
const mattressPricingMigration = fs.readFileSync(
  path.join(__dirname, '../src/db/migrations/20260726000028_add_stay_mattress_pricing.js'),
  'utf8'
);

test('Stay migration uses aggregate inventory and immutable rate history', () => {
  assert.match(migration, /total_inventory/);
  assert.match(migration, /stay_rate_history/);
  assert.match(migration, /mattress_requested/);
  assert.doesNotMatch(migration, /createTable\('stay_units'/);
  assert.doesNotMatch(migration, /stay_booking_unit_assignments/);
});

test('Stay mattress pricing uses a forward-compatible migration', () => {
  assert.match(mattressPricingMigration, /hasColumn\(tableName, 'mattress_requested'\)/);
  assert.match(mattressPricingMigration, /mattress_quantity/);
  assert.match(mattressPricingMigration, /mattress_nightly_rate/);
  assert.match(mattressPricingMigration, /defaultTo\(200\)/);
  assert.match(mattressPricingMigration, /mattress_total/);
  assert.match(mattressPricingMigration, /dropColumn\('mattress_requested'\)/);
});

test('Stay migration preserves existing Bus, Pooja, and Event admins', () => {
  assert.match(migration, /admin_type: 'bus_admin'/);
  assert.match(migration, /'bus_admin', 'stay_admin', 'super_admin'/);
});

test('Stay migration retains status and admin action history indefinitely', () => {
  assert.match(migration, /stay_booking_status_history/);
  assert.match(migration, /stay_admin_actions/);
  assert.match(migration, /admin_role_history/);
});
