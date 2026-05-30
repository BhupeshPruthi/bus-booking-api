const test = require('node:test');
const assert = require('node:assert/strict');

const {
  assertBusesAreFuture,
  buildBusDeletionResult,
  getDeleteTargetBusIds,
  splitBookingsForBusDeletion,
} = require('../src/services/busDeletionPolicy');

const futureDate = '2099-01-01T00:00:00.000Z';
const pastDate = '2020-01-01T00:00:00.000Z';

test('empty future one-way buses can be physically deleted', () => {
  const busIds = getDeleteTargetBusIds({
    id: 'outbound',
    trip_type: 'one_way',
    return_bus_id: null,
  });

  assertBusesAreFuture([{ id: 'outbound', departure_time: futureDate }]);
  assert.deepEqual(busIds, ['outbound']);
  assert.deepEqual(
    buildBusDeletionResult({ busIds, mode: 'deleted' }),
    {
      message: 'Bus deleted successfully',
      busIds: ['outbound'],
      mode: 'deleted',
      rejectedBookingCount: 0,
      cancelledBookingCount: 0,
    }
  );
});

test('empty future round trips target both legs for physical deletion', () => {
  const busIds = getDeleteTargetBusIds({
    id: 'outbound',
    trip_type: 'round_trip',
    return_bus_id: 'return',
  });

  assertBusesAreFuture([
    { id: 'outbound', departure_time: futureDate },
    { id: 'return', departure_time: futureDate },
  ]);
  assert.deepEqual(busIds, ['outbound', 'return']);
});

test('return-leg selections still target the whole round trip', () => {
  const busIds = getDeleteTargetBusIds(
    {
      id: 'return',
      trip_type: 'round_trip',
      return_bus_id: 'outbound',
    },
    [{ id: 'outbound', return_bus_id: 'return' }]
  );

  assert.deepEqual(busIds, ['return', 'outbound']);
});

test('future buses with bookings reject pending review and cancel confirmed bookings', () => {
  const result = splitBookingsForBusDeletion([
    { id: 'pending-booking', status: 'pending' },
    { id: 'payment-booking', status: 'payment_uploaded' },
    { id: 'confirmed-booking', status: 'confirmed' },
    { id: 'cancel-request', status: 'cancellation_requested' },
    { id: 'old-cancelled', status: 'cancelled' },
    { id: 'old-rejected', status: 'rejected' },
    { id: 'old-expired', status: 'expired' },
  ]);

  assert.deepEqual(result.rejectIds, ['pending-booking', 'payment-booking']);
  assert.deepEqual(result.cancelIds, ['confirmed-booking', 'cancel-request']);
  assert.deepEqual(result.terminalIds, ['old-cancelled', 'old-rejected', 'old-expired']);
});

test('round-trip mixed booking cancellation reports both status changes', () => {
  const busIds = getDeleteTargetBusIds({
    id: 'outbound',
    trip_type: 'round_trip',
    return_bus_id: 'return',
  });
  const bookings = splitBookingsForBusDeletion([
    { id: 'outbound-pending', status: 'pending' },
    { id: 'return-confirmed', status: 'confirmed' },
  ]);

  assert.deepEqual(
    buildBusDeletionResult({
      busIds,
      mode: 'cancelled',
      rejectedBookingCount: bookings.rejectIds.length,
      cancelledBookingCount: bookings.cancelIds.length,
    }),
    {
      message: 'Bus cancelled successfully',
      busIds: ['outbound', 'return'],
      mode: 'cancelled',
      rejectedBookingCount: 1,
      cancelledBookingCount: 1,
    }
  );
});

test('past buses cannot be deleted', () => {
  assert.throws(
    () => assertBusesAreFuture([{ id: 'past-bus', departure_time: pastDate }]),
    /Cannot delete a bus after departure time/
  );
});
