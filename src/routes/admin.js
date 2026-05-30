const express = require('express');
const adminController = require('../controllers/adminController');
const { authenticate } = require('../middlewares/authenticate');
const { adminOrSuperUser } = require('../middlewares/authorize');
const validate = require('../middlewares/validate');
const {
  bookingQuerySchema,
  adminCancelBookingSchema,
  createTripSchema,
  createPoojaSchema,
  createEventSchema,
  adminCancelPoojaBookingSchema,
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
 * @route POST /api/admin/poojas/:poojaId/bookings/:bookingId/cancel
 * @desc Cancel a confirmed pooja token booking
 * @access Admin
 */
router.post(
  '/poojas/:poojaId/bookings/:bookingId/cancel',
  validate(adminCancelPoojaBookingSchema),
  adminController.cancelPoojaBooking
);

/**
 * @route GET /api/admin/buses
 * @desc Get all buses with filters
 * @access Admin
 */
router.get('/buses', adminController.getAllBuses);

/**
 * @route DELETE /api/admin/buses/:id
 * @desc Delete a future bus or cancel it if bookings exist
 * @access Admin
 */
router.delete('/buses/:id', adminController.deleteBus);

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

/**
 * @route POST /api/admin/bookings/:id/cancel
 * @desc Admin direct cancellation for any active booking
 * @access Admin
 */
router.post(
  '/bookings/:id/cancel',
  validate(adminCancelBookingSchema),
  adminController.cancelBooking
);

module.exports = router;
