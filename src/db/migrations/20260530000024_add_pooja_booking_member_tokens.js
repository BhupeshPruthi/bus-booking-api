/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function (knex) {
  await knex.raw(`
    ALTER TABLE pooja_bookings
      ADD COLUMN IF NOT EXISTS member_count integer NOT NULL DEFAULT 1,
      ADD COLUMN IF NOT EXISTS city varchar(100) NOT NULL DEFAULT 'Delhi - NCR',
      ADD COLUMN IF NOT EXISTS token_number integer NULL,
      ADD COLUMN IF NOT EXISTS cancelled_at timestamp NULL,
      ADD COLUMN IF NOT EXISTS cancelled_by uuid NULL
  `);

  await knex.raw(`
    DO $$
    BEGIN
      IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'pooja_bookings'::regclass
          AND contype = 'f'
          AND conname = 'pooja_bookings_cancelled_by_fkey'
      ) THEN
        ALTER TABLE pooja_bookings
          ADD CONSTRAINT pooja_bookings_cancelled_by_fkey
          FOREIGN KEY (cancelled_by)
          REFERENCES users(id)
          ON DELETE SET NULL;
      END IF;
    END $$;
  `);

  await knex.raw(`
    WITH existing AS (
      SELECT pooja_id, MAX(token_number) AS max_token
      FROM pooja_bookings
      WHERE token_number IS NOT NULL
      GROUP BY pooja_id
    ),
    numbered AS (
      SELECT
        pb.id,
        COALESCE(existing.max_token, 0) +
          ROW_NUMBER() OVER (PARTITION BY pb.pooja_id ORDER BY pb.created_at, pb.id) AS rn
      FROM pooja_bookings pb
      LEFT JOIN existing ON existing.pooja_id = pb.pooja_id
      WHERE pb.token_number IS NULL
    )
    UPDATE pooja_bookings
    SET token_number = numbered.rn
    FROM numbered
    WHERE pooja_bookings.id = numbered.id
  `);

  await knex.raw(`
    ALTER TABLE pooja_bookings
      DROP CONSTRAINT IF EXISTS pooja_bookings_pooja_id_token_number_unique
  `);

  await knex.raw(`
    CREATE UNIQUE INDEX IF NOT EXISTS pooja_bookings_confirmed_token_number_unique
      ON pooja_bookings (pooja_id, token_number)
      WHERE status = 'confirmed'
  `);
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function (knex) {
  await knex.raw(`
    DROP INDEX IF EXISTS pooja_bookings_confirmed_token_number_unique
  `);

  await knex.raw(`
    ALTER TABLE pooja_bookings
      DROP CONSTRAINT IF EXISTS pooja_bookings_pooja_id_token_number_unique,
      DROP CONSTRAINT IF EXISTS pooja_bookings_cancelled_by_fkey,
      DROP COLUMN IF EXISTS cancelled_by,
      DROP COLUMN IF EXISTS cancelled_at,
      DROP COLUMN IF EXISTS token_number,
      DROP COLUMN IF EXISTS city,
      DROP COLUMN IF EXISTS member_count
  `);
};
