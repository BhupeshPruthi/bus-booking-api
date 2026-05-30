function firstHeaderValue(value) {
  return String(value || '').split(',')[0].trim();
}

function getRequestBaseUrl(req) {
  const protocol = firstHeaderValue(req.get('x-forwarded-proto')) || req.protocol || 'http';
  const host = firstHeaderValue(req.get('x-forwarded-host')) || req.get('host');
  return `${protocol}://${host}`;
}

function getApiBaseUrl(req) {
  return `${getRequestBaseUrl(req).replace(/\/+$/, '')}/api`;
}

module.exports = {
  getApiBaseUrl,
  getRequestBaseUrl,
};
