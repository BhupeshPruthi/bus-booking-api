/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function (knex) {
  return knex.schema.createTable('bookings', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.uuid('user_id').notNullable().references('id').inTable('users').onDelete('RESTRICT');
    table.uuid('bus_id').notNullable().references('id').inTable('buses').onDelete('RESTRICT');
    table.uuid('pickup_point_id').notNullable().references('id').inTable('pickup_points').onDelete('RESTRICT');
    table.integer('seat_count').notNullable();
    table.specificType('passenger_names', 'text[]');
    table.decimal('total_amount', 10, 2).notNullable();
    table
      .enum('status', ['pending', 'payment_uploaded', 'confirmed', 'rejected', 'expired', 'cancelled'])
      .defaultTo('pending');
    table.timestamp('hold_expires_at').notNullable();
    table.timestamp('created_at').defaultTo(knex.fn.now());
    table.timestamp('updated_at').defaultTo(knex.fn.now());

    table.index('user_id');
    table.index('bus_id');
    table.index('status');
    table.index('hold_expires_at');
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function (knex) {
  return knex.schema.dropTable('bookings');
};
