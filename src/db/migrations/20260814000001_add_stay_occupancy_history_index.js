/**
 * Support the rolling historical occupancy report, which includes completed stays.
 *
 * @param { import("knex").Knex } knex
 */
exports.up = async function (knex) {
  await knex.raw(`
    CREATE INDEX IF NOT EXISTS stay_bookings_occupancy_history_idx
      ON stay_bookings (check_in_date, check_out_date)
      WHERE confirmed_at IS NOT NULL
        AND status IN ('confirmed', 'cancellation_requested', 'completed')
  `);
};

/**
 * @param { import("knex").Knex } knex
 */
exports.down = async function (knex) {
  await knex.raw('DROP INDEX IF EXISTS stay_bookings_occupancy_history_idx');
};
