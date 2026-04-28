const express = require('express');
const consumerController = require('../controllers/consumerController');
const { authenticate } = require('../middlewares/authenticate');
const validate = require('../middlewares/validate');
const {
  searchBusesSchema,
  createBookingSchema,
  cancellationRequestSchema,
  bookPoojaTokenSchema,
} = require('../validators/schemas');

const router = express.Router();

/**
 * @route GET /api/buses
 * @desc Search available buses
 * @access Public
 */
router.get('/buses', validate(searchBusesSchema, 'query'), consumerController.searchBuses);

/**
 * @route GET /api/buses/:id
 * @desc Get bus details with availability
 * @access Public
 */
router.get('/buses/:id', consumerController.getBusDetails);

/**
 * @route GET /api/poojas
 * @desc Get upcoming poojas
 * @access Public
 */
router.get('/poojas', consumerController.getUpcomingPoojas);

/**
 * @route GET /api/poojas/:id
 * @desc Get pooja details
 * @access Public
 */
router.get('/poojas/:id', consumerController.getPoojaDetails);

/**
 * @route GET /api/events
 * @desc Get upcoming events
 * @access Public
 */
router.get('/events', consumerController.getUpcomingEvents);

/**
 * @route POST /api/bookings
 * @desc Create a new booking
 * @access Consumer
 */
router.post(
  '/bookings',
  authenticate,
  validate(createBookingSchema),
  consumerController.createBooking
);

/**
 * @route GET /api/bookings
 * @desc Get user's bookings
 * @access Consumer
 */
router.get('/bookings', authenticate, consumerController.getMyBookings);

/**
 * @route GET /api/bookings/:id
 * @desc Get booking by ID
 * @access Consumer (own bookings only)
 */
router.get('/bookings/:id', authenticate, consumerController.getBookingById);

/**
 * @route POST /api/bookings/:id/cancellation-request
 * @desc Request cancellation for a confirmed booking
 * @access Consumer (own bookings only)
 */
router.post(
  '/bookings/:id/cancellation-request',
  authenticate,
  validate(cancellationRequestSchema),
  consumerController.requestBookingCancellation
);

/**
 * @route POST /api/poojas/:id/bookings
 * @desc Book 1 token for a pooja
 * @access Consumer
 */
router.post(
  '/poojas/:id/bookings',
  authenticate,
  validate(bookPoojaTokenSchema),
  consumerController.bookPoojaToken
);

module.exports = router;
