/**
 * Add priced mattress quantities without changing the already-applied Stay
 * migration. Legacy requests retain the fact that a mattress was requested,
 * but are not charged retroactively.
 */
exports.up = async function (knex) {
  const tableName = 'stay_bookings';
  const hasLegacyRequest = await knex.schema.hasColumn(tableName, 'mattress_requested');
  const hasQuantity = await knex.schema.hasColumn(tableName, 'mattress_quantity');
  const hasNightlyRate = await knex.schema.hasColumn(tableName, 'mattress_nightly_rate');
  const hasTotal = await knex.schema.hasColumn(tableName, 'mattress_total');

  if (!hasQuantity || !hasNightlyRate || !hasTotal) {
    await knex.schema.alterTable(tableName, (table) => {
      if (!hasQuantity) {
        table.integer('mattress_quantity').notNullable().defaultTo(0);
      }
      if (!hasNightlyRate) {
        table.decimal('mattress_nightly_rate', 12, 2).notNullable().defaultTo(200);
      }
      if (!hasTotal) {
        table.decimal('mattress_total', 12, 2).notNullable().defaultTo(0);
      }
    });
  }

  if (hasLegacyRequest) {
    await knex(tableName)
      .where('mattress_requested', true)
      .andWhere('mattress_quantity', 0)
      .update({ mattress_quantity: 1 });

    await knex.schema.alterTable(tableName, (table) => {
      table.dropColumn('mattress_requested');
    });
  }
};

exports.down = async function (knex) {
  const tableName = 'stay_bookings';
  const hasLegacyRequest = await knex.schema.hasColumn(tableName, 'mattress_requested');
  const hasQuantity = await knex.schema.hasColumn(tableName, 'mattress_quantity');
  const hasNightlyRate = await knex.schema.hasColumn(tableName, 'mattress_nightly_rate');
  const hasTotal = await knex.schema.hasColumn(tableName, 'mattress_total');

  if (!hasLegacyRequest) {
    await knex.schema.alterTable(tableName, (table) => {
      table.boolean('mattress_requested').notNullable().defaultTo(false);
    });
  }

  if (hasQuantity) {
    await knex(tableName)
      .where('mattress_quantity', '>', 0)
      .update({ mattress_requested: true });
  }

  if (hasQuantity || hasNightlyRate || hasTotal) {
    await knex.schema.alterTable(tableName, (table) => {
      if (hasQuantity) table.dropColumn('mattress_quantity');
      if (hasNightlyRate) table.dropColumn('mattress_nightly_rate');
      if (hasTotal) table.dropColumn('mattress_total');
    });
  }
};
