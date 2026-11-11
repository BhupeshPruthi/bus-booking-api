const test = require('node:test');
const assert = require('node:assert/strict');
const express = require('express');

const pageRoutes = require('../src/routes/pages');

async function withServer(run) {
  const app = express();
  app.use(pageRoutes);
  const server = app.listen(0);
  try {
    await run(`http://127.0.0.1:${server.address().port}`);
  } finally {
    server.close();
  }
}

test('serves the privacy policy as HTML at /privacy and /privacy-policy', async () => {
  await withServer(async (base) => {
    for (const path of ['/privacy', '/privacy-policy']) {
      const response = await fetch(`${base}${path}`);
      assert.equal(response.status, 200);
      assert.match(response.headers.get('content-type'), /text\/html/);
      const body = await response.text();
      assert.match(body, /<h1>Privacy Policy<\/h1>/);
      assert.match(body, /bolguru03@gmail\.com/);
    }
  });
});

test('serves the support page as HTML at /support', async () => {
  await withServer(async (base) => {
    const response = await fetch(`${base}/support`);
    assert.equal(response.status, 200);
    assert.match(response.headers.get('content-type'), /text\/html/);
    assert.match(await response.text(), /<h1>Support<\/h1>/);
  });
});
