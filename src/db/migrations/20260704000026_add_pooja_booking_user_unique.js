/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function (knex) {
  await knex.raw(`
    WITH duplicates AS (
      SELECT id
      FROM (
        SELECT
          id,
          ROW_NUMBER() OVER (
            PARTITION BY pooja_id, user_id
            ORDER BY created_at, id
          ) AS row_number
        FROM pooja_bookings
        WHERE status = 'confirmed'
      ) ranked
      WHERE row_number > 1
    )
    UPDATE pooja_bookings
    SET
      status = 'cancelled',
      cancelled_at = COALESCE(cancelled_at, NOW()),
      updated_at = NOW()
    WHERE id IN (SELECT id FROM duplicates)
  `);

  await knex.raw(`
    CREATE UNIQUE INDEX IF NOT EXISTS pooja_bookings_confirmed_user_unique
      ON pooja_bookings (pooja_id, user_id)
      WHERE status = 'confirmed'
  `);
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function (knex) {
  await knex.raw(`
    DROP INDEX IF EXISTS pooja_bookings_confirmed_user_unique
  `);
};
