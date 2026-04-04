const { db } = require('../config/database');
const { NotFoundError } = require('../utils/errors');
const authService = require('./authService');

class UserService {
  /**
   * Get user by ID
   */
  async getUserById(userId) {
    const user = await db('users').where('id', userId).first();

    if (!user) {
      throw new NotFoundError('User');
    }

    return {
      id: user.id,
      mobile: user.mobile,
      name: user.name,
      email: user.email,
      role: user.role,
      isSuperUser: authService.isSuperUser(user),
      createdAt: user.created_at,
    };
  }

  /**
   * Update user profile
   */
  async updateProfile(userId, data) {
    const updateData = {
      updated_at: new Date(),
    };

    if (data.name !== undefined) updateData.name = data.name;
    if (data.email !== undefined) updateData.email = data.email;

    const [user] = await db('users')
      .where('id', userId)
      .update(updateData)
      .returning('*');

    if (!user) {
      throw new NotFoundError('User');
    }

    return {
      id: user.id,
      mobile: user.mobile,
      name: user.name,
      email: user.email,
      role: user.role,
      isSuperUser: authService.isSuperUser(user),
      updatedAt: user.updated_at,
    };
  }
}

module.exports = new UserService();
