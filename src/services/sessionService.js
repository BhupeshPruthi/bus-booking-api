const config = require('../config');
const { db } = require('../config/database');
const { UnauthorizedError } = require('../utils/errors');
const {
  capabilitiesFor,
  normalizeAdminType,
} = require('./adminCapabilityService');

function withConfiguredSuperUser(user) {
  if (!user) return null;

  const isConfiguredSuperUser =
    (config.superUserMobile && user.mobile === config.superUserMobile) ||
    (config.superUserEmail &&
      user.email &&
      String(user.email).toLowerCase() === config.superUserEmail);

  return isConfiguredSuperUser ? { ...user, isSuperUser: true } : user;
}

async function getCurrentUser(userId, database = db) {
  if (!userId) {
    throw new UnauthorizedError(
      'Session user could not be resolved. Please sign in again.',
      'SESSION_USER_NOT_FOUND'
    );
  }

  const user = await database('users')
    .select('id', 'mobile', 'email', 'role', 'admin_type')
    .where('id', userId)
    .first();

  if (!user) {
    throw new UnauthorizedError(
      'Your account no longer exists. Please sign in again.',
      'SESSION_USER_NOT_FOUND'
    );
  }

  return withConfiguredSuperUser(user);
}

function formatSession(user) {
  return {
    id: user.id,
    role: user.role,
    adminType: normalizeAdminType(user),
    isSuperUser: user.isSuperUser === true,
    capabilities: capabilitiesFor(user),
  };
}

async function getSession(userId, database = db) {
  return formatSession(await getCurrentUser(userId, database));
}

module.exports = {
  getCurrentUser,
  getSession,
  formatSession,
  withConfiguredSuperUser,
};
