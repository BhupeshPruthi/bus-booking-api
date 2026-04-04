/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function (knex) {
  return knex.schema.alterTable('buses', (table) => {
    table.timestamp('arrival_time').nullable();
    table.string('contact_name', 200).nullable();
    table.string('contact_phone', 20).nullable();
    table.enum('trip_type', ['one_way', 'round_trip']).defaultTo('one_way');
    table
      .uuid('return_bus_id')
      .nullable()
      .references('id')
      .inTable('buses')
      .onDelete('SET NULL');
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function (knex) {
  return knex.schema.alterTable('buses', (table) => {
    table.dropColumn('return_bus_id');
    table.dropColumn('trip_type');
    table.dropColumn('contact_phone');
    table.dropColumn('contact_name');
    table.dropColumn('arrival_time');
  });
};
