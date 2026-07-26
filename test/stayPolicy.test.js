const test = require('node:test');
const assert = require('node:assert/strict');

const {
  ADMIN_TYPES,
  CAPABILITIES,
  normalizeAdminType,
  capabilitiesFor,
} = require('../src/services/adminCapabilityService');
const {
  nightsBetween,
  databaseDateOnly,
  checkInInstant,
  checkoutInstant,
  calculateLineTotal,
  calculateGuestCapacity,
  refundEligibility,
} = require('../src/services/stayService').helpers;
const {
  INVENTORY_HOLDING_STATUSES,
  MATTRESS_NIGHTLY_RATE,
} = require('../src/services/stayService').constants;
const {
  stayCancellationDecisionSchema,
  createStayBookingSchema,
  updateAdminTypeSchema,
} = require('../src/validators/schemas');

test('legacy admins retain Bus, Pooja, and Event capabilities', () => {
  const user = { role: 'admin' };
  assert.equal(normalizeAdminType(user), ADMIN_TYPES.BUS);
  assert.deepEqual(capabilitiesFor(user), [
    CAPABILITIES.BUS_MANAGE,
    CAPABILITIES.POOJA_MANAGE,
    CAPABILITIES.EVENT_MANAGE,
  ]);
});

test('Stay Admin cannot inherit Bus, Pooja, or Event access', () => {
  const capabilities = capabilitiesFor({ role: 'admin', adminType: ADMIN_TYPES.STAY });
  assert.deepEqual(capabilities, [CAPABILITIES.STAY_MANAGE]);
});

test('admin_type alone cannot elevate a consumer account', () => {
  assert.equal(
    normalizeAdminType({ role: 'consumer', adminType: ADMIN_TYPES.STAY }),
    null
  );
  assert.deepEqual(
    capabilitiesFor({ role: 'consumer', adminType: ADMIN_TYPES.SUPER }),
    []
  );
});

test('Super Admin and configured superusers receive all capabilities', () => {
  assert.deepEqual(
    new Set(capabilitiesFor({ role: 'admin', adminType: ADMIN_TYPES.SUPER })),
    new Set(Object.values(CAPABILITIES))
  );
  assert.equal(normalizeAdminType({ role: 'consumer', isSuperUser: true }), ADMIN_TYPES.SUPER);
});

test('Bus Admin retains Event access while Stay Admin remains isolated', () => {
  assert.equal(
    capabilitiesFor({ role: 'admin', adminType: ADMIN_TYPES.BUS })
      .includes(CAPABILITIES.EVENT_MANAGE),
    true
  );
  assert.equal(
    capabilitiesFor({ role: 'admin', adminType: ADMIN_TYPES.STAY })
      .includes(CAPABILITIES.EVENT_MANAGE),
    false
  );
});

test('only confirmed inventory, including confirmed cancellation requests, is held', () => {
  assert.deepEqual(INVENTORY_HOLDING_STATUSES, ['confirmed', 'cancellation_requested']);
  assert.equal(INVENTORY_HOLDING_STATUSES.includes('pending'), false);
});

test('Stay nights use checkout-exclusive calendar dates', () => {
  assert.equal(nightsBetween('2026-08-01', '2026-08-02'), 1);
  assert.equal(nightsBetween('2026-08-01', '2026-08-05'), 4);
  assert.throws(
    () => nightsBetween('2026-08-01', '2026-08-01'),
    /Check-out date must be after check-in date/
  );
});

test('Stay check-in is noon and checkout is 11 AM India time', () => {
  assert.equal(checkInInstant('2026-08-01').toISOString(), '2026-08-01T06:30:00.000Z');
  assert.equal(checkoutInstant('2026-08-02').toISOString(), '2026-08-02T05:30:00.000Z');
});

test('PostgreSQL DATE values retain their calendar date', () => {
  const postgresDate = new Date(2026, 7, 1);
  assert.equal(databaseDateOnly(postgresDate, 'check_in_date'), '2026-08-01');
  assert.equal(databaseDateOnly('2026-08-01', 'check_in_date'), '2026-08-01');
});

