exports.up = async function (knex) {
  const hasAppleSub = await knex.schema.hasColumn('users', 'apple_sub');
  if (!hasAppleSub) await knex.schema.alterTable('users', (table) => table.string('apple_sub', 255).unique());
  const hasDeletedAt = await knex.schema.hasColumn('users', 'deleted_at');
  if (!hasDeletedAt) await knex.schema.alterTable('users', (table) => table.timestamp('deleted_at').nullable());
};

exports.down = async function (knex) {
  const hasDeletedAt = await knex.schema.hasColumn('users', 'deleted_at');
  if (hasDeletedAt) await knex.schema.alterTable('users', (table) => table.dropColumn('deleted_at'));
  const hasAppleSub = await knex.schema.hasColumn('users', 'apple_sub');
  if (hasAppleSub) await knex.schema.alterTable('users', (table) => table.dropColumn('apple_sub'));
};
