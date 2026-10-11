const test = require('node:test');
const assert = require('node:assert/strict');
const jwt = require('jsonwebtoken');

const config = require('../src/config');
const { chalisaIdentity } = require('../src/middlewares/chalisaIdentity');
const { authenticate } = require('../src/middlewares/authenticate');
const chalisa = require('../src/services/chalisaService');
const { CAPABILITIES, capabilitiesFor } = require('../src/services/adminCapabilityService');

const { computeStreak, monthRange, normalizeText, marathonState } = chalisa.helpers;

function run(middleware, headers) {
  const req = { headers };
  let error;
  middleware(req, {}, (err) => { error = err; });
  return { req, error };
}

test('streak counts consecutive days ending today or yesterday', () => {
  assert.equal(computeStreak(['2026-10-11', '2026-10-10', '2026-10-09', '2026-10-07'], '2026-10-11'), 3);
  assert.equal(computeStreak(['2026-10-10', '2026-10-09'], '2026-10-11'), 2);
  assert.equal(computeStreak(['2026-10-08'], '2026-10-11'), 0);
  assert.equal(computeStreak(['2026-01-01', '2025-12-31'], '2026-01-01'), 2);
});

test('month ranges handle December and reject bad input', () => {
  assert.deepEqual(monthRange('2026-12'), { fromDate: '2026-12-01', toDateExclusive: '2027-01-01' });
  assert.throws(() => monthRange('2026-13'), { code: 'VALIDATION_ERROR' });
});

test('names are trimmed and control characters removed', () => {
  assert.equal(normalizeText('  Ram\u0007   Prasad \n'), 'Ram Prasad');
});

test('marathon state follows its times', () => {
  const now = new Date('2026-10-11T10:00:00Z');
  assert.equal(marathonState(null, now), 'none');
  assert.equal(marathonState({ starts_at: '2026-10-12T00:00:00Z' }, now), 'scheduled');
  assert.equal(marathonState({ starts_at: '2026-10-01T00:00:00Z' }, now), 'running');
  assert.equal(marathonState({ starts_at: '2026-10-01T00:00:00Z', ended_at: now }, now), 'ended');
  assert.equal(marathonState({ starts_at: '2026-10-12T00:00:00Z', cancelled_at: now }, now), 'cancelled');
});

test('only super admins can manage marathons', () => {
  assert.ok(capabilitiesFor({ role: 'admin', isSuperUser: true }).includes(CAPABILITIES.CHALISA_MANAGE));
  assert.ok(!capabilitiesFor({ role: 'admin', adminType: 'bus_admin' }).includes(CAPABILITIES.CHALISA_MANAGE));
  assert.ok(!capabilitiesFor({ role: 'admin', adminType: 'stay_admin' }).includes(CAPABILITIES.CHALISA_MANAGE));
  assert.ok(!capabilitiesFor({ role: 'consumer' }).includes(CAPABILITIES.CHALISA_MANAGE));
});

test('identity: a signed-in user is recognised from the access token', () => {
  const token = jwt.sign({ id: 'user-1', role: 'consumer' }, config.jwt.secret);
  const { req, error } = run(chalisaIdentity(), { authorization: `Bearer ${token}` });
  assert.equal(error, undefined);
  assert.deepEqual(req.chalisaIdentity, { userId: 'user-1' });
});

test('identity: an expired access token asks the app to refresh, not to become a guest', () => {
  const token = jwt.sign({ id: 'user-1' }, config.jwt.secret, { expiresIn: -10 });
  const { error } = run(chalisaIdentity(), { authorization: `Bearer ${token}` });
  assert.equal(error.code, 'TOKEN_EXPIRED');
});

test('identity: guests are recognised from their pass, and a bad pass is rejected', () => {
  const pass = chalisa.signGuestToken('participant-9');
  const ok = run(chalisaIdentity(), { 'x-chalisa-guest': pass });
  assert.deepEqual(ok.req.chalisaIdentity, { guestParticipantId: 'participant-9' });

  const bad = run(chalisaIdentity(), { 'x-chalisa-guest': 'forged' });
  assert.equal(bad.error.code, 'INVALID_GUEST_TOKEN');

  const lenient = run(chalisaIdentity({ allowInvalidGuest: true }), { 'x-chalisa-guest': 'forged' });
  assert.equal(lenient.error, undefined);
  assert.deepEqual(lenient.req.chalisaIdentity, {});
});

test('a guest pass can never be used as a sign-in token', () => {
  const pass = chalisa.signGuestToken('participant-9');
  const { error } = run(authenticate, { authorization: `Bearer ${pass}` });
  assert.equal(error.code, 'INVALID_TOKEN');
});

test('creating new guests is rate limited per IP, but existing guests and users are not', async () => {
  process.env.CHALISA_NEW_GUESTS_PER_HOUR = '2';
  delete require.cache[require.resolve('../src/routes/chalisa')];
  const router = require('../src/routes/chalisa');
  const layer = router.stack.find((l) => l.route?.path === '/participant');
  const limiter = layer.route.stack[1].handle;

  const call = (identity) => new Promise((resolve) => {
    const req = { ip: '203.0.113.7', headers: {}, chalisaIdentity: identity, app: { get: () => false } };
    const res = {
      statusCode: 200,
      setHeader() {}, getHeader() {}, append() {},
      status(code) { this.statusCode = code; return this; },
      send() { resolve(this.statusCode); return this; },
      json() { resolve(this.statusCode); return this; },
      end() { resolve(this.statusCode); return this; },
    };
    limiter(req, res, () => resolve('next'));
  });

  assert.equal(await call({}), 'next');
  assert.equal(await call({}), 'next');
  assert.equal(await call({}), 429);
  assert.equal(await call({ guestParticipantId: 'p1' }), 'next');
  assert.equal(await call({ userId: 'u1' }), 'next');
  delete process.env.CHALISA_NEW_GUESTS_PER_HOUR;
});
