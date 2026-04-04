/**
 * PM2 — example: pm2 start ecosystem.config.cjs
 * Set env vars in env_file on the server or inline below.
 */
module.exports = {
  apps: [
    {
      name: 'bus-booking-api',
      script: 'src/app.js',
      instances: 1,
      exec_mode: 'fork',
      env_production: {
        NODE_ENV: 'production',
        TRUST_PROXY: '1',
      },
    },
  ],
};
