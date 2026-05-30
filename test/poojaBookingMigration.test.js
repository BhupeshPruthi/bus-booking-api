const test = require('node:test');
const assert = require('node:assert/strict');

const migration = require('../src/db/migrations/20260530000024_add_pooja_booking_member_tokens');

test('pooja booking migration adds member fields and backfills per-pooja token numbers', async () => {
  const statements = [];
  const knex = {
    raw(sql) {
      statements.push(sql.replace(/\s+/g, ' ').trim());
      return Promise.resolve();
    },
  };

  await migration.up(knex);

  const joined = statements.join(' ');
  assert.match(joined, /ADD COLUMN IF NOT EXISTS member_count integer NOT NULL DEFAULT 1/);
  assert.match(joined, /ADD COLUMN IF NOT EXISTS city varchar\(100\) NOT NULL DEFAULT 'Delhi - NCR'/);
  assert.match(joined, /ADD COLUMN IF NOT EXISTS token_number integer NULL/);
  assert.match(joined, /ADD COLUMN IF NOT EXISTS cancelled_at timestamp NULL/);
  assert.match(joined, /ADD COLUMN IF NOT EXISTS cancelled_by uuid NULL/);
  assert.match(joined, /FOREIGN KEY \(cancelled_by\) REFERENCES users\(id\) ON DELETE SET NULL/);
  assert.match(joined, /COALESCE\(existing\.max_token, 0\)/);
  assert.match(joined, /ROW_NUMBER\(\) OVER \(PARTITION BY pb\.pooja_id ORDER BY pb\.created_at, pb\.id\)/);
  assert.match(joined, /CREATE UNIQUE INDEX IF NOT EXISTS pooja_bookings_confirmed_token_number_unique/);
  assert.match(joined, /ON pooja_bookings \(pooja_id, token_number\) WHERE status = 'confirmed'/);
});
