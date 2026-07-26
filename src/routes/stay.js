const express = require('express');
const stayController = require('../controllers/stayController');
const { authenticate } = require('../middlewares/authenticate');
const validate = require('../middlewares/validate');
const {
  stayQuoteSchema,
  createStayBookingSchema,
  stayBookingListSchema,
  stayCancellationSchema,
} = require('../validators/schemas');

const router = express.Router();

router.get('/catalog', stayController.getCatalog);
router.post('/quote', validate(stayQuoteSchema), stayController.quote);
router.post('/bookings', authenticate, validate(createStayBookingSchema), stayController.createBooking);
router.get(
  '/bookings',
  authenticate,
  validate(stayBookingListSchema, 'query'),
  stayController.getMyBookings
);
router.get('/bookings/:id', authenticate, stayController.getBooking);
router.post(
  '/bookings/:id/cancellation-requests',
  authenticate,
  validate(stayCancellationSchema),
  stayController.requestCancellation
);

module.exports = router;
