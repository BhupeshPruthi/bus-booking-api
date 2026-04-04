require('dotenv').config();

/**
 * PostgreSQL connection for Knex: prefer DATABASE_URL (typical on VPS / managed DB),
 * else discrete DB_* variables.
 */
function getPgConnection() {
  const url = process.env.DATABASE_URL?.trim();
  if (url) {
    return url;
  }
  return {
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT, 10) || 5432,
    database: process.env.DB_NAME || 'bus_booking',
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || 'postgres',
  };
}

module.exports = { getPgConnection };
