const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

function projectPath(relativePath) {
  return path.join(__dirname, '..', relativePath);
}

function makeQuery(table, fixture) {
  const conditions = [];
  let operation = 'select';
  let insertData = null;
  let returningColumn = null;
  let firstOnly = false;

  function matches(row) {
    return conditions.every((condition) => row[condition.column] === condition.value);
  }

  const query = {
    where(column, value) {
      conditions.push({ column, value });
      return query;
    },
    first() {
      firstOnly = true;
      return query;
    },
    insert(data) {
      operation = 'insert';
      insertData = data;
      return query;
    },
    returning(column) {
      returningColumn = column;
      return query;
    },
    execute() {
      const rows = fixture[table];

      if (operation === 'insert') {
        const row = {
          id: `event-${rows.length + 1}`,
          created_at: new Date('2099-01-01T00:00:00.000Z').toISOString(),
          updated_at: new Date('2099-01-01T00:00:00.000Z').toISOString(),
          ...insertData,
        };
        rows.push(row);
        return returningColumn === '*' ? [row] : [{ [returningColumn]: row[returningColumn] }];
      }

      const result = rows.filter(matches);
      return firstOnly ? result[0] : result;
    },
    then(resolve, reject) {
      return Promise.resolve()
        .then(() => query.execute())
        .then(resolve, reject);
    },
  };

  return query;
}

function loadEventServiceWithFixture(fixture, storageMock = {}) {
  const databasePath = projectPath('src/config/database.js');
  const storagePath = projectPath('src/services/eventImageStorageService.js');
  const eventServicePath = projectPath('src/services/eventService.js');

  delete require.cache[databasePath];
  delete require.cache[storagePath];
  delete require.cache[eventServicePath];

  require.cache[databasePath] = {
    id: databasePath,
    filename: databasePath,
    loaded: true,
    exports: {
      db: (table) => makeQuery(table, fixture),
      testConnection: async () => {},
      knexConfig: {},
    },
  };

  require.cache[storagePath] = {
    id: storagePath,
    filename: storagePath,
    loaded: true,
    exports: {
      uploadEventImage: storageMock.uploadEventImage || (async () => null),
      deleteEventImage: storageMock.deleteEventImage || (async () => {}),
    },
  };

  return require(eventServicePath);
}

test('createEvent keeps JSON/no-image compatibility and returns null imageUrl', async () => {
  const fixture = { events: [] };
  const eventService = loadEventServiceWithFixture(fixture);

  const event = await eventService.createEvent('admin-1', {
    header: 'Aarti',
    subHeader: 'Evening program',
    eventDate: '2099-01-01T18:00:00.000Z',
  });

  assert.equal(event.header, 'Aarti');
  assert.equal(event.imageUrl, null);
  assert.equal(Object.hasOwn(fixture.events[0], 'image_url'), false);
});

test('createEvent stores uploaded event image URL', async () => {
  const fixture = { events: [] };
  const uploads = [];
  const eventService = loadEventServiceWithFixture(fixture, {
    uploadEventImage: async (file) => {
      uploads.push(file);
      return { key: 'events/test.jpg', url: 'https://cdn.example.com/events/test.jpg' };
    },
  });

  const event = await eventService.createEvent(
    'admin-1',
    {
      header: 'Satsang',
      subHeader: 'Community event',
      eventDate: '2099-02-01T18:00:00.000Z',
    },
    { buffer: Buffer.from('image'), mimetype: 'image/jpeg', originalname: 'event.jpg' }
  );

  assert.equal(uploads.length, 1);
  assert.equal(event.imageUrl, 'https://cdn.example.com/events/test.jpg');
  assert.equal(fixture.events[0].image_url, 'https://cdn.example.com/events/test.jpg');
});
