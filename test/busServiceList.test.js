const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

function projectPath(relativePath) {
  return path.join(__dirname, '..', relativePath);
}

function loadBusServiceWithQueryRecorder(calls) {
  const databasePath = projectPath('src/config/database.js');
  const busServicePath = projectPath('src/services/busService.js');

  const query = {};
  for (const method of ['join', 'leftJoin', 'select', 'where', 'whereRaw', 'orderBy', 'offset', 'limit']) {
    query[method] = (...args) => {
      calls.push({ method, args });
      return query;
    };
  }
  query.then = (resolve, reject) => Promise.resolve([]).then(resolve, reject);

  const db = (table) => {
    assert.equal(table, 'buses');
    calls.push({ method: 'from', args: [table] });
    return query;
  };

  delete require.cache[databasePath];
  delete require.cache[busServicePath];
  require.cache[databasePath] = {
    id: databasePath,
    filename: databasePath,
    loaded: true,
    exports: { db, testConnection: async () => {}, knexConfig: {} },
  };

  return require(busServicePath);
}

test('admin scheduled-bus filtering happens before pagination', async () => {
  const calls = [];
  const before = Date.now();
  const busService = loadBusServiceWithQueryRecorder(calls);

  const result = await busService.getAllBuses({ status: 'scheduled' });

  assert.deepEqual(result, []);

  const activeFilterIndex = calls.findIndex(({ method }) => method === 'whereRaw');
  const limitIndex = calls.findIndex(({ method }) => method === 'limit');
  assert.ok(activeFilterIndex >= 0, 'expected the active-bus cutoff filter');
  assert.ok(activeFilterIndex < limitIndex, 'active-bus cutoff must run before pagination');

  const [sql, bindings] = calls[activeFilterIndex].args;
  assert.match(sql, /GREATEST/);
  assert.match(sql, /return_buses\.arrival_time/);
  assert.equal(bindings.length, 1);

  const cutoff = bindings[0];
  const expectedCutoff = before - 24 * 60 * 60 * 1000;
  assert.ok(cutoff instanceof Date);
  assert.ok(cutoff.getTime() >= expectedCutoff);
  assert.ok(cutoff.getTime() <= Date.now() - 24 * 60 * 60 * 1000);
});

test('admin queries without scheduled status retain historical-list behavior', async () => {
  const calls = [];
  const busService = loadBusServiceWithQueryRecorder(calls);

  await busService.getAllBuses({ status: 'cancelled' });

  assert.equal(calls.some(({ method }) => method === 'whereRaw'), false);
});
