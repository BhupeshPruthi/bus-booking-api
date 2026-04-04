require('dotenv').config();
const { getPgConnection } = require('./src/config/dbConnection');

const pool = {
  min: 2,
  max: 10,
};

const migrations = {
  directory: './src/db/migrations',
  tableName: 'knex_migrations',
};

const seeds = {
  directory: './src/db/seeds',
};

module.exports = {
  development: {
    client: 'pg',
    connection: getPgConnection(),
    pool,
    migrations,
    seeds,
  },

  production: {
    client: 'pg',
    connection: getPgConnection(),
    pool,
    migrations,
    seeds,
  },
};
