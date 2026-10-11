const express = require('express');
const rateLimit = require('express-rate-limit');
const chalisaController = require('../controllers/chalisaController');
const { chalisaIdentity } = require('../middlewares/chalisaIdentity');
const validate = require('../middlewares/validate');
const {
  chalisaParticipantSchema,
  chalisaReadingSchema,
  chalisaReadingParamsSchema,
  chalisaMyStatsQuerySchema,
  chalisaBoardQuerySchema,
  chalisaArchiveQuerySchema,
} = require('../validators/schemas');

const router = express.Router();

/**
 * New guest passes need no sign-in, and each new guest may add a starting count, so creating
 * them is limited per IP. Existing guests and signed-in users renaming themselves are not
 * limited here. 20 an hour leaves room for families and carriers that share one IP.
 */
const newGuestLimiter = rateLimit({
  windowMs: 60 * 60 * 1000,
  max: parseInt(process.env.CHALISA_NEW_GUESTS_PER_HOUR, 10) || 20,
  standardHeaders: true,
  legacyHeaders: false,
  skip: (req) => Boolean(req.chalisaIdentity?.userId || req.chalisaIdentity?.guestParticipantId),
  message: {
    success: false,
    error: { code: 'RATE_LIMITED', message: 'Too many new names from this network. Please try again later.' },
  },
});

/**
 * @route PUT /api/chalisa/participant
 * @desc Set the name shown on the board. A guest without a pass gets one in the response.
 * @access Signed-in user or guest (X-Chalisa-Guest)
 */
router.put(
  '/participant',
  chalisaIdentity(),
  newGuestLimiter,
  validate(chalisaParticipantSchema),
  chalisaController.saveParticipant
);

/**
 * @route POST /api/chalisa/readings
 * @desc Count one completed round, or once per marathon a startingCount (before the first round)
 * @access Signed-in user or guest
 */
router.post(
  '/readings',
  chalisaIdentity(),
  validate(chalisaReadingSchema),
  chalisaController.recordReading
);

/**
 * @route DELETE /api/chalisa/readings/:clientRequestId
 * @desc Undo a round within 2 minutes
 * @access Signed-in user or guest
 */
router.delete(
  '/readings/:clientRequestId',
  chalisaIdentity(),
  validate(chalisaReadingParamsSchema, 'params'),
  chalisaController.undoReading
);

/**
 * @route GET /api/chalisa/me
 * @desc Personal totals, streak and day-by-day history for a month
 * @access Signed-in user or guest
 */
router.get(
  '/me',
  chalisaIdentity(),
  validate(chalisaMyStatsQuerySchema, 'query'),
  chalisaController.getMyStats
);

/**
 * @route GET /api/chalisa/marathon
 * @desc Running marathon: total, devotees and board (highest first), plus the caller's rank
 * @access Public
 */
router.get(
  '/marathon',
  chalisaIdentity({ lenient: true }),
  validate(chalisaBoardQuerySchema, 'query'),
  chalisaController.getMarathonBoard
);

/**
 * @route GET /api/chalisa/summary
 * @desc Home page entry: whether a marathon is scheduled or running, with headline numbers
 * @access Public (adds the caller's count for today when identified)
 */
router.get(
  '/summary',
  chalisaIdentity({ lenient: true }),
  chalisaController.getSummary
);

/**
 * @route GET /api/chalisa/marathons/archive
 * @desc Past marathons with their final totals
 * @access Public
 */
router.get(
  '/marathons/archive',
  validate(chalisaArchiveQuerySchema, 'query'),
  chalisaController.listArchivedMarathons
);

module.exports = router;
