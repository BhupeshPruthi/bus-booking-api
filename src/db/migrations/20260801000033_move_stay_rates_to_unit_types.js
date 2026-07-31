const UNIT_TYPES_TABLE = 'stay_unit_types';
const RATE_HISTORY_TABLE = 'stay_rate_history';

const DEFAULT_RATES = {
  three_bed_room: 1200,
  four_bed_room: 1500,
  five_bed_room: 1600,
  hall: 3500,
};

exports.up = async function (knex) {
  const hasNightlyRate = await knex.schema.hasColumn(UNIT_TYPES_TABLE, 'nightly_rate');
  if (!hasNightlyRate) {
    await knex.schema.alterTable(UNIT_TYPES_TABLE, (table) => {
      table.decimal('nightly_rate', 12, 2).nullable();
    });
  }

  const hasRateHistory = await knex.schema.hasTable(RATE_HISTORY_TABLE);
  if (hasRateHistory) {
    await knex.raw(`
      UPDATE ${UNIT_TYPES_TABLE} AS unit_type
      SET nightly_rate = latest_rate.nightly_rate,
          updated_at = NOW()
      FROM (
        SELECT DISTINCT ON (unit_type_id)
          unit_type_id,
          nightly_rate
        FROM ${RATE_HISTORY_TABLE}
        WHERE effective_from <= NOW()
        ORDER BY unit_type_id, effective_from DESC, created_at DESC
      ) AS latest_rate
      WHERE unit_type.id = latest_rate.unit_type_id
        AND unit_type.nightly_rate IS NULL
    `);
  }

  for (const [code, nightlyRate] of Object.entries(DEFAULT_RATES)) {
    await knex(UNIT_TYPES_TABLE)
      .where({ code })
      .whereNull('nightly_rate')
      .update({ nightly_rate: nightlyRate, updated_at: knex.fn.now() });
  }

  const missingRates = await knex(UNIT_TYPES_TABLE)
    .whereNull('nightly_rate')
    .select('code');
  if (missingRates.length > 0) {
    const codes = missingRates.map((row) => row.code).join(', ');
    throw new Error(`Cannot finish Stay rate migration; missing rates for: ${codes}`);
  }

  await knex.raw(`
    ALTER TABLE ${UNIT_TYPES_TABLE}
      ALTER COLUMN nightly_rate SET NOT NULL
  `);

  if (hasRateHistory) {
    await knex.schema.dropTable(RATE_HISTORY_TABLE);
  }
};

// This pre-release data move is intentionally irreversible. Booked rates remain
// snapshotted in stay_booking_items and current rates remain in stay_unit_types.
exports.down = async function () {};

exports.DEFAULT_RATES = DEFAULT_RATES;
