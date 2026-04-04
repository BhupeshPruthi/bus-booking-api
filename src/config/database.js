const knex = require('knex');
const { getPgConnection } = require('./dbConnection');
const logger = require('../utils/logger');

const knexConfig = {
  client: 'pg',
  connection: getPgConnection(),
  pool: {
    min: 2,
    max: 10,
  },
  migrations: {
    directory: './src/db/migrations',
    tableName: 'knex_migrations',
  },
  seeds: {
    directory: './src/db/seeds',
  },
};

const db = knex(knexConfig);

// Test database connection
const testConnection = async () => {
  try {
    await db.raw('SELECT 1');
    logger.info('Database connected successfully');
  } catch (error) {
    logger.error('Database connection failed:', error);
    throw error;
  }
};

module.exports = { db, testConnection, knexConfig };
