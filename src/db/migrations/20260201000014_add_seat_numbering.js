/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function (knex) {
  await knex.schema.alterTable('buses', (table) => {
    table.integer('seat_start_number').defaultTo(1);
  });
  await knex.schema.alterTable('bookings', (table) => {
    table.string('assigned_seats', 500);
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function (knex) {
  await knex.schema.alterTable('buses', (table) => {
    table.dropColumn('seat_start_number');
  });
  await knex.schema.alterTable('bookings', (table) => {
    table.dropColumn('assigned_seats');
  });
};
