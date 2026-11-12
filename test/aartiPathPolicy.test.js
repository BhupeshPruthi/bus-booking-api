const test = require('node:test');
const assert = require('node:assert/strict');

const {
  ADMIN_TYPES,
  CAPABILITIES,
  hasCapability,
} = require('../src/services/adminCapabilityService');
const {
  normalizeTitle,
  isPdfBuffer,
  contentDisposition,
  formatItem,
} = require('../src/services/aartiPathService').helpers;
const {
  createAartiPathSchema,
  aartiPathParamsSchema,
} = require('../src/validators/schemas');

test('Super Admin and Bus Admin can manage Aarti & Path; Stay Admin and users cannot', () => {
  const can = (user) => hasCapability(user, CAPABILITIES.AARTI_PATH_MANAGE);
  assert.equal(can({ role: 'admin', adminType: ADMIN_TYPES.SUPER }), true);
  assert.equal(can({ role: 'consumer', isSuperUser: true }), true);
  assert.equal(can({ role: 'admin', adminType: ADMIN_TYPES.BUS }), true);
  assert.equal(can({ role: 'admin', adminType: ADMIN_TYPES.STAY }), false);
  assert.equal(can({ role: 'consumer' }), false);
  assert.equal(can(null), false);
});

test('normalizeTitle trims and collapses whitespace', () => {
  assert.equal(normalizeTitle('  Bajrang   Baan \n'), 'Bajrang Baan');
  assert.equal(normalizeTitle('हनुमान  चालीसा'), 'हनुमान चालीसा');
  assert.equal(normalizeTitle(undefined), '');
});

test('isPdfBuffer accepts real PDF headers and rejects other files', () => {
  assert.equal(isPdfBuffer(Buffer.from('%PDF-1.7\n%âãÏÓ\n1 0 obj')), true);
  // Some writers put a few junk bytes before the header; it only has to be in the first 1 KB.
  assert.equal(isPdfBuffer(Buffer.concat([Buffer.alloc(16, 0x20), Buffer.from('%PDF-1.4')])), true);
  assert.equal(isPdfBuffer(Buffer.concat([Buffer.alloc(2048, 0x20), Buffer.from('%PDF-1.4')])), false);
  assert.equal(isPdfBuffer(Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10])), false); // JPEG
  assert.equal(isPdfBuffer(Buffer.from('%PDF')), false);
  assert.equal(isPdfBuffer(null), false);
});

test('contentDisposition keeps an ASCII fallback and the full Hindi title', () => {
  assert.equal(
    contentDisposition('Hanuman Chalisa (English)'),
    "inline; filename=\"Hanuman Chalisa (English).pdf\"; filename*=UTF-8''Hanuman%20Chalisa%20(English).pdf"
  );
  const hindi = contentDisposition('हनुमान "चालीसा"');
  assert.equal(
    hindi,
    `inline; filename="aarti-path.pdf"; filename*=UTF-8''${encodeURIComponent('हनुमान "चालीसा"')}.pdf`
  );
});

test('formatItem returns the API shape with a PDF URL on the request host', () => {
  const row = {
    id: '7d1f4f0e-3c55-4c1e-9d55-9b0b2f6f8a11',
    title: 'Hanuman Ji Ki Aarti',
    page_count: 1,
    size_bytes: '683008',
    created_at: '2026-11-11T10:00:00.000Z',
    file_key: 'aarti-path/x.pdf',
  };
  assert.deepEqual(formatItem(row, 'https://api.example.com/api/'), {
    id: row.id,
    title: 'Hanuman Ji Ki Aarti',
    pageCount: 1,
    sizeBytes: 683008,
    pdfUrl: `https://api.example.com/api/aarti-path/${row.id}/pdf`,
    createdAt: '2026-11-11T10:00:00.000Z',
  });
  assert.equal(formatItem({ ...row, page_count: null }, null).pageCount, null);
  assert.equal(formatItem(row, null).pdfUrl, null);
});

test('create schema takes multipart text fields', () => {
  const ok = createAartiPathSchema.validate({ title: '  Bajrang Baan ', pageCount: '2' });
  assert.equal(ok.error, undefined);
  assert.deepEqual(ok.value, { title: 'Bajrang Baan', pageCount: 2 });

  const noCount = createAartiPathSchema.validate({ title: 'Hanuman Bahuk', pageCount: '' });
  assert.equal(noCount.error, undefined);
  assert.equal(noCount.value.pageCount, undefined);

  assert.ok(createAartiPathSchema.validate({ title: '   ' }).error);
  assert.ok(createAartiPathSchema.validate({ title: 'x'.repeat(81) }).error);
  assert.ok(createAartiPathSchema.validate({ title: 'Ok', pageCount: '0' }).error);
  assert.ok(aartiPathParamsSchema.validate({ id: 'not-a-uuid' }).error);
});
