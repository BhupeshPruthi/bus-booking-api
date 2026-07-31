const stayService = require('../services/stayService');
const stayCouponService = require('../services/stayCouponService');
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
const getCoupons = asyncHandler(async (req, res) => {
  res.json({ success: true, data: await stayCouponService.list() });
});
const createCoupon = asyncHandler(async (req, res) => {
  const coupon = await stayCouponService.create(req.user.id, req.body);
  res.status(201).json({ success: true, data: coupon });
});
const deactivateCoupon = asyncHandler(async (req, res) => {
  res.json({ success: true, data: await stayCouponService.deactivate(req.params.id) });
});
module.exports = {
  getBookings,
  getBooking,
  confirmBooking,
  rejectBooking,
  getCancellations,
  decideCancellation,
  getCoupons,
  createCoupon,
  deactivateCoupon,
};
