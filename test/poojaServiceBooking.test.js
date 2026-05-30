const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

function projectPath(relativePath) {
  return path.join(__dirname, '..', relativePath);
}

function makeQuery(table, fixture) {
  const conditions = [];
  let operation = 'select';
  let updateData = null;
  let insertData = null;
  let returningColumn = null;
  let firstOnly = false;
  let aggregate = null;

  function rowsForTable() {
    return fixture[table];
  }

  function matches(row) {
    return conditions.every((condition) => row[condition.column] === condition.value);
  }

  const query = {
    where(column, value) {
      conditions.push({ column, value });
      return query;
    },
    forUpdate() {
      return query;
    },
    first() {
      firstOnly = true;
      return query;
    },
    sum(aliasExpression) {
      aggregate = { type: 'sum', expression: aliasExpression };
      return query;
    },
    max(aliasExpression) {
      aggregate = { type: 'max', expression: aliasExpression };
      return query;
    },
    insert(data) {
      operation = 'insert';
      insertData = data;
      return query;
    },
    update(data) {
      operation = 'update';
      updateData = data;
      return query;
    },
    returning(column) {
      returningColumn = column;
      return query;
    },
    execute() {
      const rows = rowsForTable();
      const matchingRows = rows.filter(matches);

      if (operation === 'insert') {
        const row = {
          id: `booking-${rows.length + 1}`,
          created_at: new Date().toISOString(),
          updated_at: new Date().toISOString(),
          ...insertData,
        };
        rows.push(row);
        return returningColumn === '*' ? [row] : [{ [returningColumn]: row[returningColumn] }];
      }

      if (operation === 'update') {
        matchingRows.forEach((row) => Object.assign(row, updateData));
        if (returningColumn === '*') return matchingRows;
        if (returningColumn) return matchingRows.map((row) => ({ [returningColumn]: row[returningColumn] }));
        return matchingRows.length;
      }

      if (aggregate) {
        const [column, alias] = aggregate.expression.split(/\s+as\s+/i);
        const values = matchingRows
          .map((row) => parseInt(row[column], 10))
          .filter((value) => Number.isFinite(value));
        const result = aggregate.type === 'sum'
          ? values.reduce((total, value) => total + value, 0)
          : (values.length ? Math.max(...values) : null);
        return { [alias]: result };
      }

      return firstOnly ? matchingRows[0] : matchingRows;
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
  const db = (table) => makeQuery(table, fixture);
  db.transaction = async (callback) => callback((table) => makeQuery(table, fixture));
  return db;
}

function loadPoojaServiceWithFixture(fixture) {
  const databasePath = projectPath('src/config/database.js');
  const poojaServicePath = projectPath('src/services/poojaService.js');

  delete require.cache[databasePath];
  delete require.cache[poojaServicePath];
  require.cache[databasePath] = {
    id: databasePath,
    filename: databasePath,
    loaded: true,
    exports: { db: makeDb(fixture), testConnection: async () => {}, knexConfig: {} },
  };

  return require(poojaServicePath);
}

test('bookToken consumes capacity by member count and assigns next per-pooja token number', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: '2099-01-01T10:00:00.000Z',
        total_tokens: 5,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [
      { id: 'existing', pooja_id: 'pooja-1', status: 'confirmed', member_count: 3, token_number: 1 },
    ],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  const booking = await poojaService.bookToken('pooja-1', 'user-1', {
    name: 'Asha',
    phone: '9999999999',
    memberCount: 2,
    city: '',
  });

  assert.equal(booking.memberCount, 2);
  assert.equal(booking.city, 'Delhi - NCR');
  assert.equal(booking.tokenNumber, 2);
  assert.equal(await poojaService.getBookedTokensCount('pooja-1'), 5);
});

test('bookToken rejects member count above available capacity', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: '2099-01-01T10:00:00.000Z',
        total_tokens: 4,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [
      { id: 'existing', pooja_id: 'pooja-1', status: 'confirmed', member_count: 3, token_number: 1 },
    ],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  await assert.rejects(
    () => poojaService.bookToken('pooja-1', 'user-1', {
      name: 'Asha',
      phone: '9999999999',
      memberCount: 2,
      city: 'Noida',
    }),
    /No tokens available/
  );
});

