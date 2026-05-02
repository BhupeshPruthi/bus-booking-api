const test = require('node:test');
const assert = require('node:assert/strict');

const seatAvailability = require('../src/services/seatAvailabilityService');

function range(start, end) {
  return Array.from({ length: end - start + 1 }, (_, index) => start + index);
}

test('reserveBestAvailableSeats prefers a consecutive block for group requests', () => {
  const occupiedSeats = new Set(range(1, 20).filter((seat) => seat !== 11));

  const seats = seatAvailability.reserveBestAvailableSeats(occupiedSeats, 2, 1, 25);

  assert.deepEqual(seats, [21, 22]);
  assert.equal(occupiedSeats.has(11), false);
  assert.equal(occupiedSeats.has(21), true);
  assert.equal(occupiedSeats.has(22), true);
});

test('reserveBestAvailableSeats falls back to separate seats when no block exists', () => {
  const occupiedSeats = new Set([...range(1, 10), 12, 14]);

  const seats = seatAvailability.reserveBestAvailableSeats(occupiedSeats, 2, 1, 15);

  assert.deepEqual(seats, [11, 13]);
});

test('reserveBestAvailableSeats keeps first-free behavior for single-seat requests', () => {
  const occupiedSeats = new Set(range(1, 20).filter((seat) => seat !== 11));

  const seats = seatAvailability.reserveBestAvailableSeats(occupiedSeats, 1, 1, 25);

  assert.deepEqual(seats, [11]);
});

test('reserveBestAvailableSeats respects the configured first sale seat', () => {
  const occupiedSeats = new Set([9, 10, 11, 13]);

  const seats = seatAvailability.reserveBestAvailableSeats(occupiedSeats, 2, 9, 15);

  assert.deepEqual(seats, [14, 15]);
  assert.equal(occupiedSeats.has(8), false);
});

test('collectOccupiedSeats combines assigned seats and legacy seat counts', () => {
  const occupiedSeats = seatAvailability.collectOccupiedSeats(
    [
      { assigned_seats: '10,12', seat_count: 2 },
      { assigned_seats: null, seat_count: 2 },
    ],
    10,
    15
  );

  assert.deepEqual([...occupiedSeats].sort((a, b) => a - b), [10, 11, 12, 13]);
});

test('getReservedSeatCountFromBookings releases cancelled, rejected, and expired bookings', () => {
  const reservedSeats = seatAvailability.getReservedSeatCountFromBookings([
    { status: 'pending', seat_count: 1 },
    { status: 'payment_uploaded', seat_count: 2 },
    { status: 'confirmed', seat_count: 3 },
    { status: 'cancellation_requested', seat_count: 4 },
    { status: 'cancelled', seat_count: 5 },
    { status: 'rejected', seat_count: 6 },
    { status: 'expired', seat_count: 7 },
  ]);

  assert.equal(reservedSeats, 10);
});

test('getReservationBusIds reserves both sides of round trips', () => {
  assert.deepEqual(
    seatAvailability.getReservationBusIds({
      id: 'outbound',
      trip_type: 'round_trip',
      return_bus_id: 'return',
    }),
    ['outbound', 'return']
  );

  assert.deepEqual(
    seatAvailability.getReservationBusIds({
      id: 'one-way',
      trip_type: 'one_way',
      return_bus_id: 'ignored',
    }),
    ['one-way']
  );
});
