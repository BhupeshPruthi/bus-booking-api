const config = require('../config');
const { ForbiddenError } = require('../utils/errors');

/**
 * Admin or superuser (SUPER_USER_MOBILE / SUPER_USER_EMAIL in env).
 * Used for all /api/admin/* routes after authenticate.
 */
const adminOrSuperUser = (req, res, next) => {
  if (!req.user) {
    return next(new ForbiddenError('User not authenticated'));
  }

  const isSuperUser =
    (config.superUserMobile && req.user.mobile === config.superUserMobile) ||
    (config.superUserEmail &&
      req.user.email &&
      String(req.user.email).toLowerCase() === config.superUserEmail);
  const isAdmin = req.user.role === 'admin';

  if (!isAdmin && !isSuperUser) {
    return next(new ForbiddenError('Access denied. Admin or superuser required.'));
  }

  next();
};

module.exports = { adminOrSuperUser };
