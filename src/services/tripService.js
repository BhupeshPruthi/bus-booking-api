const { db } = require('../config/database');
const logger = require('../utils/logger');

class TripService {
  /**
   * Create a complete trip: route + stops + bus (+ return bus if round trip).
   * Everything runs inside a single transaction.
   */
  async createTrip(data) {
    return db.transaction(async (trx) => {
      // 1. Find or create outbound route
      const outboundRoute = await this.findOrCreateRoute(
        trx,
        data.source,
        data.destination
      );

      // 2. Create outbound bus first, then attach stops to it
      const routeName = `${data.source} to ${data.destination}`;
      const [outboundBus] = await trx('buses')
        .insert({
          route_id: outboundRoute.id,
          bus_name: routeName,
          total_seats: data.totalSeats,
          seat_start_number: data.seatStartNumber || 1,
          price: data.price,
          departure_time: data.departureTime,
          arrival_time: data.arrivalTime,
          contact_name: data.contactName,
          contact_phone: data.contactPhone,
          trip_type: data.tripType,
          status: 'scheduled',
        })
        .returning('*');

      // 3. Sync pickup points for this specific bus
      await this.syncPickupPoints(trx, outboundRoute.id, outboundBus.id, data.stops);

      let returnBus = null;

      // 4. If round trip, create return route + bus
      if (data.tripType === 'round_trip') {
        const returnRoute = await this.findOrCreateRoute(
          trx,
          data.destination,
          data.source
        );

        // Reverse the stops for the return route — attached to return bus below

        const returnRouteName = `${data.destination} to ${data.source}`;
        const [returnBusRow] = await trx('buses')
          .insert({
            route_id: returnRoute.id,
            bus_name: returnRouteName,
            total_seats: data.totalSeats,
            seat_start_number: data.seatStartNumber || 1,
            price: data.price,
            departure_time: data.returnDepartureTime,
            arrival_time: data.returnArrivalTime,
            contact_name: data.contactName,
            contact_phone: data.contactPhone,
            trip_type: 'round_trip',
            return_bus_id: outboundBus.id,
            status: 'scheduled',
          })
          .returning('*');

        returnBus = returnBusRow;

        // Sync pickup points for the return bus
        const reversedStops = [...data.stops].reverse();
        await this.syncPickupPoints(trx, returnRoute.id, returnBus.id, reversedStops);

        // Link outbound bus to return bus
        await trx('buses')
          .where('id', outboundBus.id)
          .update({ return_bus_id: returnBus.id });

        outboundBus.return_bus_id = returnBus.id;
      }

      logger.info(
        `Trip created: ${data.source} -> ${data.destination} (${data.tripType}), bus_id=${outboundBus.id}`
      );

      // Fetch pickup points for the response
      const pickupPoints = await trx('pickup_points')
        .where('bus_id', outboundBus.id)
        .orderBy('sequence', 'asc');

      const result = {
        outbound: {
          ...this.formatBus(outboundBus),
          route: this.formatRoute(outboundRoute),
          stops: pickupPoints.map(this.formatPickupPoint),
        },
      };

      if (returnBus) {
        const returnRoute = await trx('routes')
          .where('id', returnBus.route_id)
          .first();
        const returnPickupPoints = await trx('pickup_points')
          .where('bus_id', returnBus.id)
          .orderBy('sequence', 'asc');

        result.return = {
          ...this.formatBus(returnBus),
          route: this.formatRoute(returnRoute),
          stops: returnPickupPoints.map(this.formatPickupPoint),
        };
      }

      return result;
    });
  }

  async findOrCreateRoute(trx, source, destination) {
    let route = await trx('routes')
      .whereRaw('LOWER(source) = LOWER(?)', [source])
      .whereRaw('LOWER(destination) = LOWER(?)', [destination])
      .where('is_active', true)
      .first();

    if (!route) {
      const [newRoute] = await trx('routes')
        .insert({
          name: `${source} - ${destination}`,
          source,
          destination,
          is_active: true,
        })
        .returning('*');
      route = newRoute;
    }

    return route;
  }

  async syncPickupPoints(trx, routeId, busId, stops) {
    if (!stops || stops.length === 0) return;

    // Remove existing pickup points for this specific bus that have no bookings
    const existingPoints = await trx('pickup_points')
      .where('bus_id', busId)
      .select('id');

    if (existingPoints.length > 0) {
      const pointIds = existingPoints.map((p) => p.id);
      const usedPoints = await trx('bookings')
        .whereIn('pickup_point_id', pointIds)
        .select('pickup_point_id')
        .groupBy('pickup_point_id');
      const usedIds = new Set(usedPoints.map((p) => p.pickup_point_id));
      const deletableIds = pointIds.filter((id) => !usedIds.has(id));

      if (deletableIds.length > 0) {
        await trx('pickup_points').whereIn('id', deletableIds).del();
      }
    }

    // Insert new pickup points linked to this bus
    const pointsToInsert = stops.map((stop, index) => ({
      route_id: routeId,
      bus_id: busId,
      name: stop.name,
      address: stop.address || null,
      sequence: index + 1,
    }));

    await trx('pickup_points').insert(pointsToInsert);
  }

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
      status: bus.status,
      createdAt: bus.created_at,
    };
  }

  formatRoute(route) {
    return {
      id: route.id,
      name: route.name,
      source: route.source,
      destination: route.destination,
    };
  }

  formatPickupPoint(pp) {
    return {
      id: pp.id,
      name: pp.name,
      address: pp.address,
      sequence: pp.sequence,
    };
  }
}

module.exports = new TripService();
