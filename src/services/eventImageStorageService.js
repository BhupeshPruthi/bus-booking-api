const path = require('path');
const { DeleteObjectCommand, PutObjectCommand, S3Client } = require('@aws-sdk/client-s3');
const { v4: uuidv4 } = require('uuid');
const config = require('../config');
const { InternalError } = require('../utils/errors');

const extensionByMime = {
  'image/jpeg': '.jpg',
  'image/jpg': '.jpg',
  'image/png': '.png',
  'image/gif': '.gif',
  'image/webp': '.webp',
};

let client = null;

function getMissingConfigKeys() {
  const required = {
    'S3_ENDPOINT, AWS_S3_ENDPOINT, AWS_ENDPOINT_URL, or APP_IMAGES_S3_ENDPOINT': config.s3.endpoint,
    'S3_BUCKET, AWS_S3_BUCKET, AWS_S3_BUCKET_NAME, or APP_IMAGES_S3_BUCKET': config.s3.bucket,
    'S3_ACCESS_KEY_ID, AWS_S3_ACCESS_KEY_ID, AWS_ACCESS_KEY_ID, or APP_IMAGES_S3_ACCESS_KEY_ID': config.s3.accessKeyId,
    'S3_SECRET_ACCESS_KEY, AWS_S3_SECRET_ACCESS_KEY, AWS_SECRET_ACCESS_KEY, or APP_IMAGES_S3_SECRET_ACCESS_KEY': config.s3.secretAccessKey,
    'S3_PUBLIC_BASE_URL, AWS_S3_PUBLIC_URL, or APP_IMAGES_S3_PUBLIC_BASE_URL': config.s3.publicBaseUrl,
  };

  return Object.entries(required)
    .filter(([, value]) => !String(value || '').trim())
    .map(([key]) => key);
}

function assertConfigured() {
  const missing = getMissingConfigKeys();
  if (missing.length > 0) {
    throw new InternalError(`Event image storage is not configured: missing ${missing.join(', ')}`);
  }
}

function getClient() {
  assertConfigured();
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

function getExtension(file) {
  return extensionByMime[file.mimetype] || path.extname(file.originalname || '').toLowerCase() || '.jpg';
}

function publicUrlForKey(key) {
  return `${config.s3.publicBaseUrl.replace(/\/+$/, '')}/${key}`;
}

async function uploadEventImage(file) {
  if (!file) return null;

  const key = `events/${uuidv4()}${getExtension(file)}`;
  await getClient().send(new PutObjectCommand({
    Bucket: config.s3.bucket,
    Key: key,
    Body: file.buffer,
    ContentType: file.mimetype,
    CacheControl: 'public, max-age=31536000, immutable',
  }));

  return {
    key,
    url: publicUrlForKey(key),
  };
}

async function deleteEventImage(key) {
  if (!key) return;
  assertConfigured();
  await getClient().send(new DeleteObjectCommand({
    Bucket: config.s3.bucket,
    Key: key,
  }));
}

module.exports = {
  uploadEventImage,
  deleteEventImage,
};
