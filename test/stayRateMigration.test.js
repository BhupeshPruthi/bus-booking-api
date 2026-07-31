const test = require('node:test');
const assert = require('node:assert/strict');

const migration = require(
  '../src/db/migrations/20260801000033_move_stay_rates_to_unit_types'
);

test('Stay rate migration preserves the established catalogue prices', () => {
  assert.deepEqual(migration.DEFAULT_RATES, {
    three_bed_room: 1200,
    four_bed_room: 1500,
    five_bed_room: 1600,
    hall: 3500,
  });
});

test('Stay rate migration backfills, validates, and retires rate history', async () => {
  const operations = [];
  const updates = [];

  const query = {
    where(criteria) {
      this.code = criteria.code;
      return this;
    },
    whereNull(column) {
      this.nullColumn = column;
      return this;
    },
    async update(values) {
      updates.push({ code: this.code, nullColumn: this.nullColumn, values });
    },
    async select(column) {
      operations.push(`select:${column}`);
      return [];
    },
  };

  const knex = (tableName) => {
    assert.equal(tableName, 'stay_unit_types');
    return Object.create(query);
  };
  knex.fn = { now: () => 'NOW' };
  knex.raw = async (sql) => operations.push(sql.replace(/\s+/g, ' ').trim());
  knex.schema = {
    hasColumn: async () => false,
    hasTable: async () => true,
    alterTable: async (tableName, callback) => {
      operations.push(`alter:${tableName}`);
      callback({ decimal: () => ({ nullable: () => undefined }) });
    },
    dropTable: async (tableName) => operations.push(`drop:${tableName}`),
  };

  await migration.up(knex);

  assert.equal(updates.length, 4);
  assert.deepEqual(updates.map(({ code, values }) => [code, values.nightly_rate]), [
    ['three_bed_room', 1200],
    ['four_bed_room', 1500],
    ['five_bed_room', 1600],
    ['hall', 3500],
  ]);
  assert.ok(operations.some((operation) => operation.includes('DISTINCT ON (unit_type_id)')));
  assert.ok(operations.some((operation) => operation.includes('SET NOT NULL')));
  assert.equal(operations.at(-1), 'drop:stay_rate_history');
});
