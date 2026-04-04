/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function (knex) {
  return knex.schema.createTable('buses', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.uuid('route_id').notNullable().references('id').inTable('routes').onDelete('RESTRICT');
    table.string('bus_name', 200).notNullable();
    table.integer('total_seats').notNullable();
    table.decimal('price', 10, 2).notNullable();
    table.timestamp('departure_time').notNullable();
    table.integer('booking_hold_minutes').defaultTo(180); // 3 hours default
    table.enum('status', ['scheduled', 'departed', 'cancelled']).defaultTo('scheduled');
    table.timestamp('created_at').defaultTo(knex.fn.now());
    table.timestamp('updated_at').defaultTo(knex.fn.now());

    table.index('route_id');
    table.index('departure_time');
    table.index('status');
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function (knex) {
  return knex.schema.dropTable('buses');
};
