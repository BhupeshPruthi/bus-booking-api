const { db } = require('../config/database');
const { NotFoundError, ValidationError } = require('../utils/errors');
const seatAvailability = require('./seatAvailabilityService');

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

    const reservedSeatsByBus = await seatAvailability.getReservedSeatsByBus(db, buses);

    const formatted = buses.map((bus) => ({
      ...this.formatBus(bus),
      route: {
        name: bus.route_name,
        source: bus.source,
        destination: bus.destination,
      },
      ...seatAvailability.buildAvailability(bus, reservedSeatsByBus[bus.id]),
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

    const reservedSeatsByBus = await seatAvailability.getReservedSeatsByBus(db, buses);

    const formatted = buses.map((bus) => ({
      ...this.formatBus(bus),
      route: {
        name: bus.route_name,
        source: bus.source,
        destination: bus.destination,
      },
      ...seatAvailability.buildAvailability(bus, reservedSeatsByBus[bus.id]),
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

    const reservedSeatsByBus = await seatAvailability.getReservedSeatsByBus(db, [bus]);
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
      ...seatAvailability.buildAvailability(bus, reservedSeatsByBus[busId]),
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

    // Cancel every booking that still reserves seats for this bus.
    await db('bookings')
      .where('bus_id', busId)
      .whereNotIn('status', seatAvailability.RELEASED_SEAT_STATUSES)
      .update({ status: 'cancelled', updated_at: new Date() });

    return { message: 'Bus trip cancelled successfully' };
  }

  /**
   * Get booked seats count for buses
   * Any requested seat remains reserved until the request is cancelled.
   */
  async getBookedSeatsCount(busIds) {
    if (!busIds.length) return {};

    const buses = await db('buses')
      .select('id', 'trip_type', 'return_bus_id')
      .whereIn('id', busIds);

    const counts = await seatAvailability.getReservedSeatsByBus(db, buses);
    return busIds.reduce((acc, busId) => {
      acc[busId] = counts[busId] || 0;
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
