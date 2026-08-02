/**
 * Speed up rolling occupancy reports without duplicating booking data.
 *
 * @param { import("knex").Knex } knex
 */
exports.up = async function (knex) {
  await knex.raw(`
    CREATE INDEX IF NOT EXISTS stay_bookings_active_occupancy_idx
      ON stay_bookings (check_in_date, check_out_date)
      WHERE confirmed_at IS NOT NULL
        AND status IN ('confirmed', 'cancellation_requested')
  `);
};

/**
 * @param { import("knex").Knex } knex
 */
exports.down = async function (knex) {
  await knex.raw('DROP INDEX IF EXISTS stay_bookings_active_occupancy_idx');
};
