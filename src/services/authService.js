const jwt = require('jsonwebtoken');
const { v4: uuidv4 } = require('uuid');
const { OAuth2Client } = require('google-auth-library');
const { db } = require('../config/database');
const config = require('../config');
const { UnauthorizedError, ValidationError } = require('../utils/errors');
const logger = require('../utils/logger');

function maskGoogleClientId(value) {
  if (!value) return undefined;
  const str = String(value);
  if (str.length <= 18) return '<redacted>';
  return `${str.slice(0, 12)}...${str.slice(-31)}`;
}

function decodeJwtPayload(idToken) {
  const parts = String(idToken || '').split('.');
  if (parts.length < 2) return null;
  try {
    return JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
  } catch (_e) {
    return null;
  }
}

class AuthService {
  /**
   * Verify Google ID token and return Google profile fields
   */
  async verifyGoogleIdToken(idToken) {
    const allowedClientIds = config.google.clientIds || [];
    if (allowedClientIds.length === 0) {
      throw new ValidationError('Google Sign-In is not configured (GOOGLE_CLIENT_ID)');
    }
    const client = new OAuth2Client(allowedClientIds[0]);
    let ticket;
    try {
      ticket = await client.verifyIdToken({
        idToken,
        audience: allowedClientIds,
      });
    } catch (e) {
      const payload = decodeJwtPayload(idToken);
      logger.warn('Google ID token verification failed', {
        message: e.message,
        expectedAudiences: allowedClientIds.map(maskGoogleClientId),
        token: payload
          ? {
              aud: maskGoogleClientId(payload.aud),
              azp: maskGoogleClientId(payload.azp),
              iss: payload.iss,
              emailVerified: payload.email_verified,
              exp: payload.exp ? new Date(payload.exp * 1000).toISOString() : undefined,
            }
          : undefined,
      });
      throw new UnauthorizedError('Invalid Google sign-in token');
    }
    const payload = ticket.getPayload();
    if (!payload) {
      throw new UnauthorizedError('Invalid Google sign-in token');
    }
    const email = payload.email;
    if (!email) {
      throw new ValidationError('Your Google account must have an email address');
    }
    const emailVerified = payload.email_verified === true || payload.email_verified === 'true';
    if (!emailVerified) {
      throw new ValidationError('Please verify your Google email before signing in');
    }
    return {
      sub: payload.sub,
      email: String(email).toLowerCase().trim(),
      name: payload.name ? String(payload.name).trim() : '',
      picture: payload.picture || null,
    };
  }

  /**
   * Sign in with Google (consumers by default; operator access via SUPER_USER_* env or role admin in DB).
   */
  async signInWithGoogle(idToken) {
    const { sub, email, name } = await this.verifyGoogleIdToken(idToken);

    let user = await db('users').where('google_sub', sub).first();

    if (!user) {
      user = await db('users').where('email', email).first();
      if (user) {
        await db('users').where('id', user.id).update({
          google_sub: sub,
          updated_at: db.fn.now(),
        });
        user = await db('users').where('id', user.id).first();
      }
    }

    let isNewUser = false;

    if (!user) {
      isNewUser = true;
      const displayName = name || email.split('@')[0];
      const [newUser] = await db('users')
        .insert({
          google_sub: sub,
          email,
          name: displayName,
          mobile: null,
          role: 'consumer',
        })
        .returning('*');
      user = newUser;
      logger.info(`New Google user registered: ${email}`);
    } else {
      const updates = {};
      if (name && name !== user.name) {
        updates.name = name;
      }
      if (user.google_sub !== sub) {
        updates.google_sub = sub;
      }
      if (Object.keys(updates).length > 0) {
        updates.updated_at = db.fn.now();
        await db('users').where('id', user.id).update(updates);
        user = await db('users').where('id', user.id).first();
      }
    }

    const accessToken = this.generateAccessToken(user);
    const refreshToken = await this.generateRefreshToken(user.id);

    return {
      accessToken,
      refreshToken,
      user: {
        id: user.id,
        mobile: user.mobile,
        email: user.email,
        name: user.name,
        role: user.role,
        isSuperUser: this.isSuperUser(user),
        isNewUser,
      },
    };
  }

  /**
   * Generate JWT access token
   */
  generateAccessToken(user) {
    return jwt.sign(
      {
        id: user.id,
        mobile: user.mobile,
        email: user.email,
        role: user.role,
        isSuperUser: this.isSuperUser(user),
      },
      config.jwt.secret,
      { expiresIn: config.jwt.expiresIn }
    );
  }

  /**
   * Generate refresh token
   */
  async generateRefreshToken(userId) {
    const token = uuidv4();
    const expiresAt = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000); // 30 days

    await db('refresh_tokens').insert({
      token,
      user_id: userId,
      expires_at: expiresAt,
    });

    return token;
  }

  /**
   * Refresh access token using refresh token
   */
  async refreshAccessToken(refreshToken) {
    const tokenRecord = await db('refresh_tokens')
      .where('token', refreshToken)
      .where('expires_at', '>', new Date())
      .first();

    if (!tokenRecord) {
      throw new UnauthorizedError('Invalid or expired refresh token');
    }

    const user = await db('users').where('id', tokenRecord.user_id).first();

    if (!user) {
      throw new UnauthorizedError('User not found');
    }

    await db('refresh_tokens').where('id', tokenRecord.id).del();

    const accessToken = this.generateAccessToken(user);
    const newRefreshToken = await this.generateRefreshToken(user.id);

    return {
      accessToken,
      refreshToken: newRefreshToken,
    };
  }

  /**
   * Logout - invalidate refresh token
   */
  async logout(refreshToken) {
    await db('refresh_tokens').where('token', refreshToken).del();
    return { message: 'Logged out successfully' };
  }

  /**
   * Check if a user is the superuser (by legacy mobile or Google email)
   */
  isSuperUser(user) {
    const byMobile = config.superUserMobile && user.mobile === config.superUserMobile;
    const byEmail =
      config.superUserEmail &&
      user.email &&
      String(user.email).toLowerCase() === config.superUserEmail;
    return !!(byMobile || byEmail);
  }
}

module.exports = new AuthService();
