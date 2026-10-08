const express = require('express');
const authController = require('../controllers/authController');
const { authenticate } = require('../middlewares/authenticate');
const validate = require('../middlewares/validate');
const { googleSignInSchema, appleSignInSchema, refreshTokenSchema } = require('../validators/schemas');

const router = express.Router();

/**
 * @route POST /api/auth/google
 * @desc Sign in with Google ID token (new users are consumers)
 * @access Public
 */
router.post('/google', validate(googleSignInSchema), authController.signInWithGoogle);

/**
 * @route POST /api/auth/apple
 * @desc Sign in with an Apple identity token (iOS)
 * @access Public
 */
router.post('/apple', validate(appleSignInSchema), authController.signInWithApple);

/**
 * @route POST /api/auth/refresh-token
 * @desc Refresh access token using refresh token
 * @access Public
 */
router.post('/refresh-token', validate(refreshTokenSchema), authController.refreshToken);

/**
 * @route POST /api/auth/logout
 * @desc Logout and invalidate refresh token
 * @access Public
 */
router.post('/logout', validate(refreshTokenSchema), authController.logout);

/** Account deletion is required for App Store account-based apps. */
router.delete('/account', authenticate, authController.deleteAccount);

/**
 * @route GET /api/auth/session
 * @desc Return the database-resolved role, admin type, and capabilities
 * @access Authenticated
 */
router.get('/session', authenticate, authController.session);

module.exports = router;
