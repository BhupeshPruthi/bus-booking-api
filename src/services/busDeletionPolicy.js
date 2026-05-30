const { ConflictError } = require('../utils/errors');

const REJECTED_WHEN_BUS_CANCELLED_STATUSES = ['pending', 'payment_uploaded'];
const CANCELLED_WHEN_BUS_CANCELLED_STATUSES = ['confirmed', 'cancellation_requested'];
const TERMINAL_BOOKING_STATUSES = ['cancelled', 'rejected', 'expired'];
const BUS_CANCELLED_BY_ADMIN_REASON = 'Bus cancelled by admin';

function unique(values) {
  return [...new Set(values.filter(Boolean))];
}

function getDeleteTargetBusIds(selectedBus, linkedBuses = []) {
  const ids = [selectedBus?.id];

  if (selectedBus?.trip_type === 'round_trip' && selectedBus.return_bus_id) {
    ids.push(selectedBus.return_bus_id);
  }

  linkedBuses.forEach((bus) => {
    if (bus.return_bus_id === selectedBus?.id) {
      ids.push(bus.id);
    }
  });

  return unique(ids);
}

function assertBusesAreFuture(buses, now = new Date()) {
  const startedBus = buses.find((bus) => new Date(bus.departure_time) <= now);
  if (startedBus) {
    throw new ConflictError('Cannot delete a bus after departure time');
  }
}

function splitBookingsForBusDeletion(bookings = []) {
  return bookings.reduce(
    (acc, booking) => {
      if (REJECTED_WHEN_BUS_CANCELLED_STATUSES.includes(booking.status)) {
        acc.rejectIds.push(booking.id);
      } else if (CANCELLED_WHEN_BUS_CANCELLED_STATUSES.includes(booking.status)) {
        acc.cancelIds.push(booking.id);
      } else if (TERMINAL_BOOKING_STATUSES.includes(booking.status)) {
        acc.terminalIds.push(booking.id);
      }
      return acc;
    },
    { rejectIds: [], cancelIds: [], terminalIds: [] }
  );
}

function buildBusDeletionResult({ busIds, mode, rejectedBookingCount = 0, cancelledBookingCount = 0 }) {
  return {
    message: mode === 'deleted'
      ? 'Bus deleted successfully'
      : 'Bus cancelled successfully',
    busIds,
    mode,
    rejectedBookingCount,
    cancelledBookingCount,
  };
}

module.exports = {
  BUS_CANCELLED_BY_ADMIN_REASON,
  REJECTED_WHEN_BUS_CANCELLED_STATUSES,
  CANCELLED_WHEN_BUS_CANCELLED_STATUSES,
  TERMINAL_BOOKING_STATUSES,
  getDeleteTargetBusIds,
  assertBusesAreFuture,
  splitBookingsForBusDeletion,
  buildBusDeletionResult,
};