test('bookToken allows duplicate users and increments token numbers', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: '2099-01-01T10:00:00.000Z',
        total_tokens: 10,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  const first = await poojaService.bookToken('pooja-1', 'same-user', {
    name: 'Asha',
    phone: '9999999999',
    memberCount: 1,
    city: 'Delhi',
  });
  const second = await poojaService.bookToken('pooja-1', 'same-user', {
    name: 'Asha',
    phone: '9999999999',
    memberCount: 1,
    city: 'Delhi',
  });

  assert.equal(first.tokenNumber, 1);
  assert.equal(second.tokenNumber, 2);
});

test('bookToken reuses the lowest token number released by cancelled bookings', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: '2099-01-01T10:00:00.000Z',
        total_tokens: 3,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [
      { id: 'cancelled-1', pooja_id: 'pooja-1', status: 'cancelled', member_count: 1, token_number: 1 },
      { id: 'confirmed-2', pooja_id: 'pooja-1', status: 'confirmed', member_count: 1, token_number: 2 },
    ],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  const booking = await poojaService.bookToken('pooja-1', 'user-1', {
    name: 'Asha',
    phone: '9999999999',
    memberCount: 1,
    city: 'Delhi',
  });

  assert.equal(booking.tokenNumber, 1);
  assert.ok(booking.tokenNumber <= fixture.poojas[0].total_tokens);
});

test('bookToken skips token numbers still reserved by confirmed bookings', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: '2099-01-01T10:00:00.000Z',
        total_tokens: 2,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [
      { id: 'cancelled-1', pooja_id: 'pooja-1', status: 'cancelled', member_count: 1, token_number: 1 },
      { id: 'confirmed-1', pooja_id: 'pooja-1', status: 'confirmed', member_count: 1, token_number: 1 },
    ],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  const booking = await poojaService.bookToken('pooja-1', 'user-1', {
    name: 'Asha',
    phone: '9999999999',
    memberCount: 1,
    city: 'Delhi',
  });

  assert.equal(booking.tokenNumber, 2);
  const confirmedTokenNumbers = fixture.pooja_bookings
    .filter((row) => row.pooja_id === 'pooja-1' && row.status === 'confirmed')
    .map((row) => row.token_number);
  assert.equal(new Set(confirmedTokenNumbers).size, confirmedTokenNumbers.length);
});

test('cancelBookingAsAdmin cancels future confirmed token and releases capacity', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: '2099-01-01T10:00:00.000Z',
        total_tokens: 10,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [
      { id: 'booking-1', pooja_id: 'pooja-1', status: 'confirmed', member_count: 4, token_number: 1 },
    ],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  const cancelled = await poojaService.cancelBookingAsAdmin('pooja-1', 'booking-1', 'admin-1');

  assert.equal(cancelled.status, 'cancelled');
  assert.equal(cancelled.memberCount, 4);
  assert.equal(cancelled.cancelledAt, fixture.pooja_bookings[0].cancelled_at);
  assert.equal(fixture.pooja_bookings[0].cancelled_by, 'admin-1');
  assert.equal(await poojaService.getBookedTokensCount('pooja-1'), 0);
});

test('cancelBookingAsAdmin rejects wrong pooja, already-cancelled, and past pooja tokens', async () => {
  const futureFixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: '2099-01-01T10:00:00.000Z',
        total_tokens: 10,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [
      { id: 'booking-1', pooja_id: 'other-pooja', status: 'confirmed', member_count: 1, token_number: 1 },
      { id: 'booking-2', pooja_id: 'pooja-1', status: 'cancelled', member_count: 1, token_number: 2 },
    ],
  };
  let poojaService = loadPoojaServiceWithFixture(futureFixture);

  await assert.rejects(
    () => poojaService.cancelBookingAsAdmin('pooja-1', 'booking-1', 'admin-1'),
    /Pooja booking not found/
  );
  await assert.rejects(
    () => poojaService.cancelBookingAsAdmin('pooja-1', 'booking-2', 'admin-1'),
    /Cannot cancel token with status: cancelled/
  );

  const pastFixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: '2020-01-01T10:00:00.000Z',
        total_tokens: 10,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [
      { id: 'booking-1', pooja_id: 'pooja-1', status: 'confirmed', member_count: 1, token_number: 1 },
    ],
  };
  poojaService = loadPoojaServiceWithFixture(pastFixture);

  await assert.rejects(
    () => poojaService.cancelBookingAsAdmin('pooja-1', 'booking-1', 'admin-1'),
    /Cannot cancel a token after the pooja has started/
  );
});
