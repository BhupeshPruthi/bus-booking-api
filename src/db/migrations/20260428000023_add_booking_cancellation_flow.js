/**
 * Add a pending cancellation state and audit fields for user/admin cancellation flows.
 *
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function (knex) {
  await knex.raw(`
    ALTER TYPE bookings_status_enum
    ADD VALUE IF NOT EXISTS 'cancellation_requested'
  `);

  await knex.schema.alterTable('bookings', (table) => {
    table.timestamp('cancellation_requested_at').nullable();
    table.text('cancellation_reason').nullable();
    table.text('cancellation_rejection_reason').nullable();
    table.string('cancellation_previous_status', 50).nullable();
    table.timestamp('cancelled_at').nullable();
    table.uuid('cancelled_by').nullable().references('id').inTable('users').onDelete('SET NULL');
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function (knex) {
  await knex.schema.alterTable('bookings', (table) => {
    table.dropColumn('cancelled_by');
    table.dropColumn('cancelled_at');
    table.dropColumn('cancellation_previous_status');
    table.dropColumn('cancellation_rejection_reason');
    table.dropColumn('cancellation_reason');
    table.dropColumn('cancellation_requested_at');
  });
};

exports.config = { transaction: false };
