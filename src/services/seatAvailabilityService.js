const RELEASED_SEAT_STATUSES = ['cancelled', 'rejected', 'expired'];

function toPositiveInteger(value, fallback = 0) {
  const parsed = parseInt(value, 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function reservesSeatStatus(status) {
  return !RELEASED_SEAT_STATUSES.includes(status);
}

/** Max passengers that can be booked for this bus (seats seat_start..total_seats). */
function bookableSeatCapacity(bus) {
  const totalSeats = toPositiveInteger(bus.total_seats ?? bus.totalSeats, 0);
  const seatStart = toPositiveInteger(bus.seat_start_number ?? bus.seatStartNumber, 1);
  return Math.max(0, totalSeats - seatStart + 1);
}

function buildAvailability(bus, reservedSeats = 0) {
  const bookedSeats = Math.max(0, parseInt(reservedSeats || 0, 10) || 0);
  const bookableSeats = bookableSeatCapacity(bus);
  return {
    bookedSeats,
    availableSeats: Math.max(0, bookableSeats - bookedSeats),
    bookableSeats,
  };
}

function parseAssignedSeats(value) {
  if (!value) return [];
  return String(value)
    .split(',')
    .map((seat) => parseInt(seat.trim(), 10))
    .filter((seat) => Number.isInteger(seat));
}

function reserveFirstAvailableSeats(occupiedSeats, count, seatStart, totalSeats) {
  const requestedCount = Math.max(0, parseInt(count, 10) || 0);
  const firstSeat = toPositiveInteger(seatStart, 1);
  const lastSeat = toPositiveInteger(totalSeats, 0);
  const seats = [];

  for (let seat = firstSeat; seat <= lastSeat && seats.length < requestedCount; seat += 1) {
    if (!occupiedSeats.has(seat)) {
      occupiedSeats.add(seat);
      seats.push(seat);
    }
  }

  return seats;
}

function findConsecutiveAvailableSeats(occupiedSeats, count, seatStart, totalSeats) {
  const requestedCount = Math.max(0, parseInt(count, 10) || 0);
  const firstSeat = toPositiveInteger(seatStart, 1);
  const lastSeat = toPositiveInteger(totalSeats, 0);
  const lastStartSeat = lastSeat - requestedCount + 1;

  if (requestedCount <= 0 || lastStartSeat < firstSeat) {
    return [];
  }

  for (let startSeat = firstSeat; startSeat <= lastStartSeat; startSeat += 1) {
    const seats = [];
    for (let seat = startSeat; seat < startSeat + requestedCount; seat += 1) {
      if (occupiedSeats.has(seat)) {
        break;
      }
      seats.push(seat);
    }

    if (seats.length === requestedCount) {
      return seats;
    }
  }

  return [];
}

function reserveBestAvailableSeats(occupiedSeats, count, seatStart, totalSeats) {
  const requestedCount = Math.max(0, parseInt(count, 10) || 0);

  if (requestedCount > 1) {
    const consecutiveSeats = findConsecutiveAvailableSeats(
      occupiedSeats,
      requestedCount,
      seatStart,
      totalSeats
    );

    if (consecutiveSeats.length === requestedCount) {
      consecutiveSeats.forEach((seat) => occupiedSeats.add(seat));
      return consecutiveSeats;
    }
  }

  return reserveFirstAvailableSeats(occupiedSeats, requestedCount, seatStart, totalSeats);
}

function collectOccupiedSeats(bookings, seatStart, totalSeats) {
  const occupiedSeats = new Set();
  const firstSeat = toPositiveInteger(seatStart, 1);
  const lastSeat = toPositiveInteger(totalSeats, 0);

  bookings.forEach((booking) => {
    const assignedSeats = parseAssignedSeats(booking.assigned_seats)
      .filter((seat) => seat >= firstSeat && seat <= lastSeat);

    if (assignedSeats.length > 0) {
      assignedSeats.forEach((seat) => occupiedSeats.add(seat));
      return;
    }

    // Compatibility for older rows that may not have assigned_seats populated.
    reserveFirstAvailableSeats(
      occupiedSeats,
      parseInt(booking.seat_count || 0, 10),
      firstSeat,
      lastSeat
    );
  });

  return occupiedSeats;
}

function getBusId(busOrId) {
  return typeof busOrId === 'string' ? busOrId : busOrId?.id;
}

function getReturnBusId(busOrId) {
  return typeof busOrId === 'string' ? null : (busOrId?.return_bus_id ?? busOrId?.returnBusId ?? null);
}

function getTripType(busOrId) {
  return typeof busOrId === 'string' ? null : (busOrId?.trip_type ?? busOrId?.tripType ?? null);
}

function getReservationBusIds(busOrId) {
  const busId = getBusId(busOrId);
  if (!busId) return [];

  const returnBusId = getReturnBusId(busOrId);
  if (getTripType(busOrId) === 'round_trip' && returnBusId && returnBusId !== busId) {
    return [busId, returnBusId];
  }

  return [busId];
}

function getReservedSeatCountFromBookings(bookings = []) {
  return bookings
    .filter((booking) => reservesSeatStatus(booking.status))
    .reduce((total, booking) => {
      return total + (parseInt(booking.seat_count ?? booking.seatCount ?? 0, 10) || 0);
    }, 0);
}

async function getReservedSeatsByBus(db, busesOrBusIds) {
  if (!busesOrBusIds.length) return {};

  const reservationIdsByBusId = busesOrBusIds.reduce((acc, busOrId) => {
    const busId = getBusId(busOrId);
    if (busId) acc[busId] = getReservationBusIds(busOrId);
    return acc;
  }, {});
  const allReservationBusIds = [...new Set(Object.values(reservationIdsByBusId).flat())];

  if (!allReservationBusIds.length) return {};

  const rows = await db('bookings')
    .select('bus_id')
    .sum('seat_count as total')
    .whereIn('bus_id', allReservationBusIds)
    .whereNotIn('status', RELEASED_SEAT_STATUSES)
    .groupBy('bus_id');

  const reservedByActualBusId = rows.reduce((acc, row) => {
    acc[row.bus_id] = parseInt(row.total, 10) || 0;
    return acc;
  }, {});

  return Object.entries(reservationIdsByBusId).reduce((acc, [busId, reservationBusIds]) => {
    acc[busId] = reservationBusIds.reduce((total, reservationBusId) => {
      return total + (reservedByActualBusId[reservationBusId] || 0);
    }, 0);
    return acc;
  }, {});
}

module.exports = {
  RELEASED_SEAT_STATUSES,
  reservesSeatStatus,
  bookableSeatCapacity,
  buildAvailability,
  parseAssignedSeats,
  reserveFirstAvailableSeats,
  findConsecutiveAvailableSeats,
  reserveBestAvailableSeats,
  collectOccupiedSeats,
  getReservationBusIds,
  getReservedSeatCountFromBookings,
  getReservedSeatsByBus,
};
