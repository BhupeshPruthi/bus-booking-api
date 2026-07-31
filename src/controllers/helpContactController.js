const helpContactService = require('../services/helpContactService');
const asyncHandler = require('../utils/asyncHandler');

const list = asyncHandler(async (req, res) => {
  const result = await helpContactService.list();
  res.json({ success: true, data: result });
});

module.exports = { list };
