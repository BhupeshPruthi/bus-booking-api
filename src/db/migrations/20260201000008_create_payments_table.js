/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function (knex) {
  return knex.schema.createTable('payments', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.uuid('booking_id').unique().notNullable().references('id').inTable('bookings').onDelete('CASCADE');
    table.string('screenshot_url', 500).notNullable();
    table.enum('status', ['pending_review', 'approved', 'rejected']).defaultTo('pending_review');
    table.uuid('reviewed_by').references('id').inTable('users').onDelete('SET NULL');
    table.string('rejection_reason', 500);
    table.timestamp('uploaded_at').defaultTo(knex.fn.now());
    table.timestamp('reviewed_at');

    table.index('booking_id');
    table.index('status');
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function (knex) {
  return knex.schema.dropTable('payments');
};
