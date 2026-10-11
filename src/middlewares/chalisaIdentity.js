const jwt = require('jsonwebtoken');
const config = require('../config');
const { UnauthorizedError } = require('../utils/errors');
const { verifyGuestToken } = require('../services/chalisaService');

const GUEST_HEADER = 'x-chalisa-guest';

/**
 * Works out who is counting: a signed-in user (Bearer token) or a guest (X-Chalisa-Guest).
 * Unlike optionalAuth, an expired sign-in token is rejected with TOKEN_EXPIRED so the app
 * refreshes it, instead of quietly treating the user as a new guest.
 * Sets req.chalisaIdentity to { userId } | { guestParticipantId } | {}.
 */
const chalisaIdentity = ({ allowInvalidGuest = false } = {}) => (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (authHeader && authHeader.startsWith('Bearer ')) {
      try {
        const decoded = jwt.verify(authHeader.slice('Bearer '.length), config.jwt.secret);
        req.user = decoded;
        req.chalisaIdentity = { userId: decoded.id };
        return next();
      } catch (error) {
        if (error.name === 'TokenExpiredError') {
          return next(new UnauthorizedError('Token expired', 'TOKEN_EXPIRED'));
        }
        return next(new UnauthorizedError('Invalid token', 'INVALID_TOKEN'));
      }
    }

    const guestToken = req.headers[GUEST_HEADER];
    if (guestToken) {
      try {
        req.chalisaIdentity = { guestParticipantId: verifyGuestToken(guestToken) };
      } catch (error) {
        if (!allowInvalidGuest) return next(error);
        req.chalisaIdentity = {};
      }
      return next();
    }

    req.chalisaIdentity = {};
    next();
  } catch (error) {
    next(error);
  }
};

module.exports = { chalisaIdentity, GUEST_HEADER };
