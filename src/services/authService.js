const jwt = require('jsonwebtoken');
const { v4: uuidv4 } = require('uuid');
const { OAuth2Client } = require('google-auth-library');
const crypto = require('crypto');
const { db } = require('../config/database');
const config = require('../config');
const { UnauthorizedError, ValidationError } = require('../utils/errors');
const logger = require('../utils/logger');
const {
  normalizeAdminType,
  capabilitiesFor,
} = require('./adminCapabilityService');

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
  async verifyAppleIdentityToken(identityToken) {
    const audiences = config.apple.clientIds || [];
    if (audiences.length === 0) throw new ValidationError('Apple Sign-In is not configured (APPLE_CLIENT_ID)');
    const [encodedHeader, encodedPayload, encodedSignature] = String(identityToken).split('.');
    if (!encodedHeader || !encodedPayload || !encodedSignature) throw new UnauthorizedError('Invalid Apple sign-in token');
    let header;
    let payload;
    try {
      header = JSON.parse(Buffer.from(encodedHeader, 'base64url').toString('utf8'));
      payload = JSON.parse(Buffer.from(encodedPayload, 'base64url').toString('utf8'));
    } catch {
      throw new UnauthorizedError('Invalid Apple sign-in token');
    }
    if (header.alg !== 'RS256' || !header.kid) throw new UnauthorizedError('Invalid Apple sign-in token');
    let response;
    try {
      response = await fetch('https://appleid.apple.com/auth/keys', { signal: AbortSignal.timeout(5000) });
    } catch {
      throw new UnauthorizedError('Unable to verify Apple sign-in token');
    }
    if (!response.ok) throw new UnauthorizedError('Unable to verify Apple sign-in token');
    const { keys = [] } = await response.json();
    const jwk = keys.find((key) => key.kid === header.kid);
    if (!jwk) throw new UnauthorizedError('Invalid Apple sign-in token');
    const valid = crypto.verify('RSA-SHA256', Buffer.from(`${encodedHeader}.${encodedPayload}`), crypto.createPublicKey({ key: jwk, format: 'jwk' }), Buffer.from(encodedSignature, 'base64url'));
    if (!valid || payload.iss !== 'https://appleid.apple.com' || !audiences.includes(payload.aud) || !payload.sub || !payload.exp || payload.exp * 1000 <= Date.now()) throw new UnauthorizedError('Invalid Apple sign-in token');
    return { sub: String(payload.sub), email: payload.email ? String(payload.email).toLowerCase().trim() : null };
  }

  async signInWithApple(identityToken) {
    const { sub, email } = await this.verifyAppleIdentityToken(identityToken);
    let user = await db('users').where('apple_sub', sub).whereNull('deleted_at').first();
    if (!user && email) user = await db('users').where('email', email).whereNull('deleted_at').first();
    if (!user) {
      const [created] = await db('users').insert({ apple_sub: sub, email, name: email ? email.split('@')[0] : 'Balaji Sevak User', mobile: null, role: 'consumer' }).returning('*');
      user = created;
    } else if (user.apple_sub !== sub) {
      await db('users').where('id', user.id).update({ apple_sub: sub, updated_at: db.fn.now() });
      user = await db('users').where('id', user.id).first();
    }
    const accessToken = this.generateAccessToken(user);
    const refreshToken = await this.generateRefreshToken(user.id);
    return { accessToken, refreshToken, user: { id: user.id, mobile: user.mobile, email: user.email, name: user.name, role: user.role, isSuperUser: this.isSuperUser(user), adminType: normalizeAdminType({ ...user, isSuperUser: this.isSuperUser(user) }), capabilities: capabilitiesFor({ ...user, isSuperUser: this.isSuperUser(user) }), isNewUser: false } };
  }

  async deleteAccount(userId) {
    const deletedEmail = `deleted+${userId}@invalid.balajisevak`;
    await db.transaction(async (trx) => {
      await trx('refresh_tokens').where('user_id', userId).del();
      await trx('users').where('id', userId).update({ name: 'Deleted User', email: deletedEmail, mobile: null, google_sub: null, apple_sub: null, deleted_at: trx.fn.now(), updated_at: trx.fn.now() });
    });
  }
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
        adminType: normalizeAdminType({
          ...user,
          isSuperUser: this.isSuperUser(user),
        }),
        capabilities: capabilitiesFor({
          ...user,
          isSuperUser: this.isSuperUser(user),
        }),
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
        adminType: normalizeAdminType({
          ...user,
          isSuperUser: this.isSuperUser(user),
        }),
        capabilities: capabilitiesFor({
          ...user,
          isSuperUser: this.isSuperUser(user),
        }),
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
