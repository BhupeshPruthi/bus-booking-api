const multer = require('multer');
const path = require('path');
const { v4: uuidv4 } = require('uuid');
const config = require('../config');
const { ValidationError } = require('../utils/errors');

// Configure storage
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, path.join(config.upload.dir, 'payments'));
  },
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname);
    const filename = `${uuidv4()}${ext}`;
    cb(null, filename);
  },
});

const memoryStorage = multer.memoryStorage();

// File filter for images only
const fileFilter = (req, file, cb) => {
  const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp'];

  if (allowedTypes.includes(file.mimetype)) {
    cb(null, true);
  } else {
    cb(new ValidationError('Only image files are allowed (jpeg, jpg, png, gif, webp)'), false);
  }
};

// Configure multer
const upload = multer({
  storage,
  fileFilter,
  limits: {
    fileSize: config.upload.maxFileSize,
  },
});

const eventImageUpload = multer({
  storage: memoryStorage,
  fileFilter,
  limits: {
    fileSize: config.upload.maxFileSize,
  },
});

/** Aarti & Path PDFs. The largest sample (Hanuman 1008 Naam) is 9.8 MB. */
const PDF_MAX_FILE_SIZE = 20 * 1024 * 1024;

const pdfFileFilter = (req, file, cb) => {
  const name = String(file.originalname || '').toLowerCase();
  // Some pickers send octet-stream; the service still checks the bytes are a real PDF.
  const looksLikePdf = file.mimetype === 'application/pdf'
    || (file.mimetype === 'application/octet-stream' && name.endsWith('.pdf'));
  if (looksLikePdf) {
    cb(null, true);
  } else {
    cb(new ValidationError('Only PDF files are allowed'), false);
  }
};

const pdfUpload = multer({
  storage: memoryStorage,
  fileFilter: pdfFileFilter,
  limits: {
    fileSize: PDF_MAX_FILE_SIZE,
    files: 1,
  },
});

/** Turns multer errors into validation errors that name the size limit for that upload. */
const multerErrorHandler = (maxFileSize) => (err, req, res, next) => {
  if (err instanceof multer.MulterError) {
    if (err.code === 'LIMIT_FILE_SIZE') {
      return next(new ValidationError(`File too large. Maximum size is ${maxFileSize / 1024 / 1024}MB`));
    }
    return next(new ValidationError(err.message));
  }
  next(err);
};

// Error handler for multer errors
const handleMulterError = multerErrorHandler(config.upload.maxFileSize);
const handlePdfMulterError = multerErrorHandler(PDF_MAX_FILE_SIZE);

module.exports = {
  upload,
  eventImageUpload,
  handleMulterError,
  pdfUpload,
  handlePdfMulterError,
  PDF_MAX_FILE_SIZE,
};