test('Stay prices are inclusive per unit per night totals', () => {
  assert.equal(calculateLineTotal(1200, 2, 3), 7200);
  assert.equal(calculateLineTotal(1500, 1, 1), 1500);
  assert.equal(calculateLineTotal(1600, 1, 2), 3200);
  assert.equal(calculateLineTotal(3500, 3, 1), 10500);
});

test('mattresses cost ₹200 each per night', () => {
  assert.equal(MATTRESS_NIGHTLY_RATE, 200);
  assert.equal(calculateLineTotal(MATTRESS_NIGHTLY_RATE, 3, 2), 1200);
});

test('Stay mattress quantity is capped at 100 per booking', () => {
  const baseBooking = {
    checkInDate: '2026-08-01',
    checkOutDate: '2026-08-02',
    items: [{ unitTypeCode: 'three_bed_room', quantity: 1 }],
    guestCount: 1,
    contactName: 'Guest',
    contactEmail: 'guest@example.com',
    contactPhone: '9999999999',
    cancellationPolicyAccepted: true,
  };

  assert.equal(createStayBookingSchema.validate({
    ...baseBooking,
    mattressQuantity: 100,
  }).error, undefined);
  assert.match(
    createStayBookingSchema.validate({
      ...baseBooking,
      mattressQuantity: 101,
    }).error?.message || '',
    /less than or equal to 100/
  );
});

test('Stay guest capacity is derived from the selected accommodation', () => {
  assert.equal(calculateGuestCapacity([
    { capacity_per_unit: 3, quantity: 2 },
    { capacity_per_unit: 4, quantity: 1 },
  ]), 10);
});

test('Stay guest count cannot exceed the PostgreSQL integer range', () => {
  const result = createStayBookingSchema.validate({
    checkInDate: '2026-08-01',
    checkOutDate: '2026-08-02',
    items: [{ unitTypeCode: 'three_bed_room', quantity: 1 }],
    guestCount: 2147483648,
    contactName: 'Guest',
    contactEmail: 'guest@example.com',
    contactPhone: '9999999999',
    cancellationPolicyAccepted: true,
  });
  assert.match(result.error?.message || '', /less than or equal to 2147483647/);
});

test('Stay duration is capped at exactly seven nights', () => {
  assert.equal(nightsBetween('2026-08-01', '2026-08-08'), 7);
  assert.throws(
    () => nightsBetween('2026-08-01', '2026-08-09'),
    /cannot exceed 7 nights/
  );
});

test('refund eligibility uses request time and exact noon check-in cutoff', () => {
  const exactly48Hours = new Date('2026-07-30T06:30:00.000Z');
  const oneSecondLate = new Date('2026-07-30T06:30:01.000Z');

  assert.equal(
    refundEligibility('2026-08-01', exactly48Hours).standardFullRefundEligible,
    true
  );
  assert.equal(
    refundEligibility('2026-08-01', oneSecondLate).standardFullRefundEligible,
    false
  );
});

test('late cancellations support full, partial, or no discretionary refund', () => {
  for (const refundDecision of ['full', 'none']) {
    assert.equal(stayCancellationDecisionSchema.validate({
      action: 'approve',
      refundDecision,
      reason: refundDecision === 'none' ? 'Inside cutoff' : undefined,
    }).error, undefined);
  }
  assert.equal(stayCancellationDecisionSchema.validate({
    action: 'approve',
    refundDecision: 'partial',
    refundAmount: 500,
    reason: 'Admin discretion',
  }).error, undefined);
});

test('Super Admin can revoke admin access with an audited reason', () => {
  assert.equal(updateAdminTypeSchema.validate({
    adminType: 'consumer',
    reason: 'Access no longer required',
  }).error, undefined);
  assert.ok(updateAdminTypeSchema.validate({ adminType: 'consumer' }).error);
});
