const service = require('../services/adminManagementService');
const asyncHandler = require('../utils/asyncHandler');

const listUsers = asyncHandler(async (req, res) => {
  res.json({ success: true, data: await service.listUsers(req.query) });
});

const updateAdminType = asyncHandler(async (req, res) => {
  res.json({
    success: true,
    data: await service.updateAdminType(
      req.params.id,
      req.user.id,
      req.body.adminType,
      req.body.reason
    ),
  });
});

module.exports = { listUsers, updateAdminType };
