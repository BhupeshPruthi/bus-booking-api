const { db } = require('../config/database');
const { NotFoundError, ValidationError, ConflictError, ForbiddenError } = require('../utils/errors');

const ACTIVE_SEAT_STATUSES = ['pending', 'payment_uploaded', 'confirmed'];

function parseAssignedSeats(value) {
  if (!value) return [];
  return String(value)
    .split(',')
    .map((seat) => parseInt(seat.trim(), 10))
    .filter((seat) => Number.isInteger(seat));
}

function reserveFirstAvailableSeats(occupiedSeats, count, seatStart, totalSeats) {
  const seats = [];
  for (let seat = seatStart; seat <= totalSeats && seats.length < count; seat += 1) {
    if (!occupiedSeats.has(seat)) {
      occupiedSeats.add(seat);
      seats.push(seat);
    }
  }
  return seats;
}

function collectOccupiedSeats(bookings, seatStart, totalSeats) {
  const occupiedSeats = new Set();

  bookings.forEach((booking) => {
    const assignedSeats = parseAssignedSeats(booking.assigned_seats)
      .filter((seat) => seat >= seatStart && seat <= totalSeats);

    if (assignedSeats.length > 0) {
      assignedSeats.forEach((seat) => occupiedSeats.add(seat));
      return;
    }

    // Compatibility for older rows that may not have assigned_seats populated.
    reserveFirstAvailableSeats(
      occupiedSeats,
      parseInt(booking.seat_count || 0, 10),
      seatStart,
      totalSeats
    );
  });

  return occupiedSeats;
}

class BookingService {
  /**
   * Create a new booking
   */
  async createBooking(userId, data) {
    const bookingId = await db.transaction(async (trx) => {
      // Lock the bus row so concurrent bookings cannot choose the same free seats.
      const bus = await trx('buses').where('id', data.busId).forUpdate().first();
      if (!bus) {
        throw new NotFoundError('Bus');
      }

      // Check if bus is available for booking
      if (bus.status !== 'scheduled') {
        throw new ValidationError('This bus is not available for booking');
      }

      if (new Date(bus.departure_time) <= new Date()) {
        throw new ValidationError('Cannot book a bus that has already departed');
      }

      // Verify pickup point belongs to this specific bus
      const pickupPoint = await trx('pickup_points')
        .where('id', data.pickupPointId)
        .where('bus_id', data.busId)
        .first();

      if (!pickupPoint) {
        throw new ValidationError('Invalid pickup point for this bus');
      }

      const seatStart = bus.seat_start_number || 1;
      const activeBookings = await trx('bookings')
        .where('bus_id', data.busId)
        .whereIn('status', ACTIVE_SEAT_STATUSES)
        .orderBy('created_at', 'asc')
        .select('assigned_seats', 'seat_count');

      const occupiedSeats = collectOccupiedSeats(activeBookings, seatStart, bus.total_seats);
      const availableSeats = Math.max(0, bus.total_seats - seatStart + 1 - occupiedSeats.size);
      if (data.seatCount > availableSeats) {
        throw new ConflictError(`Only ${availableSeats} seats available. Requested: ${data.seatCount}`);
      }

      const seatNumbers = reserveFirstAvailableSeats(
        occupiedSeats,
        data.seatCount,
        seatStart,
        bus.total_seats
      );

      if (seatNumbers.length < data.seatCount) {
        throw new ConflictError(`Only ${availableSeats} seats available. Requested: ${data.seatCount}`);
      }

      // Calculate total amount
      const totalAmount = parseFloat(bus.price) * data.seatCount;

      // Bookings stay pending until admin acts — set far-future hold
      const holdExpiresAt = new Date('2099-12-31T23:59:59Z');

      // Create booking
      const [booking] = await trx('bookings')
        .insert({
          user_id: userId,
          bus_id: data.busId,
          pickup_point_id: data.pickupPointId,
          seat_count: data.seatCount,
          passenger_name: data.passengerName || null,
          passenger_phone: data.passengerPhone || null,
          passenger_names: data.passengerNames || null,
          total_amount: totalAmount,
          assigned_seats: seatNumbers.join(','),
          status: 'pending',
          hold_expires_at: holdExpiresAt,
        })
        .returning('*');

      return booking.id;
    });

    return this.getBookingById(bookingId, userId);
  }

  /**
   * Get booking by ID
   */
  async getBookingById(bookingId, userId = null) {
    let query = db('bookings')
      .join('buses', 'bookings.bus_id', 'buses.id')
      .join('routes', 'buses.route_id', 'routes.id')
      .join('pickup_points', 'bookings.pickup_point_id', 'pickup_points.id')
      .join('users', 'bookings.user_id', 'users.id')
      .select(
        'bookings.*',
        'buses.bus_name',
        'buses.departure_time',
        'buses.arrival_time',
        'buses.trip_type',
        'buses.price as unit_price',
        'routes.name as route_name',
        'routes.source',
        'routes.destination',
        'pickup_points.name as pickup_point_name',
        'pickup_points.address as pickup_point_address',
        'users.mobile as user_mobile',
        'users.name as user_name'
      )
      .where('bookings.id', bookingId);

    if (userId) {
      query = query.where('bookings.user_id', userId);
    }

    const booking = await query.first();

    if (!booking) {
      throw new NotFoundError('Booking');
    }

    // Get payment if exists
    const payment = await db('payments').where('booking_id', bookingId).first();

    return this.formatBooking(booking, payment);
  }

