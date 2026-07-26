const { ForbiddenError, UnauthorizedError } = require('../utils/errors');
const logger = require('../utils/logger');
const sessionService = require('../services/sessionService');
const {
  CAPABILITIES,
  ADMIN_TYPES,
  capabilitiesFor,
  normalizeAdminType,
  hasCapability,
} = require('../services/adminCapabilityService');

function logDenial(req, user, requiredCapability) {
  logger.warn({
    message: 'Authorization denied',
    event: 'authorization_denied',
    userId: user?.id || req.user?.id || null,
    role: user?.role || null,
    adminType: normalizeAdminType(user),
    requiredCapability,
    resolvedCapabilities: capabilitiesFor(user),
    path: req.originalUrl || req.path,
    method: req.method,
  });
}

const requireCapability = (capability, message) => async (req, res, next) => {
  if (!req.user) {
    return next(new UnauthorizedError('User not authenticated'));
  }

  try {
    const user = await sessionService.getCurrentUser(req.user.id);
    if (!hasCapability(user, capability)) {
      logDenial(req, user, capability);
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
  if (!req.user) return next(new UnauthorizedError('User not authenticated'));
  try {
    const user = await sessionService.getCurrentUser(req.user.id);
    if (normalizeAdminType(user) !== ADMIN_TYPES.SUPER) {
      logDenial(req, user, CAPABILITIES.ADMIN_MANAGE);
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
