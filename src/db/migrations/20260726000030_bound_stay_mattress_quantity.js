const CONSTRAINT_NAME = 'stay_bookings_mattress_quantity_range_check';
const MAX_MATTRESS_QUANTITY = 100;

/**
 * Keep validated mattress quantities inside the same business range at the
 * database boundary. The explicit constraint also protects non-HTTP writers.
 */
exports.up = async function (knex) {
  await knex.raw(`
    ALTER TABLE stay_bookings
      ADD CONSTRAINT ${CONSTRAINT_NAME}
      CHECK (
        mattress_quantity >= 0
        AND mattress_quantity <= ${MAX_MATTRESS_QUANTITY}
      )
  `);
};

exports.down = async function (knex) {
  await knex.raw(`
    ALTER TABLE stay_bookings
      DROP CONSTRAINT IF EXISTS ${CONSTRAINT_NAME}
  `);
};
