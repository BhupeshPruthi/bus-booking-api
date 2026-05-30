const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

function projectPath(relativePath) {
  return path.join(__dirname, '..', relativePath);
}

function makeQuery(table, fixture) {
  const conditions = [];
  const selectedColumns = [];
  let operation = 'select';
  let updateData = null;
  let returningColumn = null;
  let firstOnly = false;

  function rowsForTable() {
    return fixture[table];
  }

  function matches(row) {
    return conditions.every((condition) => {
      const value = row[condition.column];
      if (condition.type === 'eq') return value === condition.value;
      if (condition.type === 'in') return condition.values.includes(value);
      return true;
    });
  }

  function project(row) {
    if (selectedColumns.length === 0) return row;
    return selectedColumns.reduce((acc, column) => {
      acc[column] = row[column];
      return acc;
    }, {});
  }

  const query = {
    where(column, value) {
      if (typeof column === 'object') {
        Object.entries(column).forEach(([key, objectValue]) => {
          conditions.push({ type: 'eq', column: key, value: objectValue });
        });
      } else {
        conditions.push({ type: 'eq', column, value });
      }
      return query;
    },
    whereIn(column, values) {
      conditions.push({ type: 'in', column, values });
      return query;
    },
    select(...columns) {
      selectedColumns.push(...columns.flat());
      return query;
    },
    forUpdate() {
      return query;
    },
    first() {
      firstOnly = true;
      return query;
    },
    update(data) {
      operation = 'update';
      updateData = data;
      return query;
    },
    del() {
      operation = 'delete';
      return query;
    },
    returning(column) {
      returningColumn = column;
      return query;
    },
    execute() {
      const rows = rowsForTable();
      const matchingRows = rows.filter(matches);

      if (operation === 'update') {
        matchingRows.forEach((row) => Object.assign(row, updateData));
        if (returningColumn) {
          return matchingRows.map((row) => ({ [returningColumn]: row[returningColumn] }));
        }
        return matchingRows.length;
      }

      if (operation === 'delete') {
        const remainingRows = rows.filter((row) => !matches(row));
        fixture[table] = remainingRows;
        return matchingRows.length;
      }

      const result = matchingRows.map(project);
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

function makeDb(fixture) {
  const db = () => {
    throw new Error('Only transaction-scoped access is expected in this test');
  };
  db.transaction = async (callback) => {
    const trx = (table) => makeQuery(table, fixture);
    return callback(trx);
  };
  return db;
}

function loadBusServiceWithFixture(fixture) {
  const databasePath = projectPath('src/config/database.js');
  const busServicePath = projectPath('src/services/busService.js');

  delete require.cache[databasePath];
  delete require.cache[busServicePath];
  require.cache[databasePath] = {
    id: databasePath,
    filename: databasePath,
    loaded: true,
    exports: { db: makeDb(fixture), testConnection: async () => {}, knexConfig: {} },
  };

  return require(busServicePath);
}

test('deleteBus physically deletes empty future round trips', async () => {
  const fixture = {
    buses: [
      {
        id: 'outbound',
        trip_type: 'round_trip',
        return_bus_id: 'return',
        departure_time: '2099-01-01T10:00:00.000Z',
      },
      {
        id: 'return',
        trip_type: 'round_trip',
        return_bus_id: 'outbound',
        departure_time: '2099-01-02T10:00:00.000Z',
      },
    ],
    bookings: [],
  };
  const busService = loadBusServiceWithFixture(fixture);

  const result = await busService.deleteBus('outbound', 'admin-user');

  assert.equal(result.mode, 'deleted');
  assert.deepEqual(result.busIds, ['outbound', 'return']);
  assert.deepEqual(fixture.buses, []);
});

test('deleteBus cancels future buses and maps active booking statuses when bookings exist', async () => {
  const fixture = {
    buses: [
      {
        id: 'outbound',
        trip_type: 'round_trip',
        return_bus_id: 'return',
        departure_time: '2099-01-01T10:00:00.000Z',
        status: 'scheduled',
      },
      {
        id: 'return',
        trip_type: 'round_trip',
        return_bus_id: 'outbound',
        departure_time: '2099-01-02T10:00:00.000Z',
        status: 'scheduled',
      },
    ],
    bookings: [
      { id: 'pending', bus_id: 'outbound', status: 'pending' },
      { id: 'payment', bus_id: 'return', status: 'payment_uploaded' },
      { id: 'confirmed', bus_id: 'outbound', status: 'confirmed' },
      { id: 'requested', bus_id: 'return', status: 'cancellation_requested' },
      { id: 'terminal', bus_id: 'return', status: 'cancelled' },
    ],
  };
  const busService = loadBusServiceWithFixture(fixture);

  const result = await busService.deleteBus('outbound', 'admin-user');

  assert.equal(result.mode, 'cancelled');
  assert.equal(result.rejectedBookingCount, 2);
  assert.equal(result.cancelledBookingCount, 2);
  assert.deepEqual(fixture.buses.map((bus) => bus.status), ['cancelled', 'cancelled']);

  const byId = Object.fromEntries(fixture.bookings.map((booking) => [booking.id, booking]));
  assert.equal(byId.pending.status, 'rejected');
  assert.equal(byId.payment.status, 'rejected');
  assert.equal(byId.confirmed.status, 'cancelled');
  assert.equal(byId.requested.status, 'cancelled');
  assert.equal(byId.terminal.status, 'cancelled');

  for (const id of ['pending', 'payment', 'confirmed', 'requested']) {
    assert.equal(byId[id].cancelled_by, 'admin-user');
    assert.equal(byId[id].cancellation_reason, 'Bus cancelled by admin');
    assert.ok(byId[id].cancelled_at);
  }
  assert.equal(byId.terminal.cancelled_by, undefined);
});

test('deleteBus rejects past buses without mutating rows', async () => {
  const fixture = {
    buses: [
      {
        id: 'past-bus',
        trip_type: 'one_way',
        return_bus_id: null,
        departure_time: '2020-01-01T10:00:00.000Z',
        status: 'scheduled',
      },
    ],
    bookings: [],
  };
  const busService = loadBusServiceWithFixture(fixture);

  await assert.rejects(
    () => busService.deleteBus('past-bus', 'admin-user'),
    /Cannot delete a bus after departure time/
  );
  assert.equal(fixture.buses[0].status, 'scheduled');
});
