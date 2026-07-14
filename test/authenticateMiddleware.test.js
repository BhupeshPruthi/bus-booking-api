const assert = require('node:assert/strict');
const test = require('node:test');
const jwt = require('jsonwebtoken');

const config = require('../src/config');
const { authenticate } = require('../src/middlewares/authenticate');

function runAuthenticate(token) {
  const req = {
    headers: token ? { authorization: `Bearer ${token}` } : {},
  };

  let nextError;
  authenticate(req, {}, (error) => {
    nextError = error;
  });

  return { req, error: nextError };
}

test('authenticate maps expired access tokens to TOKEN_EXPIRED 401', () => {
  const token = jwt.sign({ id: 'user-1' }, config.jwt.secret, { expiresIn: -1 });

  const { error } = runAuthenticate(token);

  assert.equal(error.statusCode, 401);
  assert.equal(error.code, 'TOKEN_EXPIRED');
  assert.equal(error.message, 'Token expired');
});

test('authenticate maps malformed access tokens to INVALID_TOKEN 401', () => {
  const { error } = runAuthenticate('not-a-jwt');

  assert.equal(error.statusCode, 401);
  assert.equal(error.code, 'INVALID_TOKEN');
  assert.equal(error.message, 'Invalid token');
});

test('authenticate attaches decoded user for valid access tokens', () => {
  const token = jwt.sign({ id: 'user-1', role: 'consumer' }, config.jwt.secret, {
    expiresIn: '5m',
  });

  const { req, error } = runAuthenticate(token);

  assert.equal(error, undefined);
  assert.equal(req.user.id, 'user-1');
  assert.equal(req.user.role, 'consumer');
});
