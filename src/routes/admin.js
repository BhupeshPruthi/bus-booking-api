const express = require('express');
const adminController = require('../controllers/adminController');
const stayAdminController = require('../controllers/stayAdminController');
const feedbackController = require('../controllers/feedbackController');
const chalisaController = require('../controllers/chalisaController');
const { authenticate } = require('../middlewares/authenticate');
const {
  busAdminOrSuperUser,
  poojaAdminOrSuperUser,
  eventAdminOrSuperUser,
  stayAdminOrSuperUser,
  anyAdminOrSuperUser,
  chalisaSuperAdmin,
} = require('../middlewares/authorize');
const { eventImageUpload, handleMulterError } = require('../middlewares/upload');
const validate = require('../middlewares/validate');
const {
  bookingQuerySchema,
  adminCancelBookingSchema,
  createTripSchema,
  createPoojaSchema,
  updatePoojaSchema,
  createEventSchema,
  adminCancelPoojaBookingSchema,
  stayBookingListSchema,
  stayBookingIdParamsSchema,
  stayDailyOccupancySchema,
  stayOccupancyDateParamsSchema,
  stayCancellationListSchema,
  stayRejectionSchema,
  stayCancellationDecisionSchema,
  adminDirectStayCancellationSchema,
  createStayBookingSchema,
  createStayCouponSchema,
  feedbackListSchema,
  chalisaMarathonParamsSchema,
  chalisaScheduleMarathonSchema,
  chalisaUpdateMarathonSchema,
  chalisaEndMarathonSchema,
  chalisaAdminReadingSchema,
} = require('../validators/schemas');

const router = express.Router();

router.use(authenticate);

/**
 * @route POST /api/admin/chalisa/marathons
 * @desc Schedule the next Hanuman Chalisa marathon (shows on everyone's Home page at once)
 * @access Super Admin
 */
router.post(
  '/chalisa/marathons',
  chalisaSuperAdmin,
  validate(chalisaScheduleMarathonSchema),
  chalisaController.scheduleMarathon
);

/**
 * @route PATCH /api/admin/chalisa/marathons/:id
 * @desc Edit the name or goal; the start time only before it starts
 * @access Super Admin
 */
router.patch(
  '/chalisa/marathons/:id',
  chalisaSuperAdmin,
  validate(chalisaMarathonParamsSchema, 'params'),
  validate(chalisaUpdateMarathonSchema),
  chalisaController.updateMarathon
);

/**
 * @route DELETE /api/admin/chalisa/marathons/:id
 * @desc Cancel a marathon that hasn't started
 * @access Super Admin
 */
router.delete(
  '/chalisa/marathons/:id',
  chalisaSuperAdmin,
  validate(chalisaMarathonParamsSchema, 'params'),
  chalisaController.cancelMarathon
);

/**
 * @route POST /api/admin/chalisa/readings
 * @desc Add rounds to the admin's own entry in the running Path (undo via DELETE /api/chalisa/readings/:clientRequestId)
 * @access Chalisa admin
 */
router.post(
  '/chalisa/readings',
  chalisaSuperAdmin,
  validate(chalisaAdminReadingSchema),
  chalisaController.addAdminRounds
);

/**
 * @route POST /api/admin/chalisa/marathons/:id/end
 * @desc End the running marathon and archive it with its final totals
 * @access Super Admin
 */
router.post(
  '/chalisa/marathons/:id/end',
  chalisaSuperAdmin,
  validate(chalisaMarathonParamsSchema, 'params'),
  validate(chalisaEndMarathonSchema),
  chalisaController.endMarathon
);

/**
 * @route POST /api/admin/chalisa/marathons/:id/restore
 * @desc Undo ending a marathon, within 10 minutes
 * @access Super Admin
 */
router.post(
  '/chalisa/marathons/:id/restore',
  chalisaSuperAdmin,
  validate(chalisaMarathonParamsSchema, 'params'),
  chalisaController.restoreMarathon
);

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
 * @route PUT /api/admin/poojas/:id
 * @desc Update an active pooja schedule
 * @access Admin
 */
router.put(
  '/poojas/:id',
  poojaAdminOrSuperUser,
  validate(updatePoojaSchema),
  adminController.updatePooja
);

/**
 * @route DELETE /api/admin/poojas/:id
 * @desc Delete an unbooked pooja or cancel it and its bookings while preserving history
 * @access Admin
 */
router.delete('/poojas/:id', poojaAdminOrSuperUser, adminController.deletePooja);

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

/**
 * @route GET /api/admin/feedback
 * @desc List feedback with submitter contact details
 * @access Any Admin
 */
router.get(
  '/feedback',
  anyAdminOrSuperUser,
  validate(feedbackListSchema, 'query'),
  feedbackController.list
);

// ============ STAY ADMIN ============

router.get('/stay/coupons', stayAdminOrSuperUser, stayAdminController.getCoupons);
router.post(
  '/stay/coupons',
  stayAdminOrSuperUser,
  validate(createStayCouponSchema),
  stayAdminController.createCoupon
);
router.post(
  '/stay/coupons/:id/deactivate',
  stayAdminOrSuperUser,
  stayAdminController.deactivateCoupon
);

router.get(
  '/stay/daily-occupancy',
  stayAdminOrSuperUser,
  validate(stayDailyOccupancySchema, 'query'),
  stayAdminController.getDailyOccupancy
);

router.get(
  '/stay/daily-occupancy/:date/bookings',
  stayAdminOrSuperUser,
  validate(stayOccupancyDateParamsSchema, 'params'),
  stayAdminController.getDailyOccupancyBookings
);

router.get(
  '/stay/bookings',
  stayAdminOrSuperUser,
  validate(stayBookingListSchema, 'query'),
  stayAdminController.getBookings
);
router.post(
  '/stay/bookings',
  stayAdminOrSuperUser,
  validate(createStayBookingSchema),
  stayAdminController.createAdminBooking
);
router.get(
  '/stay/bookings/:id',
  stayAdminOrSuperUser,
  validate(stayBookingIdParamsSchema, 'params'),
  stayAdminController.getBooking
);
router.post(
  '/stay/bookings/:id/cancel',
  stayAdminOrSuperUser,
  validate(stayBookingIdParamsSchema, 'params'),
  validate(adminDirectStayCancellationSchema),
  stayAdminController.cancelAdminBooking
);
router.post(
  '/stay/bookings/:id/confirm',
  stayAdminOrSuperUser,
  validate(stayBookingIdParamsSchema, 'params'),
  stayAdminController.confirmBooking
);
router.post(
  '/stay/bookings/:id/reject',
  stayAdminOrSuperUser,
  validate(stayBookingIdParamsSchema, 'params'),
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
module.exports = router;
