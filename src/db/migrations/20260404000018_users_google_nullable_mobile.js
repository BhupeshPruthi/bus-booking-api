/**
 * Google Sign-In: allow users without a phone; link Google account via google_sub.
 * @param { import("knex").Knex } knex
 */
exports.up = async function (knex) {
  await knex.raw('ALTER TABLE users ALTER COLUMN mobile DROP NOT NULL');

  const hasGoogleSub = await knex.schema.hasColumn('users', 'google_sub');
  if (!hasGoogleSub) {
    await knex.schema.alterTable('users', (table) => {
      table.string('google_sub', 255).nullable().unique();
    });
  }
};

exports.down = async function (knex) {
  const hasGoogleSub = await knex.schema.hasColumn('users', 'google_sub');
  if (hasGoogleSub) {
    await knex.schema.alterTable('users', (table) => {
      table.dropColumn('google_sub');
    });
  }
  await knex.raw('ALTER TABLE users ALTER COLUMN mobile SET NOT NULL');
};
