const busService = require('../services/busService');
const bookingService = require('../services/bookingService');
const poojaService = require('../services/poojaService');
const eventService = require('../services/eventService');
const asyncHandler = require('../utils/asyncHandler');

const searchBuses = asyncHandler(async (req, res) => {
  const result = await busService.searchBuses(req.query);
  res.json({ success: true, data: result });
});

const getBusDetails = asyncHandler(async (req, res) => {
  const result = await busService.getBusById(req.params.id);
  res.json({ success: true, data: result });
});

const getUpcomingPoojas = asyncHandler(async (req, res) => {
  const result = await poojaService.getUpcomingPoojas();
  res.json({ success: true, data: result });
});

const getPoojaDetails = asyncHandler(async (req, res) => {
  const result = await poojaService.getPoojaById(req.params.id);
  res.json({ success: true, data: result });
});

const bookPoojaToken = asyncHandler(async (req, res) => {
  const result = await poojaService.bookToken(req.params.id, req.user.id, req.body);
  res.status(201).json({ success: true, data: result });
});

const getUpcomingEvents = asyncHandler(async (req, res) => {
  const result = await eventService.getUpcomingEvents();
  res.json({ success: true, data: result });
});

const createBooking = asyncHandler(async (req, res) => {
  const result = await bookingService.createBooking(req.user.id, req.body);
  res.status(201).json({ success: true, data: result });
});

const getMyBookings = asyncHandler(async (req, res) => {
  const result = await bookingService.getUserBookings(req.user.id, req.query);
  res.json({ success: true, data: result });
});

const getBookingById = asyncHandler(async (req, res) => {
  const result = await bookingService.getBookingById(req.params.id, req.user.id);
  res.json({ success: true, data: result });
});

const requestBookingCancellation = asyncHandler(async (req, res) => {
  const result = await bookingService.requestCancellation(
    req.params.id,
    req.user.id,
    req.body.reason || null
  );
  res.json({ success: true, data: result });
});

module.exports = {
  searchBuses,
  getBusDetails,
  getUpcomingPoojas,
  getPoojaDetails,
  bookPoojaToken,
  getUpcomingEvents,
  createBooking,
  getMyBookings,
  getBookingById,
  requestBookingCancellation,
};
