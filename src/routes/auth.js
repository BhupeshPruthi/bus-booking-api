const express = require('express');
const authController = require('../controllers/authController');
const validate = require('../middlewares/validate');
const { googleSignInSchema, refreshTokenSchema } = require('../validators/schemas');

const router = express.Router();

/**
 * @route POST /api/auth/google
 * @desc Sign in with Google ID token (new users are consumers)
 * @access Public
 */
router.post('/google', validate(googleSignInSchema), authController.signInWithGoogle);

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

module.exports = router;
