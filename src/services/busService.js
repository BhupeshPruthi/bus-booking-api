const { db } = require('../config/database');
const { NotFoundError, ValidationError } = require('../utils/errors');

const ACTIVE_SEAT_STATUSES = ['pending', 'payment_uploaded', 'confirmed', 'cancellation_requested'];

/** Max passengers that can be booked for this bus (seats seat_start..total_seats). */
function bookableSeatCapacity(bus) {
  const start = bus.seat_start_number || 1;
  return Math.max(0, bus.total_seats - start + 1);
}

class BusService {
  /**
   * Create a new bus trip
   */
  async createBus(data) {
    // Verify route exists
    const route = await db('routes').where('id', data.routeId).first();
    if (!route) {
      throw new NotFoundError('Route');
    }

    const [bus] = await db('buses')
      .insert({
        route_id: data.routeId,
        bus_name: data.busName,
        total_seats: data.totalSeats,
        price: data.price,
        departure_time: data.departureTime,
        booking_hold_minutes: data.bookingHoldMinutes || 180,
        status: 'scheduled',
      })
      .returning('*');

    return this.formatBus(bus);
  }

  /**
   * Get all buses (admin) with filters
   */
  async getAllBuses(filters = {}) {
    let query = db('buses')
      .join('routes', 'buses.route_id', 'routes.id')
      .select(
        'buses.*',
        'routes.name as route_name',
        'routes.source',
        'routes.destination'
      );

    if (filters.routeId) {
      query = query.where('buses.route_id', filters.routeId);
    }

    if (filters.status) {
      query = query.where('buses.status', filters.status);
    }

    if (filters.date) {
      const startOfDay = new Date(filters.date);
      startOfDay.setHours(0, 0, 0, 0);
      const endOfDay = new Date(filters.date);
      endOfDay.setHours(23, 59, 59, 999);

      query = query.whereBetween('buses.departure_time', [startOfDay, endOfDay]);
    }

    query = query.orderBy('buses.departure_time', 'asc');

    // Pagination
    const page = filters.page || 1;
    const limit = filters.limit || 20;
    query = query.offset((page - 1) * limit).limit(limit);

    const buses = await query;

    // Get booked seats count for each bus
    const busIds = buses.map((b) => b.id);
    const bookedSeats = await this.getBookedSeatsCount(busIds);

    const formatted = buses.map((bus) => ({
      ...this.formatBus(bus),
      route: {
        name: bus.route_name,
        source: bus.source,
        destination: bus.destination,
      },
      bookedSeats: bookedSeats[bus.id] || 0,
      availableSeats: Math.max(
        0,
        bookableSeatCapacity(bus) - (bookedSeats[bus.id] || 0)
      ),
    }));

    return this.enrichWithReturnTimes(formatted);
  }

  /**
   * Search available buses (consumer)
   */
  async searchBuses(filters) {
    let query = db('buses')
      .join('routes', 'buses.route_id', 'routes.id')
      .select(
        'buses.*',
        'routes.name as route_name',
        'routes.source',
        'routes.destination'
      )
      .where('buses.status', 'scheduled')
      .where('buses.departure_time', '>', new Date())
      .where('routes.is_active', true);

    if (filters.routeId) {
      query = query.where('buses.route_id', filters.routeId);
    }

    if (filters.source) {
      query = query.whereILike('routes.source', `%${filters.source}%`);
    }

    if (filters.destination) {
      query = query.whereILike('routes.destination', `%${filters.destination}%`);
    }

    if (filters.date) {
      const startOfDay = new Date(filters.date);
      startOfDay.setHours(0, 0, 0, 0);
      const endOfDay = new Date(filters.date);
      endOfDay.setHours(23, 59, 59, 999);

      query = query.whereBetween('buses.departure_time', [startOfDay, endOfDay]);
    }

    query = query.orderBy('buses.departure_time', 'asc');

    // Pagination
    const page = filters.page || 1;
    const limit = filters.limit || 20;
    query = query.offset((page - 1) * limit).limit(limit);

    const buses = await query;

    // Get booked seats count for each bus
    const busIds = buses.map((b) => b.id);
    const bookedSeats = await this.getBookedSeatsCount(busIds);

    const formatted = buses.map((bus) => ({
      ...this.formatBus(bus),
      route: {
        name: bus.route_name,
        source: bus.source,
        destination: bus.destination,
      },
      bookedSeats: bookedSeats[bus.id] || 0,
      availableSeats: Math.max(
        0,
        bookableSeatCapacity(bus) - (bookedSeats[bus.id] || 0)
      ),
    }));

    return this.enrichWithReturnTimes(formatted);
  }

