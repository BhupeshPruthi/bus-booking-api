#!/usr/bin/env node
/**
 * Lists all public tables and prints row counts + full row data (JSON).
 * Uses DATABASE_URL or DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD from .env
 * (same as knexfile development).
 *
 * Usage: node scripts/dump-db-data.js
 *    or: npm run db:dump-data
 */
require('dotenv').config();
const { Client } = require('pg');

function getConnectionConfig() {
  if (process.env.DATABASE_URL) {
    return { connectionString: process.env.DATABASE_URL };
  }
  return {
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT, 10) || 5432,
    database: process.env.DB_NAME || 'bus_booking',
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || 'postgres',
  };
}

async function main() {
  const client = new Client(getConnectionConfig());
  await client.connect();

  const { rows: tables } = await client.query(`
    SELECT tablename
    FROM pg_catalog.pg_tables
    WHERE schemaname = 'public'
    ORDER BY tablename
  `);

  console.log('=== PUBLIC TABLES ===\n');
  for (const { tablename } of tables) {
    const safe = `"${tablename.replace(/"/g, '""')}"`;
    const { rows: countRows } = await client.query(
      `SELECT COUNT(*)::bigint AS c FROM ${safe}`
    );
    const n = Number(countRows[0].c);
    console.log(`--- ${tablename} (${n} rows) ---`);
    if (n === 0) {
      console.log('[]\n');
      continue;
    }
    const { rows: data } = await client.query(`SELECT * FROM ${safe}`);
    console.log(JSON.stringify(data, null, 2));
    console.log('');
  }

  await client.end();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
