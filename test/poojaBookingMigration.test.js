const test = require('node:test');
const assert = require('node:assert/strict');

const memberTokenMigration = require('../src/db/migrations/20260530000024_add_pooja_booking_member_tokens');
const userUniqueMigration = require('../src/db/migrations/20260704000026_add_pooja_booking_user_unique');

test('pooja booking migration adds member fields and backfills per-pooja token numbers', async () => {
  const statements = [];
  const knex = {
    raw(sql) {
      statements.push(sql.replace(/\s+/g, ' ').trim());
      return Promise.resolve();
    },
  };

  await memberTokenMigration.up(knex);

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

test('pooja booking user unique migration cancels duplicate confirmed user tokens before adding index', async () => {
  const statements = [];
  const knex = {
    raw(sql) {
      statements.push(sql.replace(/\s+/g, ' ').trim());
      return Promise.resolve();
    },
  };

  await userUniqueMigration.up(knex);

  const joined = statements.join(' ');
  assert.match(joined, /ROW_NUMBER\(\) OVER \( PARTITION BY pooja_id, user_id ORDER BY created_at, id \) AS row_number/);
  assert.match(joined, /WHERE status = 'confirmed'/);
  assert.match(joined, /SET status = 'cancelled'/);
  assert.match(joined, /cancelled_at = COALESCE\(cancelled_at, NOW\(\)\)/);
  assert.match(joined, /CREATE UNIQUE INDEX IF NOT EXISTS pooja_bookings_confirmed_user_unique/);
  assert.match(joined, /ON pooja_bookings \(pooja_id, user_id\) WHERE status = 'confirmed'/);
});
