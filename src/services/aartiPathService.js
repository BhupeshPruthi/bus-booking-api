const {
  DeleteObjectCommand,
  GetObjectCommand,
  PutObjectCommand,
} = require('@aws-sdk/client-s3');
const { v4: uuidv4 } = require('uuid');
const config = require('../config');
const { db } = require('../config/database');
const logger = require('../utils/logger');
const { NotFoundError, ValidationError } = require('../utils/errors');
const objectStorage = require('./objectStorage');

const TABLE = 'aarti_path_items';
const STORAGE_LABEL = 'Aarti & Path';
const KEY_PREFIX = 'aarti-path/';
const TITLE_MAX_LENGTH = 80;
/** Items never change once added, so their PDF can be cached for good. */
const PDF_CACHE_CONTROL = 'public, max-age=31536000, immutable';

/** Trims and collapses inner whitespace, so "  Bajrang   Baan " is stored as "Bajrang Baan". */
function normalizeTitle(title) {
  return String(title || '').replace(/\s+/g, ' ').trim();
}

/** A PDF starts with "%PDF-" within its first 1024 bytes. */
function isPdfBuffer(buffer) {
  if (!Buffer.isBuffer(buffer) || buffer.length < 5) return false;
  const index = buffer.subarray(0, 1024).indexOf('%PDF-');
  return index !== -1;
}

/** inline Content-Disposition with an ASCII fallback name and the full UTF-8 title. */
function contentDisposition(title) {
  const name = normalizeTitle(title) || 'Aarti and Path';
  const ascii = name.replace(/[^A-Za-z0-9 ._()-]/g, '').replace(/\s+/g, ' ').trim() || 'aarti-path';
  return `inline; filename="${ascii}.pdf"; filename*=UTF-8''${encodeURIComponent(name)}.pdf`;
}

function formatItem(row, apiBaseUrl) {
  return {
    id: row.id,
    title: row.title,
    pageCount: row.page_count ?? null,
    sizeBytes: Number(row.size_bytes),
    pdfUrl: apiBaseUrl ? `${apiBaseUrl.replace(/\/+$/, '')}/aarti-path/${row.id}/pdf` : null,
    createdAt: row.created_at,
  };
}

function s3() {
  return objectStorage.getS3Client(STORAGE_LABEL);
}

async function deleteStoredPdf(key) {
  await s3().send(new DeleteObjectCommand({ Bucket: config.s3.bucket, Key: key }));
}

class AartiPathService {
  async list({ apiBaseUrl } = {}) {
    const rows = await db(TABLE).orderBy([
      { column: 'created_at', order: 'asc' },
      { column: 'id', order: 'asc' },
    ]);
    return rows.map((row) => formatItem(row, apiBaseUrl));
  }

  async create(adminUserId, data, file, { apiBaseUrl } = {}) {
    if (!file) {
      throw new ValidationError('Choose a PDF to upload', [
        { field: 'file', message: 'A PDF file is required' },
      ]);
    }
    if (!isPdfBuffer(file.buffer)) {
      throw new ValidationError('Only PDF files are allowed', [
        { field: 'file', message: 'This file is not a PDF' },
      ]);
    }

    const title = normalizeTitle(data.title);
    if (!title || title.length > TITLE_MAX_LENGTH) {
      throw new ValidationError('Validation failed', [
        { field: 'title', message: `Title must be 1 to ${TITLE_MAX_LENGTH} characters` },
      ]);
    }

    const key = `${KEY_PREFIX}${uuidv4()}.pdf`;
    await s3().send(new PutObjectCommand({
      Bucket: config.s3.bucket,
      Key: key,
      Body: file.buffer,
      ContentType: 'application/pdf',
      CacheControl: PDF_CACHE_CONTROL,
    }));

    try {
      const [row] = await db(TABLE)
        .insert({
          title,
          file_key: key,
          size_bytes: file.buffer.length,
          page_count: data.pageCount ?? null,
          created_by: adminUserId,
        })
        .returning('*');
      return formatItem(row, apiBaseUrl);
    } catch (error) {
      await deleteStoredPdf(key).catch(() => {});
      throw error;
    }
  }

  async getPdf(id) {
    const row = await db(TABLE).where('id', id).first();
    if (!row) throw new NotFoundError('Aarti or Path');

    try {
      const result = await s3().send(new GetObjectCommand({
        Bucket: config.s3.bucket,
        Key: row.file_key,
      }));
      return {
        body: result.Body,
        contentLength: result.ContentLength ?? Number(row.size_bytes),
        contentDisposition: contentDisposition(row.title),
        cacheControl: PDF_CACHE_CONTROL,
      };
    } catch (error) {
      if (objectStorage.isMissingObjectError(error)) {
        throw new NotFoundError('Aarti or Path PDF');
      }
      throw error;
    }
  }

  async remove(id, adminUserId) {
    const row = await db(TABLE).where('id', id).first();
    if (!row) throw new NotFoundError('Aarti or Path');

    await db(TABLE).where('id', id).del();
    // The row is gone, so the item has left everyone's list. A leftover file only costs storage.
    try {
      await deleteStoredPdf(row.file_key);
    } catch (error) {
      logger.warn({
        message: 'Aarti & Path PDF left in storage after delete',
        event: 'aarti_path_file_delete_failed',
        itemId: id,
        fileKey: row.file_key,
        adminUserId,
        error: error.message,
      });
    }

    logger.info({
      message: 'Aarti & Path item deleted',
      event: 'aarti_path_item_deleted',
      itemId: id,
      title: row.title,
      adminUserId,
    });
    return { id };
  }
}

module.exports = new AartiPathService();
module.exports.helpers = {
  normalizeTitle,
  isPdfBuffer,
  contentDisposition,
  formatItem,
};
module.exports.constants = {
  TITLE_MAX_LENGTH,
  KEY_PREFIX,
};
