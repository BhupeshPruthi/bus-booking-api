const stayService = require('../services/stayService');
const asyncHandler = require('../utils/asyncHandler');

const getBookings = asyncHandler(async (req, res) => {
  res.json({ success: true, data: await stayService.getAdminBookings(req.query) });
});
const getBooking = asyncHandler(async (req, res) => {
  res.json({ success: true, data: await stayService.getBookingById(req.params.id) });
});
const confirmBooking = asyncHandler(async (req, res) => {
  res.json({ success: true, data: await stayService.confirmBooking(req.params.id, req.user.id) });
});
const rejectBooking = asyncHandler(async (req, res) => {
  res.json({
    success: true,
    data: await stayService.rejectBooking(req.params.id, req.user.id, req.body.reason),
  });
});
const getCancellations = asyncHandler(async (req, res) => {
  res.json({ success: true, data: await stayService.getAdminCancellations(req.query) });
});
const decideCancellation = asyncHandler(async (req, res) => {
  res.json({
    success: true,
    data: await stayService.decideCancellation(req.params.id, req.user.id, req.body),
  });
});
module.exports = {
  getBookings,
  getBooking,
  confirmBooking,
  rejectBooking,
  getCancellations,
  decideCancellation,
};
