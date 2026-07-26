const stayService = require('../services/stayService');
const asyncHandler = require('../utils/asyncHandler');

const getCatalog = asyncHandler(async (req, res) => {
  res.json({ success: true, data: await stayService.getCatalog() });
});

const quote = asyncHandler(async (req, res) => {
  res.json({ success: true, data: await stayService.quote(req.body) });
});

const createBooking = asyncHandler(async (req, res) => {
  const result = await stayService.createCustomerBooking(req.user.id, req.body);
  res.status(201).json({ success: true, data: result });
});

const getMyBookings = asyncHandler(async (req, res) => {
  res.json({ success: true, data: await stayService.getUserBookings(req.user.id, req.query) });
});

const getBooking = asyncHandler(async (req, res) => {
  res.json({ success: true, data: await stayService.getBookingById(req.params.id, req.user.id) });
});

const requestCancellation = asyncHandler(async (req, res) => {
  const result = await stayService.requestCancellation(
    req.params.id,
    req.user.id,
    req.body.reason || null
  );
  res.status(201).json({ success: true, data: result });
});

module.exports = {
  getCatalog,
  quote,
  createBooking,
  getMyBookings,
  getBooking,
  requestCancellation,
};
