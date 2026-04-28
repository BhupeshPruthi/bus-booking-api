/**
 * Store bus schedule values as real instants. Existing values were written as UTC
 * timestamps, so interpret the current timestamp-without-time-zone values as UTC.
 *
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function (knex) {
  await knex.raw(`
    ALTER TABLE buses
      ALTER COLUMN departure_time TYPE timestamptz
        USING departure_time AT TIME ZONE 'UTC',
      ALTER COLUMN arrival_time TYPE timestamptz
        USING arrival_time AT TIME ZONE 'UTC'
  `);
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function (knex) {
  await knex.raw(`
    ALTER TABLE buses
      ALTER COLUMN departure_time TYPE timestamp
        USING departure_time AT TIME ZONE 'UTC',
      ALTER COLUMN arrival_time TYPE timestamp
        USING arrival_time AT TIME ZONE 'UTC'
  `);
};
