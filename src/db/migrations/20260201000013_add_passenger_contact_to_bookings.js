/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function (knex) {
  await knex.schema.alterTable('bookings', (table) => {
    table.string('passenger_name', 200);
    table.string('passenger_phone', 15);
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function (knex) {
  await knex.schema.alterTable('bookings', (table) => {
    table.dropColumn('passenger_name');
    table.dropColumn('passenger_phone');
  });
};
