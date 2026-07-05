const busService = require('../services/busService');
const bookingService = require('../services/bookingService');
const poojaService = require('../services/poojaService');
const eventService = require('../services/eventService');
const eventImageStorageService = require('../services/eventImageStorageService');
const asyncHandler = require('../utils/asyncHandler');
const { getApiBaseUrl } = require('../utils/requestBaseUrl');

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

const getMyPoojaBookings = asyncHandler(async (req, res) => {
  const result = await poojaService.getUserPoojaBookings(req.user.id);
  res.json({ success: true, data: result });
});

const bookPoojaToken = asyncHandler(async (req, res) => {
  const result = await poojaService.bookToken(req.params.id, req.user.id, req.body);
  res.status(201).json({ success: true, data: result });
});

const getUpcomingEvents = asyncHandler(async (req, res) => {
  const result = await eventService.getUpcomingEvents({ imageBaseUrl: getApiBaseUrl(req) });
  res.json({ success: true, data: result });
});

const getEventImage = async (req, res, next) => {
  try {
    const image = await eventImageStorageService.getEventImage(req.params.imageName);
    res.set({
      'Content-Type': image.contentType,
      'Cache-Control': image.cacheControl,
    });

    if (image.body && typeof image.body.pipe === 'function') {
      image.body.on('error', next);
      image.body.pipe(res);
      return;
    }

    const chunks = [];
    for await (const chunk of image.body) {
      chunks.push(Buffer.from(chunk));
    }
    res.send(Buffer.concat(chunks));
  } catch (error) {
    next(error);
  }
};

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
  getMyPoojaBookings,
  bookPoojaToken,
  getUpcomingEvents,
  getEventImage,
  createBooking,
  getMyBookings,
  getBookingById,
  requestBookingCancellation,
};
