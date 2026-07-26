const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const {
  decodeCursor,
  encodeCursor,
  normalizedTypes,
  availableActions,
  formatItem,
} = require('../src/services/unifiedBookingService').helpers;
const {
  unifiedBookingListSchema,
  unifiedBookingDetailSchema,
} = require('../src/validators/schemas');

const migration = fs.readFileSync(
  path.join(__dirname, '../src/db/migrations/20260726000029_create_user_booking_feed.js'),
  'utf8'
);

test('unified feed projects all booking domains without replacing source tables', () => {
  assert.match(migration, /CREATE OR REPLACE VIEW user_booking_feed/);
  assert.match(migration, /'bus'::text AS booking_type/);
  assert.match(migration, /'stay'::text AS booking_type/);
  assert.match(migration, /'pooja'::text AS booking_type/);
  assert.doesNotMatch(migration, /CREATE TABLE user_booking_feed/);
});

test('unified feed uses each domain end boundary', () => {
  assert.match(migration, /return_bus\.arrival_time/);
  assert.match(migration, /TIME '11:00'/);
  assert.match(migration, /INTERVAL '8 hours'/);
});

test('unified booking cursor round trips safely', () => {
  const row = {
    starts_at: '2026-08-01T06:30:00.000Z',
    booking_type: 'stay',
    booking_id: 'e4d2c21c-6d46-4a21-b7f1-df2f5c75e394',
  };
  const cursor = encodeCursor(row);
  const decoded = decodeCursor(cursor);
  assert.equal(decoded.startsAt.toISOString(), row.starts_at);
  assert.equal(decoded.bookingType, row.booking_type);
  assert.equal(decoded.bookingId, row.booking_id);
  assert.throws(() => decodeCursor('not-a-valid-cursor'), /Invalid booking cursor/);
});

test('unified booking type filters are deduplicated', () => {
  assert.deepEqual(normalizedTypes(), ['bus', 'stay', 'pooja']);
  assert.deepEqual(normalizedTypes('stay,bus,stay'), ['stay', 'bus']);
});

test('unified booking query validates buckets, filters, and limits', () => {
  assert.equal(unifiedBookingListSchema.validate({
    bucket: 'failed',
    types: 'bus,stay,pooja',
    limit: 50,
  }).error, undefined);
  assert.ok(unifiedBookingListSchema.validate({ bucket: 'ongoing' }).error);
  assert.ok(unifiedBookingListSchema.validate({ types: 'bus,event' }).error);
  assert.ok(unifiedBookingListSchema.validate({ limit: 51 }).error);
});

test('unified booking detail validates type and UUID before querying PostgreSQL', () => {
  assert.equal(unifiedBookingDetailSchema.validate({
    bookingType: 'stay',
    bookingId: 'e4d2c21c-6d46-4a21-b7f1-df2f5c75e394',
  }).error, undefined);
  assert.ok(unifiedBookingDetailSchema.validate({
    bookingType: 'event',
    bookingId: 'not-a-uuid',
  }).error);
});

test('available actions preserve domain cancellation rules', () => {
  const future = '2099-01-01T00:00:00.000Z';
  assert.deepEqual(availableActions({
    booking_type: 'bus',
    raw_status: 'confirmed',
    starts_at: future,
    ends_at: future,
  }), ['view', 'request_cancellation']);
  assert.deepEqual(availableActions({
    booking_type: 'stay',
    raw_status: 'pending',
    starts_at: future,
    ends_at: future,
  }), ['view', 'request_cancellation']);
  assert.deepEqual(availableActions({
    booking_type: 'pooja',
    raw_status: 'confirmed',
    starts_at: future,
    ends_at: future,
  }), ['view']);
});

test('pooja unified items keep amount and currency hidden', () => {
  const item = formatItem({
    booking_type: 'pooja',
    booking_id: 'booking-id',
    reference: 'Token #1',
    raw_status: 'confirmed',
    normalized_status: 'confirmed',
    starts_at: '2026-08-01T00:00:00.000Z',
    ends_at: '2026-08-01T08:00:00.000Z',
    created_at: '2026-07-01T00:00:00.000Z',
    updated_at: '2026-07-01T00:00:00.000Z',
    total_amount: null,
    currency: null,
    title: 'Pooja',
    subtitle: 'Token #1',
    details: {},
  });
  assert.equal(item.totalAmount, null);
  assert.equal(item.currency, null);
});
