const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const tripReportsMigration = fs.readFileSync(
  path.join(__dirname, '../src/db/migrations/20260201000009_create_trip_reports_table.js'),
  'utf8'
);
const stayMigration = fs.readFileSync(
  path.join(__dirname, '../src/db/migrations/20260725000027_create_stay_business.js'),
  'utf8'
);
const cleanupMigration = require(
  '../src/db/migrations/20260801000032_remove_unused_tables'
);

test('fresh databases do not create retired tables', () => {
  assert.doesNotMatch(tripReportsMigration, /createTable\('trip_reports'/);
  assert.doesNotMatch(stayMigration, /createTable\('admin_role_history'/);
  assert.doesNotMatch(stayMigration, /createTable\('stay_admin_actions'/);
});

test('cleanup migration removes all retired tables idempotently', async () => {
  const droppedTables = [];
  const knex = {
    schema: {
      dropTableIfExists: async (tableName) => droppedTables.push(tableName),
    },
  };

  await cleanupMigration.up(knex);

  assert.deepEqual(droppedTables, [
    'stay_admin_actions',
    'admin_role_history',
    'trip_reports',
  ]);
});
