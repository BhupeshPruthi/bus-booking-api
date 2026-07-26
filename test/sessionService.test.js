const test = require('node:test');
const assert = require('node:assert/strict');

const {
  getCurrentUser,
  getSession,
  formatSession,
} = require('../src/services/sessionService');
const {
  CAPABILITIES,
} = require('../src/services/adminCapabilityService');

function fakeDatabase(row) {
  return () => ({
    select() {
      return this;
    },
    where() {
      return this;
    },
    async first() {
      return row;
    },
  });
}

test('session uses the database admin type and exposes resolved capabilities', async () => {
  const session = await getSession(
    'user-1',
    fakeDatabase({
      id: 'user-1',
      role: 'admin',
      admin_type: 'super_admin',
      email: 'admin@example.com',
      mobile: null,
    })
  );

  assert.equal(session.id, 'user-1');
  assert.equal(session.role, 'admin');
  assert.equal(session.adminType, 'super_admin');
  assert.deepEqual(new Set(session.capabilities), new Set(Object.values(CAPABILITIES)));
});

test('session response excludes email and mobile', () => {
  const session = formatSession({
    id: 'user-1',
    role: 'admin',
    admin_type: 'stay_admin',
    email: 'admin@example.com',
    mobile: '9999999999',
  });

  assert.equal(Object.hasOwn(session, 'email'), false);
  assert.equal(Object.hasOwn(session, 'mobile'), false);
});

test('missing JWT user id returns an actionable 401 error', async () => {
  await assert.rejects(
    getCurrentUser(null, fakeDatabase(null)),
    (error) =>
      error.statusCode === 401 &&
      error.code === 'SESSION_USER_NOT_FOUND' &&
      /sign in again/i.test(error.message)
  );
});

test('JWT user absent from the database returns SESSION_USER_NOT_FOUND', async () => {
  await assert.rejects(
    getCurrentUser('deleted-user', fakeDatabase(null)),
    (error) =>
      error.statusCode === 401 &&
      error.code === 'SESSION_USER_NOT_FOUND' &&
      /account no longer exists/i.test(error.message)
  );
});
