const logger = require('../utils/logger');
const config = require('../config');

/**
 * Global error handler middleware
 */
const errorHandler = (err, req, res, next) => {
  const statusCode = err.statusCode || 500;
  const isOperational = err.isOperational || false;
  const shouldLogStack = !isOperational || statusCode >= 500;
  const logLevel = shouldLogStack ? 'error' : 'warn';

  // Expected client errors, such as expired JWTs, are useful but should not look like crashes.
  logger[logLevel]({
    message: err.message,
    ...(shouldLogStack && { stack: err.stack }),
    statusCode,
    path: req.path,
    method: req.method,
    ip: req.ip,
    details: err.details,
  });

  // Send response
  res.status(statusCode).json({
    success: false,
    error: {
      code: err.code || 'INTERNAL_ERROR',
      message: isOperational ? err.message : 'Internal server error',
      ...(err.details && { details: err.details }),
      ...(config.env === 'development' && shouldLogStack && { stack: err.stack }),
    },
  });
};

/**
 * 404 Not Found handler
 */
const notFoundHandler = (req, res) => {
  res.status(404).json({
    success: false,
    error: {
      code: 'NOT_FOUND',
      message: `Route ${req.method} ${req.path} not found`,
    },
  });
};

module.exports = { errorHandler, notFoundHandler };
