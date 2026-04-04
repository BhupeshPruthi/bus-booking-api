/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function (knex) {
  return knex.schema.createTable('trip_reports', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.uuid('bus_id').unique().notNullable().references('id').inTable('buses').onDelete('CASCADE');
    
    // Summary stats
    table.integer('total_seats').notNullable();
    table.integer('booked_seats').notNullable();
    table.decimal('occupancy_percentage', 5, 2).notNullable();
    table.decimal('total_revenue', 12, 2).notNullable();
    table.integer('total_bookings').notNullable();
    table.integer('confirmed_bookings').notNullable();
    
    // Passenger list stored as JSON
    table.jsonb('passenger_list').notNullable();
    
    // Metadata
    table.uuid('generated_by').notNullable().references('id').inTable('users').onDelete('SET NULL');
    table.timestamp('generated_at').defaultTo(knex.fn.now());
    
    table.index('bus_id');
    table.index('generated_at');
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function (knex) {
  return knex.schema.dropTable('trip_reports');
};
