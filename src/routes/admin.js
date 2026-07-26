const express = require('express');
const adminController = require('../controllers/adminController');
const stayAdminController = require('../controllers/stayAdminController');
const adminManagementController = require('../controllers/adminManagementController');
const { authenticate } = require('../middlewares/authenticate');
const {
  busAdminOrSuperUser,
  poojaAdminOrSuperUser,
  eventAdminOrSuperUser,
  stayAdminOrSuperUser,
  adminManagerOnly,
} = require('../middlewares/authorize');
const { eventImageUpload, handleMulterError } = require('../middlewares/upload');
const validate = require('../middlewares/validate');
const {
  bookingQuerySchema,
  adminCancelBookingSchema,
  createTripSchema,
  createPoojaSchema,
  createEventSchema,
  adminCancelPoojaBookingSchema,
  stayBookingListSchema,
  stayCancellationListSchema,
  stayRejectionSchema,
  stayCancellationDecisionSchema,
  updateStayUnitTypeSchema,
  updateAdminTypeSchema,
} = require('../validators/schemas');

const router = express.Router();

router.use(authenticate);

/**
 * @route POST /api/admin/events
 * @desc Create a new upcoming event
 * @access Bus Admin or Super Admin
 */
router.post(
  '/events',
  eventAdminOrSuperUser,
  eventImageUpload.single('image'),
  handleMulterError,
  validate(createEventSchema),
  adminController.createEvent
);

/**
 * @route POST /api/admin/trips
 * @desc Create a complete trip (route + stops + bus, with optional return)
 * @access Bus Admin or Super Admin
 */
router.post('/trips', busAdminOrSuperUser, validate(createTripSchema), adminController.createTrip);

/**
 * @route POST /api/admin/poojas
 * @desc Schedule a new pooja
 * @access Admin
 */
router.post('/poojas', poojaAdminOrSuperUser, validate(createPoojaSchema), adminController.createPooja);

/**
 * @route GET /api/admin/poojas
 * @desc Get upcoming poojas (admin)
 * @access Admin
 */
router.get('/poojas', poojaAdminOrSuperUser, adminController.getAdminPoojas);

/**
 * @route GET /api/admin/poojas/:id
 * @desc Get pooja by ID with enrolled list (admin)
 * @access Admin
 */
router.get('/poojas/:id', poojaAdminOrSuperUser, adminController.getAdminPoojaById);

/**
 * @route POST /api/admin/poojas/:poojaId/bookings/:bookingId/cancel
 * @desc Cancel a confirmed pooja token booking
 * @access Admin
 */
router.post(
  '/poojas/:poojaId/bookings/:bookingId/cancel',
  poojaAdminOrSuperUser,
  validate(adminCancelPoojaBookingSchema),
  adminController.cancelPoojaBooking
);

/**
 * @route GET /api/admin/buses
 * @desc Get all buses with filters
 * @access Admin
 */
router.get('/buses', busAdminOrSuperUser, adminController.getAllBuses);

/**
 * @route DELETE /api/admin/buses/:id
 * @desc Delete a future bus or cancel it if bookings exist
 * @access Admin
 */
router.delete('/buses/:id', busAdminOrSuperUser, adminController.deleteBus);

/**
 * @route GET /api/admin/bookings
 * @desc Get all bookings with filters
 * @access Admin
 */
router.get('/bookings', busAdminOrSuperUser, validate(bookingQuerySchema, 'query'), adminController.getAllBookings);

/**
 * @route PUT /api/admin/bookings/:id/status
 * @desc Approve or reject a pending booking request
 * @access Admin
 */
router.put('/bookings/:id/status', busAdminOrSuperUser, adminController.processBookingRequest);

/**
 * @route POST /api/admin/bookings/:id/cancel
 * @desc Admin direct cancellation for any active booking
 * @access Admin
 */
router.post(
  '/bookings/:id/cancel',
  busAdminOrSuperUser,
  validate(adminCancelBookingSchema),
  adminController.cancelBooking
);

// ============ STAY ADMIN ============

router.get(
  '/stay/bookings',
  stayAdminOrSuperUser,
  validate(stayBookingListSchema, 'query'),
  stayAdminController.getBookings
);
router.get('/stay/bookings/:id', stayAdminOrSuperUser, stayAdminController.getBooking);
router.post('/stay/bookings/:id/confirm', stayAdminOrSuperUser, stayAdminController.confirmBooking);
router.post(
  '/stay/bookings/:id/reject',
  stayAdminOrSuperUser,
  validate(stayRejectionSchema),
  stayAdminController.rejectBooking
);
router.get(
  '/stay/cancellation-requests',
  stayAdminOrSuperUser,
  validate(stayCancellationListSchema, 'query'),
  stayAdminController.getCancellations
);
router.post(
  '/stay/cancellation-requests/:id/decision',
  stayAdminOrSuperUser,
  validate(stayCancellationDecisionSchema),
  stayAdminController.decideCancellation
);
router.patch(
  '/stay/unit-types/:id',
  stayAdminOrSuperUser,
  validate(updateStayUnitTypeSchema),
  stayAdminController.updateUnitType
);
router.get('/admin-users', adminManagerOnly, adminManagementController.listUsers);
router.patch(
  '/admin-users/:id',
  adminManagerOnly,
  validate(updateAdminTypeSchema),
  adminManagementController.updateAdminType
);

module.exports = router;
