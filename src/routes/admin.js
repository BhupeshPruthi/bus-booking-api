const express = require('express');
const adminController = require('../controllers/adminController');
const { authenticate } = require('../middlewares/authenticate');
const { adminOrSuperUser } = require('../middlewares/authorize');
const validate = require('../middlewares/validate');
const {
  bookingQuerySchema,
  createTripSchema,
  createPoojaSchema,
  createEventSchema,
} = require('../validators/schemas');

const router = express.Router();

router.use(authenticate);

/**
 * @route POST /api/admin/events
 * @desc Create a new upcoming event
 * @access Admin or Superuser
 */
router.post(
  '/events',
  adminOrSuperUser,
  validate(createEventSchema),
  adminController.createEvent
);

router.use(adminOrSuperUser);

/**
 * @route POST /api/admin/trips
 * @desc Create a complete trip (route + stops + bus, with optional return)
 * @access Admin
 */
router.post('/trips', validate(createTripSchema), adminController.createTrip);

/**
 * @route POST /api/admin/poojas
 * @desc Schedule a new pooja
 * @access Admin
 */
router.post('/poojas', validate(createPoojaSchema), adminController.createPooja);

/**
 * @route GET /api/admin/poojas
 * @desc Get upcoming poojas (admin)
 * @access Admin
 */
router.get('/poojas', adminController.getAdminPoojas);

/**
 * @route GET /api/admin/poojas/:id
 * @desc Get pooja by ID with enrolled list (admin)
 * @access Admin
 */
router.get('/poojas/:id', adminController.getAdminPoojaById);

/**
 * @route GET /api/admin/buses
 * @desc Get all buses with filters
 * @access Admin
 */
router.get('/buses', adminController.getAllBuses);

/**
 * @route GET /api/admin/bookings
 * @desc Get all bookings with filters
 * @access Admin
 */
router.get('/bookings', validate(bookingQuerySchema, 'query'), adminController.getAllBookings);

/**
 * @route PUT /api/admin/bookings/:id/status
 * @desc Approve or reject a pending booking request
 * @access Admin
 */
router.put('/bookings/:id/status', adminController.processBookingRequest);

module.exports = router;
