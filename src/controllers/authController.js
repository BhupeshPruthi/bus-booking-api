const authService = require('../services/authService');
const asyncHandler = require('../utils/asyncHandler');

/**
 * POST /api/auth/google
 */
const signInWithGoogle = asyncHandler(async (req, res) => {
  const { idToken } = req.body;
  const result = await authService.signInWithGoogle(idToken);

  res.json({
    success: true,
    data: result,
  });
});

/**
 * Refresh access token
 * POST /api/auth/refresh-token
 */
const refreshToken = asyncHandler(async (req, res) => {
  const { refreshToken } = req.body;
  const result = await authService.refreshAccessToken(refreshToken);

  res.json({
    success: true,
    data: result,
  });
});

/**
 * Logout - invalidate refresh token
 * POST /api/auth/logout
 */
const logout = asyncHandler(async (req, res) => {
  const { refreshToken } = req.body;
  const result = await authService.logout(refreshToken);

  res.json({
    success: true,
    data: result,
  });
});

module.exports = {
  signInWithGoogle,
  refreshToken,
  logout,
};
