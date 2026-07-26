const config = require('../config');
const { db } = require('../config/database');
const { ForbiddenError } = require('../utils/errors');
const {
  CAPABILITIES,
  ADMIN_TYPES,
  normalizeAdminType,
  hasCapability,
} = require('../services/adminCapabilityService');

function withConfiguredSuperUser(req) {
  const user = req.user;
  if (!user) return null;
  const isConfiguredSuperUser =
    (config.superUserMobile && user.mobile === config.superUserMobile) ||
    (config.superUserEmail &&
      user.email &&
      String(user.email).toLowerCase() === config.superUserEmail);
  return isConfiguredSuperUser ? { ...user, isSuperUser: true } : user;
}

async function currentUser(req) {
  if (!req.user?.id) return null;
  const row = await db('users')
    .select('id', 'mobile', 'email', 'role', 'admin_type')
    .where('id', req.user.id)
    .first();
  return withConfiguredSuperUser(row);
}

const requireCapability = (capability, message) => async (req, res, next) => {
  if (!req.user) {
    return next(new ForbiddenError('User not authenticated'));
  }

  try {
    const user = await currentUser(req);
    if (!hasCapability(user, capability)) {
      return next(new ForbiddenError(message));
    }
    req.user = { ...req.user, ...user };
    next();
  } catch (error) {
    next(error);
  }
};

const busAdminOrSuperUser = requireCapability(
  CAPABILITIES.BUS_MANAGE,
  'Access denied. Bus/Pooja Admin or Super Admin required.'
);
const poojaAdminOrSuperUser = requireCapability(
  CAPABILITIES.POOJA_MANAGE,
  'Access denied. Bus/Pooja Admin or Super Admin required.'
);
const stayAdminOrSuperUser = requireCapability(
  CAPABILITIES.STAY_MANAGE,
  'Access denied. Stay Admin or Super Admin required.'
);
const eventAdminOrSuperUser = requireCapability(
  CAPABILITIES.EVENT_MANAGE,
  'Access denied. Bus Admin or Super Admin required.'
);
const adminManagerOnly = requireCapability(
  CAPABILITIES.ADMIN_MANAGE,
  'Access denied. Super Admin required.'
);

const superAdminOnly = async (req, res, next) => {
  if (!req.user) return next(new ForbiddenError('User not authenticated'));
  try {
    const user = await currentUser(req);
    if (normalizeAdminType(user) !== ADMIN_TYPES.SUPER) {
      return next(new ForbiddenError('Access denied. Super Admin required.'));
    }
    req.user = { ...req.user, ...user };
    next();
  } catch (error) {
    next(error);
  }
};

// Legacy export remains mapped to Bus/Pooja access so any route not yet migrated
// cannot accidentally grant Stay Admin access to existing operations.
const adminOrSuperUser = busAdminOrSuperUser;

module.exports = {
  requireCapability,
  busAdminOrSuperUser,
  poojaAdminOrSuperUser,
  stayAdminOrSuperUser,
  eventAdminOrSuperUser,
  adminManagerOnly,
  superAdminOnly,
  adminOrSuperUser,
};