  /**
   * Get user's bookings
   */
  async getUserBookings(userId, filters = {}) {
    let query = db('bookings')
      .join('buses', 'bookings.bus_id', 'buses.id')
      .join('routes', 'buses.route_id', 'routes.id')
      .join('pickup_points', 'bookings.pickup_point_id', 'pickup_points.id')
      .select(
        'bookings.*',
        'buses.bus_name',
        'buses.departure_time',
        'buses.arrival_time',
        'buses.trip_type',
        'routes.name as route_name',
        'routes.source',
        'routes.destination',
        'pickup_points.name as pickup_point_name'
      )
      .where('bookings.user_id', userId);

    if (filters.status) {
      query = query.where('bookings.status', filters.status);
    }

    query = query.orderBy('bookings.created_at', 'desc');

    // Pagination
    const page = filters.page || 1;
    const limit = filters.limit || 20;
    query = query.offset((page - 1) * limit).limit(limit);

    const bookings = await query;

    // Get payments
    const bookingIds = bookings.map((b) => b.id);
    const payments = await db('payments').whereIn('booking_id', bookingIds);
    const paymentsMap = payments.reduce((acc, p) => {
      acc[p.booking_id] = p;
      return acc;
    }, {});

    return bookings.map((booking) => this.formatBooking(booking, paymentsMap[booking.id]));
  }

  /**
   * Get all bookings (admin)
   */
  async getAllBookings(filters = {}) {
    let query = db('bookings')
      .join('buses', 'bookings.bus_id', 'buses.id')
      .join('routes', 'buses.route_id', 'routes.id')
      .join('pickup_points', 'bookings.pickup_point_id', 'pickup_points.id')
      .join('users', 'bookings.user_id', 'users.id')
      .select(
        'bookings.*',
        'buses.bus_name',
        'buses.departure_time',
        'buses.arrival_time',
        'buses.trip_type',
        'routes.name as route_name',
        'routes.source',
        'routes.destination',
        'pickup_points.name as pickup_point_name',
        'users.mobile as user_mobile',
        'users.name as user_name'
      );

    if (filters.status) {
      query = query.where('bookings.status', filters.status);
    }

    if (filters.busId) {
      query = query.where('bookings.bus_id', filters.busId);
    }

    if (filters.userId) {
      query = query.where('bookings.user_id', filters.userId);
    }

    if (filters.fromDate) {
      query = query.where('buses.departure_time', '>=', filters.fromDate);
    }

    if (filters.toDate) {
      query = query.where('buses.departure_time', '<=', filters.toDate);
    }

    query = query.orderBy('bookings.created_at', 'desc');

    // Pagination
    const page = filters.page || 1;
    const limit = filters.limit || 20;
    query = query.offset((page - 1) * limit).limit(limit);

    const bookings = await query;

    // Get payments
    const bookingIds = bookings.map((b) => b.id);
    const payments = await db('payments').whereIn('booking_id', bookingIds);
    const paymentsMap = payments.reduce((acc, p) => {
      acc[p.booking_id] = p;
      return acc;
    }, {});

    return bookings.map((booking) => this.formatBooking(booking, paymentsMap[booking.id]));
  }

  /**
   * Upload payment screenshot
   */
  async uploadPayment(bookingId, userId, screenshotPath) {
    // Get booking
    const booking = await db('bookings')
      .where('id', bookingId)
      .where('user_id', userId)
      .first();

    if (!booking) {
      throw new NotFoundError('Booking');
    }

    if (booking.status !== 'pending') {
      throw new ValidationError(`Cannot upload payment for booking with status: ${booking.status}`);
    }

    // Check if hold has expired
    if (new Date(booking.hold_expires_at) < new Date()) {
      await db('bookings').where('id', bookingId).update({ status: 'expired', updated_at: new Date() });
      throw new ValidationError('Booking hold has expired. Please create a new booking.');
    }

    // Create or update payment record
    const existingPayment = await db('payments').where('booking_id', bookingId).first();

    if (existingPayment) {
      await db('payments')
        .where('id', existingPayment.id)
        .update({
          screenshot_url: screenshotPath,
          status: 'pending_review',
          uploaded_at: new Date(),
        });
    } else {
      await db('payments').insert({
        booking_id: bookingId,
        screenshot_url: screenshotPath,
        status: 'pending_review',
      });
    }

    // Update booking status
    await db('bookings')
      .where('id', bookingId)
      .update({ status: 'payment_uploaded', updated_at: new Date() });

    return this.getBookingById(bookingId, userId);
  }

