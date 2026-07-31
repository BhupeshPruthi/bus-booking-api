// Retain this migration filename so existing Knex migration histories remain
// valid. The unused table is removed from existing databases by the later
// remove_unused_tables migration.
exports.up = async function () {};

exports.down = async function () {};
