const UNUSED_TABLES = [
  'stay_admin_actions',
  'admin_role_history',
  'trip_reports',
];

exports.up = async function (knex) {
  for (const tableName of UNUSED_TABLES) {
    await knex.schema.dropTableIfExists(tableName);
  }
};

// This pre-release cleanup intentionally does not recreate unused tables or
// attempt to restore data that the application never read or wrote.
exports.down = async function () {};
