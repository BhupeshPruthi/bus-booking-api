/**
 * Lets a Chalisa admin add rounds to their own entry in one go (kind 'admin').
 *
 * @param { import("knex").Knex } knex
 */
exports.up = async function (knex) {
  await knex.raw('ALTER TABLE chalisa_readings DROP CONSTRAINT IF EXISTS chalisa_readings_kind_count');
  await knex.raw(`
    ALTER TABLE chalisa_readings ADD CONSTRAINT chalisa_readings_kind_count CHECK (
      (kind = 'round' AND count = 1)
      OR (kind = 'opening' AND count BETWEEN 1 AND 5000)
      OR (kind = 'admin' AND count BETWEEN 1 AND 5000)
    )
  `);
};

/**
 * @param { import("knex").Knex } knex
 */
exports.down = async function (knex) {
  // Admin additions can't satisfy the original rule, so they are removed first.
  const admin = await knex('chalisa_readings').where('kind', 'admin').select('participant_id', 'marathon_id', 'count');
  for (const row of admin) {
    await knex('chalisa_marathon_totals')
      .where({ marathon_id: row.marathon_id, participant_id: row.participant_id })
      .decrement('total', row.count);
  }
  await knex('chalisa_readings').where('kind', 'admin').del();
  await knex.raw('ALTER TABLE chalisa_readings DROP CONSTRAINT IF EXISTS chalisa_readings_kind_count');
  await knex.raw(`
    ALTER TABLE chalisa_readings ADD CONSTRAINT chalisa_readings_kind_count CHECK (
      (kind = 'round' AND count = 1) OR (kind = 'opening' AND count BETWEEN 1 AND 5000)
    )
  `);
};
