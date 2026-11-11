const path = require('path');
const {
  DeleteObjectCommand,
  GetObjectCommand,
  PutObjectCommand,
} = require('@aws-sdk/client-s3');
const { v4: uuidv4 } = require('uuid');
const config = require('../config');
const { NotFoundError, ValidationError } = require('../utils/errors');
const objectStorage = require('./objectStorage');

const extensionByMime = {
  'image/jpeg': '.jpg',
  'image/jpg': '.jpg',
  'image/png': '.png',
  'image/gif': '.gif',
  'image/webp': '.webp',
};

const contentTypeByExtension = {
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.png': 'image/png',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
};

const STORAGE_LABEL = 'Event image';

function assertConfigured() {
  objectStorage.assertConfigured(STORAGE_LABEL);
}

function getClient() {
  return objectStorage.getS3Client(STORAGE_LABEL);
}

function getExtension(file) {
  return extensionByMime[file.mimetype] || path.extname(file.originalname || '').toLowerCase() || '.jpg';
}

function publicUrlForKey(key) {
  return `${config.s3.publicBaseUrl.replace(/\/+$/, '')}/${key}`;
}

function getEventImageNameFromUrl(imageUrl) {
  if (!imageUrl) return null;
  try {
    const { pathname } = new URL(imageUrl);
    const segments = pathname.split('/').filter(Boolean).map(decodeURIComponent);
    const eventsIndex = segments.lastIndexOf('events');
    if (eventsIndex < 0 || !segments[eventsIndex + 1]) return null;
    return path.basename(segments[eventsIndex + 1]);
  } catch (error) {
    return null;
  }
}

function getEventImageProxyUrl(imageUrl, apiBaseUrl) {
  const imageName = getEventImageNameFromUrl(imageUrl);
  if (!imageName || !apiBaseUrl) return imageUrl || null;
  return `${apiBaseUrl.replace(/\/+$/, '')}/events/images/${encodeURIComponent(imageName)}`;
}

function getEventImageKey(imageName) {
  let decoded;
  try {
    decoded = decodeURIComponent(imageName || '');
  } catch (error) {
    throw new ValidationError('Invalid event image name');
  }

  const normalized = path.basename(decoded);
  const extension = path.extname(normalized).toLowerCase();
  if (
    !normalized ||
    normalized !== decoded ||
    !/^[a-zA-Z0-9._-]+$/.test(normalized) ||
    !contentTypeByExtension[extension]
  ) {
    throw new ValidationError('Invalid event image name');
  }
  return `events/${normalized}`;
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

async function getEventImage(imageName) {
  const key = getEventImageKey(imageName);
  try {
    const result = await getClient().send(new GetObjectCommand({
      Bucket: config.s3.bucket,
      Key: key,
    }));

    return {
      body: result.Body,
      contentType: result.ContentType
        || contentTypeByExtension[path.extname(key).toLowerCase()]
        || 'application/octet-stream',
      cacheControl: result.CacheControl || 'public, max-age=31536000, immutable',
    };
  } catch (error) {
    if (objectStorage.isMissingObjectError(error)) {
      throw new NotFoundError('Event image');
    }
    throw error;
  }
}

module.exports = {
  uploadEventImage,
  deleteEventImage,
  getEventImage,
  getEventImageNameFromUrl,
  getEventImageProxyUrl,
};
