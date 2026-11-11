const { S3Client } = require('@aws-sdk/client-s3');
const config = require('../config');
const { InternalError } = require('../utils/errors');

let client = null;

function getMissingConfigKeys() {
  const required = {
    S3_ENDPOINT: config.s3.endpoint,
    S3_BUCKET: config.s3.bucket,
    S3_ACCESS_KEY_ID: config.s3.accessKeyId,
    S3_SECRET_ACCESS_KEY: config.s3.secretAccessKey,
    S3_PUBLIC_BASE_URL: config.s3.publicBaseUrl,
  };

  return Object.entries(required)
    .filter(([, value]) => !String(value || '').trim())
    .map(([key]) => key);
}

/** Throws when the S3 bucket isn't configured. `label` names the feature in the error. */
function assertConfigured(label) {
  const missing = getMissingConfigKeys();
  if (missing.length > 0) {
    throw new InternalError(`${label} storage is not configured: missing ${missing.join(', ')}`);
  }
}

/** The shared S3 client for event images and Aarti & Path PDFs. */
function getS3Client(label) {
  assertConfigured(label);
  if (!client) {
    client = new S3Client({
      endpoint: config.s3.endpoint,
      region: config.s3.region,
      forcePathStyle: config.s3.forcePathStyle,
      credentials: {
        accessKeyId: config.s3.accessKeyId,
        secretAccessKey: config.s3.secretAccessKey,
      },
    });
  }
  return client;
}

function isMissingObjectError(error) {
  return error?.name === 'NoSuchKey' || error?.$metadata?.httpStatusCode === 404;
}

module.exports = {
  assertConfigured,
  getS3Client,
  isMissingObjectError,
};
