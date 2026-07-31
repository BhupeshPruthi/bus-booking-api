const feedbackService = require('../services/feedbackService');
const asyncHandler = require('../utils/asyncHandler');

const create = asyncHandler(async (req, res) => {
  const result = await feedbackService.create(req.user.id, req.body.message);
  res.status(201).json({ success: true, data: result });
});

const list = asyncHandler(async (req, res) => {
  const result = await feedbackService.list(req.query);
  res.json({ success: true, data: result });
});

module.exports = {
  create,
  list,
};
