const test = require('node:test');
const assert = require('node:assert/strict');

const {
  createFeedbackSchema,
  feedbackListSchema,
} = require('../src/validators/schemas');
const { formatFeedback } = require('../src/services/feedbackService');
const { normalizeAdminType, ADMIN_TYPES } = require('../src/services/adminCapabilityService');

test('feedback requires a non-empty message and trims it', () => {
  const valid = createFeedbackSchema.validate({ message: '  Please add more buses.  ' });
  assert.equal(valid.error, undefined);
  assert.equal(valid.value.message, 'Please add more buses.');

  assert.match(
    createFeedbackSchema.validate({ message: '   ' }).error?.message || '',
    /not allowed to be empty/
  );
  assert.match(
    createFeedbackSchema.validate({ message: 'x'.repeat(2001) }).error?.message || '',
    /less than or equal to 2000 characters/
  );
});

test('feedback list pagination has safe defaults and limits', () => {
  assert.deepEqual(feedbackListSchema.validate({}).value, { page: 1, limit: 20 });
  assert.ok(feedbackListSchema.validate({ page: 0 }).error);
  assert.ok(feedbackListSchema.validate({ limit: 101 }).error);
});

test('admin feedback includes the submitter contact details', () => {
  assert.deepEqual(formatFeedback({
    id: 'feedback-id',
    user_id: 'user-id',
    message: 'Helpful suggestion',
    created_at: '2026-08-02T10:00:00.000Z',
    user_name: 'Guest User',
    user_email: 'guest@example.com',
    user_phone: '9999999999',
  }), {
    id: 'feedback-id',
    message: 'Helpful suggestion',
    createdAt: '2026-08-02T10:00:00.000Z',
    submittedBy: {
      id: 'user-id',
      name: 'Guest User',
      email: 'guest@example.com',
      phone: '9999999999',
    },
  });
});

test('all existing admin types qualify for the shared feedback list', () => {
  assert.equal(normalizeAdminType({ role: 'admin' }), ADMIN_TYPES.BUS);
  assert.equal(
    normalizeAdminType({ role: 'admin', adminType: ADMIN_TYPES.STAY }),
    ADMIN_TYPES.STAY
  );
  assert.equal(
    normalizeAdminType({ role: 'admin', adminType: ADMIN_TYPES.SUPER }),
    ADMIN_TYPES.SUPER
  );
  assert.equal(normalizeAdminType({ role: 'consumer' }), null);
});
