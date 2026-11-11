const express = require('express');
const aartiPathController = require('../controllers/aartiPathController');
const validate = require('../middlewares/validate');
const { aartiPathParamsSchema } = require('../validators/schemas');

const router = express.Router();

/**
 * @route GET /api/aarti-path
 * @desc List Aarti & Path PDFs, oldest first
 * @access Public
 */
router.get('/', aartiPathController.list);

/**
 * @route GET /api/aarti-path/:id/pdf
 * @desc The PDF itself, served through the API domain
 * @access Public
 */
router.get(
  '/:id/pdf',
  validate(aartiPathParamsSchema, 'params'),
  aartiPathController.getPdf
);

module.exports = router;
