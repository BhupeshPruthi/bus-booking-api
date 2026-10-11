const chalisaService = require('../services/chalisaService');
const asyncHandler = require('../utils/asyncHandler');

const saveParticipant = asyncHandler(async (req, res) => {
  const result = await chalisaService.saveParticipant(req.chalisaIdentity, req.body.displayName);
  res.json({ success: true, data: result });
});

const recordReading = asyncHandler(async (req, res) => {
  const result = await chalisaService.recordReading(req.chalisaIdentity, req.body.clientRequestId, {
    startingCount: req.body.startingCount,
  });
  res.status(result.duplicate ? 200 : 201).json({ success: true, data: result });
});

const undoReading = asyncHandler(async (req, res) => {
  const result = await chalisaService.undoReading(req.chalisaIdentity, req.params.clientRequestId);
  res.json({ success: true, data: result });
});

const getMyStats = asyncHandler(async (req, res) => {
  const result = await chalisaService.getMyStats(req.chalisaIdentity, req.query);
  res.json({ success: true, data: result });
});

const getMarathonBoard = asyncHandler(async (req, res) => {
  const result = await chalisaService.getMarathonBoard(req.chalisaIdentity, req.query);
  res.json({ success: true, data: result });
});

const getSummary = asyncHandler(async (req, res) => {
  const result = await chalisaService.getSummary(req.chalisaIdentity);
  res.json({ success: true, data: result });
});

const listArchivedMarathons = asyncHandler(async (req, res) => {
  const result = await chalisaService.listArchivedMarathons(req.query);
  res.json({ success: true, data: result });
});

const scheduleMarathon = asyncHandler(async (req, res) => {
  const result = await chalisaService.scheduleMarathon(req.user.id, req.body);
  res.status(201).json({ success: true, data: result });
});

const updateMarathon = asyncHandler(async (req, res) => {
  const result = await chalisaService.updateMarathon(req.params.id, req.body);
  res.json({ success: true, data: result });
});

const cancelMarathon = asyncHandler(async (req, res) => {
  const result = await chalisaService.cancelMarathon(req.params.id);
  res.json({ success: true, data: result });
});

const endMarathon = asyncHandler(async (req, res) => {
  const result = await chalisaService.endMarathon(req.user.id, req.params.id, req.body);
  res.json({ success: true, data: result });
});

const restoreMarathon = asyncHandler(async (req, res) => {
  const result = await chalisaService.restoreMarathon(req.params.id);
  res.json({ success: true, data: result });
});

module.exports = {
  saveParticipant,
  recordReading,
  undoReading,
  getMyStats,
  getMarathonBoard,
  getSummary,
  listArchivedMarathons,
  scheduleMarathon,
  updateMarathon,
  cancelMarathon,
  endMarathon,
  restoreMarathon,
};