  /**
   * Admin: Approve/Reject payment
   */
  async processPayment(bookingId, adminId, action, rejectionReason = null) {
    const booking = await db('bookings').where('id', bookingId).first();

    if (!booking) {
      throw new NotFoundError('Booking');
    }

    if (booking.status !== 'payment_uploaded') {
      throw new ValidationError(`Cannot process payment for booking with status: ${booking.status}`);
    }

    const payment = await db('payments').where('booking_id', bookingId).first();

    if (!payment) {
      throw new ValidationError('No payment found for this booking');
    }

    if (action === 'approve') {
      await db('payments').where('id', payment.id).update({
        status: 'approved',
        reviewed_by: adminId,
        reviewed_at: new Date(),
      });

      await db('bookings')
        .where('id', bookingId)
        .update({ status: 'confirmed', updated_at: new Date() });
    } else if (action === 'reject') {
      await db('payments').where('id', payment.id).update({
        status: 'rejected',
        reviewed_by: adminId,
        rejection_reason: rejectionReason,
        reviewed_at: new Date(),
      });

      await db('bookings')
        .where('id', bookingId)
        .update({ status: 'rejected', updated_at: new Date() });
    }

    return this.getBookingById(bookingId);
  }

  /**
   * Admin: Approve or reject a pending booking request
   */
  async processBookingRequest(bookingId, adminId, action, rejectionReason = null) {
    const booking = await db('bookings').where('id', bookingId).first();

    if (!booking) {
      throw new NotFoundError('Booking');
    }

    const actionableStatuses = ['pending', 'payment_uploaded'];
    if (!actionableStatuses.includes(booking.status)) {
      throw new ValidationError(`Cannot ${action} booking with status: ${booking.status}`);
    }

    if (action === 'approve') {
      await db('bookings')
        .where('id', bookingId)
        .update({ status: 'confirmed', updated_at: new Date() });
    } else if (action === 'reject') {
      await db('bookings')
        .where('id', bookingId)
        .update({ status: 'rejected', updated_at: new Date() });
    } else {
      throw new ValidationError('Invalid action. Must be "approve" or "reject".');
    }

    return this.getBookingById(bookingId);
  }

  /**
   * Cancel booking
   */
  async cancelBooking(bookingId, userId, isAdmin = false) {
    let query = db('bookings').where('id', bookingId);

    if (!isAdmin) {
      query = query.where('user_id', userId);
    }

    const booking = await query.first();

    if (!booking) {
      throw new NotFoundError('Booking');
    }

    // Check if booking can be cancelled
    const cancellableStatuses = ['pending', 'payment_uploaded'];
    if (!isAdmin && !cancellableStatuses.includes(booking.status)) {
      throw new ValidationError(`Cannot cancel booking with status: ${booking.status}`);
    }

    // Only admin can cancel confirmed bookings
    if (booking.status === 'confirmed' && !isAdmin) {
      throw new ForbiddenError('Only admin can cancel confirmed bookings');
    }

    await db('bookings')
      .where('id', bookingId)
      .update({ status: 'cancelled', updated_at: new Date() });

    return { message: 'Booking cancelled successfully' };
  }

  /**
   * Expire pending bookings (called by cron job)
   */
  async expirePendingBookings() {
    const result = await db('bookings')
      .where('status', 'pending')
      .where('hold_expires_at', '<', new Date())
      .update({ status: 'expired', updated_at: new Date() });

    return result;
  }

  // ============ FORMATTERS ============

  formatBooking(booking, payment = null) {
    return {
      id: booking.id,
      userId: booking.user_id,
      busId: booking.bus_id,
      seatCount: booking.seat_count,
      assignedSeats: booking.assigned_seats || null,
      passengerName: booking.passenger_name,
      passengerPhone: booking.passenger_phone,
      passengerNames: booking.passenger_names,
      totalAmount: parseFloat(booking.total_amount),
      status: booking.status,
      holdExpiresAt: booking.hold_expires_at,
      createdAt: booking.created_at,
      bus: {
        name: booking.bus_name,
        departureTime: booking.departure_time,
        arrivalTime: booking.arrival_time,
        tripType: booking.trip_type,
      },
      route: {
        name: booking.route_name,
        source: booking.source,
        destination: booking.destination,
      },
      pickupPoint: {
        name: booking.pickup_point_name,
        address: booking.pickup_point_address,
      },
      user: booking.user_mobile
        ? {
            mobile: booking.user_mobile,
            name: booking.user_name,
          }
        : undefined,
      payment: payment
        ? {
            screenshotUrl: payment.screenshot_url,
            status: payment.status,
            rejectionReason: payment.rejection_reason,
            uploadedAt: payment.uploaded_at,
            reviewedAt: payment.reviewed_at,
          }
        : null,
    };
  }
}

module.exports = new BookingService();
