const test = require('node:test');
const assert = require('node:assert/strict');

const {
  createFeedbackSchema,
  feedbackListSchema,
} = require('../src/validators/schemas');
const { formatFeedback } = require('../src/services/feedbackService');
const {
  list: listHelpContacts,
  formatHelpContact,
} = require('../src/services/helpContactService');
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

test('Help contacts expose only the fields needed by the app', () => {
  assert.deepEqual(formatHelpContact({
    id: 'contact-id',
    category_code: 'emergency',
    title: 'Emergency',
    contact_name: 'Bhupesh Pruthi',
    phone: '9513333839',
    display_order: 4,
    is_active: true,
  }), {
    id: 'contact-id',
    code: 'emergency',
    title: 'Emergency',
    contactName: 'Bhupesh Pruthi',
    phone: '9513333839',
  });
});

test('Help contacts are loaded from active database rows in display order', async () => {
  const calls = [];
  const rows = [{
    id: 'contact-id',
    category_code: 'bus',
    title: 'Bus booking and services',
    contact_name: 'Bhupesh Pruthi',
    phone: '9513333839',
  }];
  const query = {
    select(...columns) {
      calls.push(['select', columns]);
      return this;
    },
    where(column, value) {
      calls.push(['where', column, value]);
      return this;
    },
    async orderBy(column, direction) {
      calls.push(['orderBy', column, direction]);
      return rows;
    },
  };
  const database = (tableName) => {
    calls.push(['table', tableName]);
    return query;
  };

  const result = await listHelpContacts(database);

  assert.deepEqual(calls[0], ['table', 'help_contacts']);
  assert.ok(calls.some((call) =>
    call[0] === 'where' && call[1] === 'is_active' && call[2] === true
  ));
  assert.ok(calls.some((call) =>
    call[0] === 'orderBy' && call[1] === 'display_order' && call[2] === 'asc'
  ));
  assert.equal(result[0].code, 'bus');
});
