const fs = require('fs');
require('dotenv').config();

/**
 * Production needs a real secret: env var and/or file (Docker/Kubernetes secrets).
 */
function resolveJwtSecret() {
  const fromEnv = process.env.JWT_SECRET?.trim();
  if (fromEnv) return fromEnv;
  const filePath = process.env.JWT_SECRET_FILE?.trim();
  if (filePath && fs.existsSync(filePath)) {
    return fs.readFileSync(filePath, 'utf8').trim();
  }
  return undefined;
}

const jwtSecretResolved = resolveJwtSecret();
const googleClientIds = (process.env.GOOGLE_CLIENT_IDS || process.env.GOOGLE_CLIENT_ID || '')
  .split(',')
  .map((id) => id.trim())
  .filter(Boolean);

const config = {
  env: process.env.NODE_ENV || 'development',
  port: parseInt(process.env.PORT, 10) || 8080,
  /** Bind address: use 0.0.0.0 so phones on the same LAN can reach the API (not only localhost). */
  host: process.env.HOST || '0.0.0.0',

  db: {
    url: process.env.DATABASE_URL,
    host: process.env.DB_HOST || 'localhost',
    port: parseInt(process.env.DB_PORT, 10) || 5432,
    name: process.env.DB_NAME || 'bus_booking',
    user: process.env.DB_USER || 'postgres',
    password: process.env.DB_PASSWORD || 'postgres',
  },

  jwt: {
    secret: jwtSecretResolved || 'dev-secret-key',
    expiresIn: process.env.JWT_EXPIRES_IN || '15m',
    refreshExpiresIn: process.env.JWT_REFRESH_EXPIRES_IN || '30d',
  },

  otp: {
    expiryMinutes: parseInt(process.env.OTP_EXPIRY_MINUTES, 10) || 5,
    length: parseInt(process.env.OTP_LENGTH, 10) || 6,
  },

  sms: {
    provider: process.env.SMS_PROVIDER || 'console',
    twilio: {
      accountSid: process.env.TWILIO_ACCOUNT_SID,
      authToken: process.env.TWILIO_AUTH_TOKEN,
      phoneNumber: process.env.TWILIO_PHONE_NUMBER,
    },
  },

  superUserMobile: process.env.SUPER_USER_MOBILE || '',
  /** Google account email treated as superuser (same privileges as legacy mobile superuser) */
  superUserEmail: process.env.SUPER_USER_EMAIL
    ? String(process.env.SUPER_USER_EMAIL).toLowerCase().trim()
    : '',

  google: {
    /** OAuth 2.0 Web client ID (used to verify Android ID tokens) */
    clientId: googleClientIds[0] || '',
    /** Optional comma-separated list for migration between Web client IDs */
    clientIds: googleClientIds,
  },

  upload: {
    dir: process.env.UPLOAD_DIR || 'uploads',
    maxFileSize: parseInt(process.env.MAX_FILE_SIZE, 10) || 5 * 1024 * 1024, // 5MB
  },
};

if (!jwtSecretResolved && config.env === 'production') {
  throw new Error(
    'Missing JWT secret in production. Set JWT_SECRET in the server environment (PaaS secrets, systemd, or .env). ' +
      'Optional: JWT_SECRET_FILE=/path/to/file for Docker/Kubernetes secret files. ' +
      'Generate: openssl rand -hex 32'
  );
}

module.exports = config;
