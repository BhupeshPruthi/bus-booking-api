const Joi = require('joi');

// Common schemas
const uuidSchema = Joi.string().uuid();
// Pagination schema
const paginationSchema = Joi.object({
  page: Joi.number().integer().min(1).default(1),
  limit: Joi.number().integer().min(1).max(100).default(20),
  sortBy: Joi.string(),
  sortOrder: Joi.string().valid('asc', 'desc').default('desc'),
});

// ============ AUTH SCHEMAS ============

const googleSignInSchema = Joi.object({
  idToken: Joi.string().required().min(100).messages({
    'string.min': 'Invalid Google credentials',
  }),
});

const refreshTokenSchema = Joi.object({
  refreshToken: Joi.string().required(),
});

// ============ PROFILE SCHEMAS ============

const updateProfileSchema = Joi.object({
  name: Joi.string().max(100).trim(),
  email: Joi.string().email().lowercase().trim(),
}).min(1);

// ============ ROUTE SCHEMAS ============

const createRouteSchema = Joi.object({
  name: Joi.string().max(200).required().trim(),
  source: Joi.string().max(100).required().trim(),
  destination: Joi.string().max(100).required().trim(),
  isActive: Joi.boolean().default(true),
});

const updateRouteSchema = Joi.object({
  name: Joi.string().max(200).trim(),
  source: Joi.string().max(100).trim(),
  destination: Joi.string().max(100).trim(),
  isActive: Joi.boolean(),
}).min(1);

// ============ PICKUP POINT SCHEMAS ============

const createPickupPointSchema = Joi.object({
  name: Joi.string().max(200).required().trim(),
  address: Joi.string().max(500).trim(),
  sequence: Joi.number().integer().min(1).required(),
});

const updatePickupPointSchema = Joi.object({
  name: Joi.string().max(200).trim(),
  address: Joi.string().max(500).trim(),
  sequence: Joi.number().integer().min(1),
}).min(1);

// ============ BUS SCHEMAS ============

const createBusSchema = Joi.object({
  routeId: uuidSchema.required(),
  busName: Joi.string().max(200).required().trim(),
  totalSeats: Joi.number().integer().min(1).max(100).required(),
  price: Joi.number().positive().precision(2).required(),
  departureTime: Joi.date().iso().greater('now').required(),
  bookingHoldMinutes: Joi.number().integer().min(30).max(1440).default(180), // 30 min to 24 hours
});

const updateBusSchema = Joi.object({
  busName: Joi.string().max(200).trim(),
  totalSeats: Joi.number().integer().min(1).max(100),
  price: Joi.number().positive().precision(2),
  departureTime: Joi.date().iso(),
  bookingHoldMinutes: Joi.number().integer().min(30).max(1440),
  status: Joi.string().valid('scheduled', 'departed', 'cancelled'),
}).min(1);

const searchBusesSchema = Joi.object({
  routeId: uuidSchema,
  date: Joi.date().iso(),
  source: Joi.string().max(100),
  destination: Joi.string().max(100),
}).concat(paginationSchema);

// ============ BOOKING SCHEMAS ============

const createBookingSchema = Joi.object({
  busId: uuidSchema.required(),
  pickupPointId: uuidSchema.required(),
  seatCount: Joi.number().integer().min(1).max(10).required(),
  passengerName: Joi.string().max(200).trim().required(),
  passengerPhone: Joi.string()
    .pattern(/^[0-9]{10,15}$/)
    .required(),
  passengerNames: Joi.array().items(Joi.string().max(100).trim()).min(1),
});

const bookingQuerySchema = Joi.object({
  status: Joi.string().valid(
    'pending',
    'payment_uploaded',
    'confirmed',
    'cancellation_requested',
    'rejected',
    'expired',
    'cancelled'
  ),
  busId: uuidSchema,
  userId: uuidSchema,
  fromDate: Joi.date().iso(),
  toDate: Joi.date().iso(),
}).concat(paginationSchema);

const cancellationRequestSchema = Joi.object({
  reason: Joi.string().max(500).allow('', null).trim(),
});

const adminCancelBookingSchema = Joi.object({
  reason: Joi.string().max(500).allow('', null).trim(),
});

// ============ TRIP SCHEMAS ============

const stopSchema = Joi.object({
  name: Joi.string().max(200).required().trim(),
  address: Joi.string().max(500).allow('', null).trim(),
});

const createTripSchema = Joi.object({
  source: Joi.string().max(100).required().trim(),
  destination: Joi.string().max(100).required().trim(),
  departureTime: Joi.date().iso().greater('now').required(),
  arrivalTime: Joi.date().iso().greater(Joi.ref('departureTime')).required().messages({
    'date.greater': 'Arrival time must be after departure time',
  }),
  totalSeats: Joi.number().integer().min(1).max(100).required(),
  seatStartNumber: Joi.number().integer().min(1).default(1),
  price: Joi.number().positive().precision(2).required(),
  contactName: Joi.string().max(200).required().trim(),
  contactPhone: Joi.string().max(20).required().trim(),
  tripType: Joi.string().valid('one_way', 'round_trip').required(),
  stops: Joi.array().items(stopSchema).min(0).default([]),
  returnDepartureTime: Joi.when('tripType', {
    is: 'round_trip',
    then: Joi.date()
      .iso()
      .min(Joi.ref('departureTime'))
      .optional()
      .messages({
        'date.min': 'Return departure cannot be before trip departure',
      }),
    otherwise: Joi.forbidden(),
  }),
  returnArrivalTime: Joi.when('tripType', {
    is: 'round_trip',
    then: Joi.date().iso().min(Joi.ref('departureTime')).optional().messages({
      'date.min': 'Return arrival cannot be before trip departure',
    }),
    otherwise: Joi.forbidden(),
  }),
});

// ============ POOJA SCHEMAS ============

const createPoojaSchema = Joi.object({
  scheduledAt: Joi.date().iso().greater('now').required(),
  place: Joi.string().max(200).required().trim(),
  totalTokens: Joi.number().integer().min(1).max(10000).default(50),
});

const bookPoojaTokenSchema = Joi.object({
  name: Joi.string().max(200).trim().required(),
  phone: Joi.string()
    .pattern(/^[0-9]{10,15}$/)
    .required(),
});

// ============ EVENTS SCHEMAS ============

const createEventSchema = Joi.object({
  header: Joi.string().max(200).required().trim(),
  subHeader: Joi.string().max(500).required().trim(),
  eventDate: Joi.date().iso().greater('now').required(),
});

// ============ PAYMENT SCHEMAS ============

const paymentActionSchema = Joi.object({
  action: Joi.string().valid('approve', 'reject').required(),
  rejectionReason: Joi.string().max(500).when('action', {
    is: 'reject',
    then: Joi.required(),
    otherwise: Joi.optional(),
  }),
});

module.exports = {
  // Common
  uuidSchema,
  paginationSchema,

  // Auth
  googleSignInSchema,
  refreshTokenSchema,

  // Profile
  updateProfileSchema,

  // Routes
  createRouteSchema,
  updateRouteSchema,

  // Pickup Points
  createPickupPointSchema,
  updatePickupPointSchema,

  // Buses
  createBusSchema,
  updateBusSchema,
  searchBusesSchema,

  // Bookings
  createBookingSchema,
  bookingQuerySchema,
  cancellationRequestSchema,
  adminCancelBookingSchema,

  // Trips
  createTripSchema,

  // Pooja
  createPoojaSchema,
  bookPoojaTokenSchema,

  // Events
  createEventSchema,

  // Payments
  paymentActionSchema,
};
