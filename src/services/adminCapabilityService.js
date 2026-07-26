const ADMIN_TYPES = Object.freeze({
  BUS: 'bus_admin',
  STAY: 'stay_admin',
  SUPER: 'super_admin',
});

const CAPABILITIES = Object.freeze({
  BUS_MANAGE: 'bus.manage',
  POOJA_MANAGE: 'pooja.manage',
  STAY_MANAGE: 'stay.manage',
  EVENT_MANAGE: 'event.manage',
  ADMIN_MANAGE: 'admin.manage',
});

function normalizeAdminType(user) {
  if (!user) return null;
  if (user.isSuperUser === true) {
    return ADMIN_TYPES.SUPER;
  }

  const configuredType = user.adminType || user.admin_type;
  if (user.role === 'admin' && Object.values(ADMIN_TYPES).includes(configuredType)) {
    return configuredType;
  }

  // Backward compatibility for existing DB rows and access tokens created before
  // the scoped-admin migration.
  if (user.role === 'admin' || user.role === 'superuser') {
    return user.role === 'superuser' ? ADMIN_TYPES.SUPER : ADMIN_TYPES.BUS;
  }
  return null;
}

function capabilitiesFor(user) {
  switch (normalizeAdminType(user)) {
    case ADMIN_TYPES.SUPER:
      return Object.values(CAPABILITIES);
    case ADMIN_TYPES.BUS:
      return [
        CAPABILITIES.BUS_MANAGE,
        CAPABILITIES.POOJA_MANAGE,
        CAPABILITIES.EVENT_MANAGE,
      ];
    case ADMIN_TYPES.STAY:
      return [CAPABILITIES.STAY_MANAGE];
    default:
      return [];
  }
}

function hasCapability(user, capability) {
  return capabilitiesFor(user).includes(capability);
}

module.exports = {
  ADMIN_TYPES,
  CAPABILITIES,
  normalizeAdminType,
  capabilitiesFor,
  hasCapability,
};
