const busService = require('../services/busService');
const bookingService = require('../services/bookingService');
const tripService = require('../services/tripService');
const poojaService = require('../services/poojaService');
const eventService = require('../services/eventService');
const asyncHandler = require('../utils/asyncHandler');

const getAllBuses = asyncHandler(async (req, res) => {
  const result = await busService.getAllBuses(req.query);
  res.json({ success: true, data: result });
});

const getAllBookings = asyncHandler(async (req, res) => {
  const result = await bookingService.getAllBookings(req.query);
  res.json({ success: true, data: result });
});

const processBookingRequest = asyncHandler(async (req, res) => {
  const { action, rejectionReason } = req.body;
  const result = await bookingService.processBookingRequest(
    req.params.id,
    req.user.id,
    action,
    rejectionReason
  );
  res.json({ success: true, data: result });
});

const cancelBooking = asyncHandler(async (req, res) => {
  const result = await bookingService.adminCancelBooking(
    req.params.id,
    req.user.id,
    req.body.reason || null
  );
  res.json({ success: true, data: result });
});

const createTrip = asyncHandler(async (req, res) => {
  const result = await tripService.createTrip(req.body);
  res.status(201).json({ success: true, data: result });
});

const createPooja = asyncHandler(async (req, res) => {
  const result = await poojaService.createPooja(req.user.id, req.body);
  res.status(201).json({ success: true, data: result });
});

const getAdminPoojas = asyncHandler(async (req, res) => {
  const result = await poojaService.getAdminUpcomingPoojas();
  res.json({ success: true, data: result });
});

const getAdminPoojaById = asyncHandler(async (req, res) => {
  const result = await poojaService.getAdminPoojaById(req.params.id);
  res.json({ success: true, data: result });
});

const createEvent = asyncHandler(async (req, res) => {
  const result = await eventService.createEvent(req.user.id, req.body);
  res.status(201).json({ success: true, data: result });
});

module.exports = {
  getAllBuses,
  getAllBookings,
  processBookingRequest,
  cancelBooking,
  createTrip,
  createPooja,
  getAdminPoojas,
  getAdminPoojaById,
  createEvent,
};
