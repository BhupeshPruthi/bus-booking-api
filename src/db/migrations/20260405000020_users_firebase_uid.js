/**
 * Optional Firebase UID column (reserved / legacy). Safe if column already exists.
 * @param { import("knex").Knex } knex
 */
exports.up = async function (knex) {
  const has = await knex.schema.hasColumn('users', 'firebase_uid');
  if (!has) {
    await knex.schema.alterTable('users', (table) => {
      table.string('firebase_uid', 255).nullable().unique();
    });
  }
};

exports.down = async function (knex) {
  const has = await knex.schema.hasColumn('users', 'firebase_uid');
  if (has) {
    await knex.schema.alterTable('users', (table) => {
      table.dropColumn('firebase_uid');
    });
  }
};