  /**
   * Get bus by ID with details
   */
  async getBusById(busId) {
    const bus = await db('buses')
      .join('routes', 'buses.route_id', 'routes.id')
      .select(
        'buses.*',
        'routes.name as route_name',
        'routes.source',
        'routes.destination'
      )
      .where('buses.id', busId)
      .first();

    if (!bus) {
      throw new NotFoundError('Bus');
    }

    // Get pickup points for this specific bus
    const pickupPoints = await db('pickup_points')
      .where('bus_id', busId)
      .orderBy('sequence', 'asc');

    // Get booked seats count
    const bookedSeats = await this.getBookedSeatsCount([busId]);
    let returnTimes = {};
    if (bus.trip_type === 'round_trip' && bus.return_bus_id) {
      const returnBus = await db('buses')
        .select('departure_time', 'arrival_time')
        .where('id', bus.return_bus_id)
        .first();

      if (returnBus) {
        returnTimes = {
          returnDepartureTime: returnBus.departure_time,
          returnArrivalTime: returnBus.arrival_time,
        };
      }
    }

    return {
      ...this.formatBus(bus),
      ...returnTimes,
      route: {
        id: bus.route_id,
        name: bus.route_name,
        source: bus.source,
        destination: bus.destination,
      },
      pickupPoints: pickupPoints.map((pp) => ({
        id: pp.id,
        name: pp.name,
        address: pp.address,
        sequence: pp.sequence,
      })),
      bookedSeats: bookedSeats[busId] || 0,
      availableSeats: Math.max(
        0,
        bookableSeatCapacity(bus) - (bookedSeats[busId] || 0)
      ),
    };
  }

  /**
   * Update bus
   */
  async updateBus(busId, data) {
    const updateData = { updated_at: new Date() };

    if (data.busName !== undefined) updateData.bus_name = data.busName;
    if (data.totalSeats !== undefined) updateData.total_seats = data.totalSeats;
    if (data.price !== undefined) updateData.price = data.price;
    if (data.departureTime !== undefined) updateData.departure_time = data.departureTime;
    if (data.bookingHoldMinutes !== undefined) updateData.booking_hold_minutes = data.bookingHoldMinutes;
    if (data.status !== undefined) updateData.status = data.status;

    const [bus] = await db('buses')
      .where('id', busId)
      .update(updateData)
      .returning('*');

    if (!bus) {
      throw new NotFoundError('Bus');
    }

    return this.formatBus(bus);
  }

  /**
   * Cancel bus trip
   */
  async cancelBus(busId) {
    const [bus] = await db('buses')
      .where('id', busId)
      .update({ status: 'cancelled', updated_at: new Date() })
      .returning('*');

    if (!bus) {
      throw new NotFoundError('Bus');
    }

    // Cancel all active bookings for this bus
    await db('bookings')
      .where('bus_id', busId)
      .whereIn('status', ACTIVE_SEAT_STATUSES)
      .update({ status: 'cancelled', updated_at: new Date() });

    return { message: 'Bus trip cancelled successfully' };
  }

  /**
   * Get booked seats count for buses
   * Only count non-cancelled, non-expired, non-rejected bookings
   */
  async getBookedSeatsCount(busIds) {
    if (busIds.length === 0) return {};

    const result = await db('bookings')
      .select('bus_id')
      .sum('seat_count as total')
      .whereIn('bus_id', busIds)
      .whereIn('status', ACTIVE_SEAT_STATUSES)
      .groupBy('bus_id');

    return result.reduce((acc, row) => {
      acc[row.bus_id] = parseInt(row.total, 10);
      return acc;
    }, {});
  }

  /**
   * For round-trip buses, attach the return bus's departure & arrival times
   */
  async enrichWithReturnTimes(busList) {
    const returnBusIds = busList
      .filter((b) => b.returnBusId && b.tripType === 'round_trip')
      .map((b) => b.returnBusId);

    if (returnBusIds.length === 0) return busList;

    const returnBuses = await db('buses')
      .select('id', 'departure_time', 'arrival_time')
      .whereIn('id', returnBusIds);

    const returnMap = returnBuses.reduce((acc, rb) => {
      acc[rb.id] = rb;
      return acc;
    }, {});

    return busList.map((bus) => {
      if (bus.returnBusId && returnMap[bus.returnBusId]) {
        const rb = returnMap[bus.returnBusId];
        return {
          ...bus,
          returnDepartureTime: rb.departure_time,
          returnArrivalTime: rb.arrival_time,
        };
      }
      return bus;
    });
  }

  // ============ FORMATTERS ============

  formatBus(bus) {
    return {
      id: bus.id,
      routeId: bus.route_id,
      busName: bus.bus_name,
      totalSeats: bus.total_seats,
      seatStartNumber: bus.seat_start_number,
      price: parseFloat(bus.price),
      departureTime: bus.departure_time,
      arrivalTime: bus.arrival_time,
      contactName: bus.contact_name,
      contactPhone: bus.contact_phone,
      tripType: bus.trip_type,
      returnBusId: bus.return_bus_id,
      bookingHoldMinutes: bus.booking_hold_minutes,
      status: bus.status,
      createdAt: bus.created_at,
      updatedAt: bus.updated_at,
    };
  }
}

module.exports = new BusService();
