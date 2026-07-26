const { db } = require('../config/database');
const { NotFoundError, ValidationError } = require('../utils/errors');
const { ADMIN_TYPES } = require('./adminCapabilityService');

class AdminManagementService {
  async listUsers(filters = {}) {
    const page = Math.max(1, Number(filters.page) || 1);
    const limit = Math.min(100, Math.max(1, Number(filters.limit) || 20));
    let base = db('users');
    if (filters.search) {
      const term = `%${String(filters.search).trim()}%`;
      base = base.where((builder) => builder
        .whereILike('name', term)
        .orWhereILike('email', term)
        .orWhereILike('mobile', term));
    }
    const count = await base.clone().count('id as count').first();
    const rows = await base.clone()
      .select('id', 'name', 'email', 'mobile', 'role', 'admin_type', 'created_at')
      .orderBy('created_at', 'desc')
      .offset((page - 1) * limit)
      .limit(limit);
    return {
      items: rows.map((row) => ({
        id: row.id,
        name: row.name,
        email: row.email,
        mobile: row.mobile,
        role: row.role,
        adminType: row.admin_type,
        createdAt: row.created_at,
      })),
      page,
      limit,
      total: Number(count?.count || 0),
    };
  }

  async updateAdminType(userId, changedBy, adminType, reason) {
    const normalizedAdminType = adminType === 'consumer' ? null : adminType;
    if (userId === changedBy && normalizedAdminType !== ADMIN_TYPES.SUPER) {
      throw new ValidationError('A Super Admin cannot remove their own Super Admin access');
    }
    const user = await db('users').where('id', userId).first();
    if (!user) throw new NotFoundError('User');
    const nextRole = normalizedAdminType ? 'admin' : 'consumer';
    await db.transaction(async (trx) => {
      await trx('users').where('id', userId).update({
        role: nextRole,
        admin_type: normalizedAdminType,
        updated_at: new Date(),
      });
      await trx('admin_role_history').insert({
        user_id: user.id,
        previous_role: user.role,
        previous_admin_type: user.admin_type,
        new_role: nextRole,
        new_admin_type: normalizedAdminType,
        changed_by: changedBy,
        reason: String(reason).trim(),
      });
      // Force the affected account to reauthenticate so stale refresh tokens
      // cannot mint access tokens with the previous capability set.
      await trx('refresh_tokens').where('user_id', userId).del();
    });
    return {
      id: user.id,
      name: user.name,
      email: user.email,
      mobile: user.mobile,
      role: nextRole,
      adminType: normalizedAdminType,
    };
  }
}

module.exports = new AdminManagementService();
