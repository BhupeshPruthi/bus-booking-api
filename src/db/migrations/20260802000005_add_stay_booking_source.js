const BOOKING_SOURCE_CHECK = 'stay_bookings_source_check';

exports.up = async function up(knex) {
  await knex.schema.alterTable('stay_bookings', (table) => {
    table.string('booking_source', 20).notNullable().defaultTo('customer');
  });
  await knex.raw(`
    ALTER TABLE stay_bookings
      ADD CONSTRAINT ${BOOKING_SOURCE_CHECK}
      CHECK (booking_source IN ('customer', 'admin'))
  `);
};

exports.down = async function down(knex) {
  await knex.raw(`
    ALTER TABLE stay_bookings
      DROP CONSTRAINT IF EXISTS ${BOOKING_SOURCE_CHECK}
  `);
  await knex.schema.alterTable('stay_bookings', (table) => {
    table.dropColumn('booking_source');
  });
};
