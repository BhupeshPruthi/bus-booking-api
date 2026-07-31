const bookingFeedMigration = require('./20260726000029_create_user_booking_feed');

const TABLE_NAME = 'stay_bookings';
const CONSTRAINT_NAME = 'stay_bookings_mattress_quantity_range_check';

exports.up = async function (knex) {
  // Replace the feed first so it no longer depends on the columns being removed.
  await bookingFeedMigration.up(knex);

  await knex.raw(`
    ALTER TABLE ${TABLE_NAME}
      DROP CONSTRAINT IF EXISTS ${CONSTRAINT_NAME}
  `);

  const removableColumns = [];
  for (const column of ['mattress_quantity', 'mattress_nightly_rate', 'mattress_total']) {
    if (await knex.schema.hasColumn(TABLE_NAME, column)) removableColumns.push(column);
  }
  if (removableColumns.length > 0) {
    await knex.schema.alterTable(TABLE_NAME, (table) => {
      table.dropColumns(...removableColumns);
    });
  }
};

exports.down = async function (knex) {
  const hasQuantity = await knex.schema.hasColumn(TABLE_NAME, 'mattress_quantity');
  const hasNightlyRate = await knex.schema.hasColumn(TABLE_NAME, 'mattress_nightly_rate');
  const hasTotal = await knex.schema.hasColumn(TABLE_NAME, 'mattress_total');

  if (!hasQuantity || !hasNightlyRate || !hasTotal) {
    await knex.schema.alterTable(TABLE_NAME, (table) => {
      if (!hasQuantity) table.integer('mattress_quantity').notNullable().defaultTo(0);
      if (!hasNightlyRate) {
        table.decimal('mattress_nightly_rate', 12, 2).notNullable().defaultTo(200);
      }
      if (!hasTotal) table.decimal('mattress_total', 12, 2).notNullable().defaultTo(0);
    });
  }

  await knex.raw(`
    ALTER TABLE ${TABLE_NAME}
      DROP CONSTRAINT IF EXISTS ${CONSTRAINT_NAME};
    ALTER TABLE ${TABLE_NAME}
      ADD CONSTRAINT ${CONSTRAINT_NAME}
      CHECK (mattress_quantity >= 0 AND mattress_quantity <= 100)
  `);
};
