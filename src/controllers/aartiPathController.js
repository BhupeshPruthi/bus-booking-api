const aartiPathService = require('../services/aartiPathService');
const asyncHandler = require('../utils/asyncHandler');
const { getApiBaseUrl } = require('../utils/requestBaseUrl');

const list = asyncHandler(async (req, res) => {
  const result = await aartiPathService.list({ apiBaseUrl: getApiBaseUrl(req) });
  res.json({ success: true, data: result });
});

const getPdf = async (req, res, next) => {
  try {
    const pdf = await aartiPathService.getPdf(req.params.id);
    res.set({
      'Content-Type': 'application/pdf',
      'Cache-Control': pdf.cacheControl,
      'Content-Disposition': pdf.contentDisposition,
    });
    if (Number.isFinite(pdf.contentLength)) {
      res.set('Content-Length', String(pdf.contentLength));
    }

    if (pdf.body && typeof pdf.body.pipe === 'function') {
      pdf.body.on('error', next);
      pdf.body.pipe(res);
      return;
    }

    const chunks = [];
    for await (const chunk of pdf.body) {
      chunks.push(Buffer.from(chunk));
    }
    res.send(Buffer.concat(chunks));
  } catch (error) {
    next(error);
  }
};

const create = asyncHandler(async (req, res) => {
  const result = await aartiPathService.create(req.user.id, req.body, req.file || null, {
    apiBaseUrl: getApiBaseUrl(req),
  });
  res.status(201).json({ success: true, data: result });
});

const remove = asyncHandler(async (req, res) => {
  const result = await aartiPathService.remove(req.params.id, req.user.id);
  res.json({ success: true, data: result });
});

module.exports = {
  list,
  getPdf,
  create,
  remove,
};
