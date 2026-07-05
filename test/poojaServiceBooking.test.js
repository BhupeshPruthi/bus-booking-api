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
  let groupByColumn = null;
  let orderByColumn = null;
  let orderByDirection = 'asc';

  function rowsForTable() {
    const baseTable = table.split(/\s+as\s+/i)[0];
    return fixture[baseTable] || [];
  }

  function readRowsForTable() {
    const baseTable = table.split(/\s+as\s+/i)[0];
    const rows = rowsForTable();
    if (baseTable !== 'pooja_bookings' || !fixture.poojas) return rows;

    return rows.map((row) => {
      const pooja = fixture.poojas.find((item) => item.id === row.pooja_id);
      if (!pooja) return row;
      return {
        ...row,
        pooja_place: row.pooja_place ?? pooja.place,
        pooja_scheduled_at: row.pooja_scheduled_at ?? pooja.scheduled_at,
      };
    });
  }

  function columnValue(row, column) {
    return row[column] ?? row[column.split('.').pop()];
  }

  function matches(row) {
    return conditions.every((condition) => {
      const actual = columnValue(row, condition.column);
      switch (condition.operator) {
        case '>':
          return new Date(actual).getTime() > new Date(condition.value).getTime();
        case 'in':
          return condition.value.includes(actual);
        case '=':
        default:
          return actual === condition.value;
      }
    });
  }

  const query = {
    select() {
      return query;
    },
    join() {
      return query;
    },
    leftJoin() {
      return query;
    },
    where(column, operatorOrValue, maybeValue) {
      const hasOperator = maybeValue !== undefined;
      conditions.push({
        column,
        operator: hasOperator ? operatorOrValue : '=',
        value: hasOperator ? maybeValue : operatorOrValue,
      });
      return query;
    },
    andWhere(column, operatorOrValue, maybeValue) {
      return query.where(column, operatorOrValue, maybeValue);
    },
    whereIn(column, values) {
      conditions.push({ column, operator: 'in', value: values });
      return query;
    },
    groupBy(column) {
      groupByColumn = column;
      return query;
    },
    orderBy(column, direction = 'asc') {
      orderByColumn = column;
      orderByDirection = direction;
      return query;
    },
    forUpdate() {
      return query;
    },
    first() {
      firstOnly = true;
      return query;
    },
    count(aliasExpression) {
      aggregate = { type: 'count', expression: aliasExpression };
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
      const readRows = operation === 'select' ? readRowsForTable() : rows;
      const matchingRows = readRows.filter(matches);

      if (operation === 'insert') {
        const insertError = fixture.__insertErrors?.[table];
        if (insertError) throw insertError;

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
        if (groupByColumn) {
          const grouped = new Map();
          matchingRows.forEach((row) => {
            const key = row[groupByColumn];
            if (!grouped.has(key)) grouped.set(key, []);
            grouped.get(key).push(row);
          });
          return Array.from(grouped.entries()).map(([key, rowsForGroup]) => ({
            [groupByColumn]: key,
            [alias]: aggregate.type === 'count'
              ? rowsForGroup.filter((row) => row[column] != null).length
              : rowsForGroup
                .map((row) => parseInt(row[column], 10))
                .filter((value) => Number.isFinite(value))
                .reduce((total, value) => total + value, 0),
          }));
        }
        if (aggregate.type === 'count') {
          return { [alias]: matchingRows.filter((row) => row[column] != null).length };
        }
        const values = matchingRows
          .map((row) => parseInt(row[column], 10))
          .filter((value) => Number.isFinite(value));
        const result = aggregate.type === 'sum'
          ? values.reduce((total, value) => total + value, 0)
          : (values.length ? Math.max(...values) : null);
        return { [alias]: result };
      }

      const selectedRows = [...matchingRows];
      if (orderByColumn) {
        selectedRows.sort((a, b) => {
          const left = columnValue(a, orderByColumn);
          const right = columnValue(b, orderByColumn);
          if (left === right) return 0;
          const result = left > right ? 1 : -1;
          return orderByDirection === 'desc' ? -result : result;
        });
      }
      return firstOnly ? selectedRows[0] : selectedRows;
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

function hoursFromNow(hours) {
  return new Date(Date.now() + hours * 60 * 60 * 1000).toISOString();
}

test('getUpcomingPoojas keeps poojas visible until eight hours after start', async () => {
  const fixture = {
    poojas: [
      {
        id: 'expired',
        scheduled_at: hoursFromNow(-9),
        total_tokens: 10,
        status: 'scheduled',
      },
      {
        id: 'active-after-start',
        scheduled_at: hoursFromNow(-2),
        total_tokens: 10,
        status: 'scheduled',
      },
      {
        id: 'future',
        scheduled_at: hoursFromNow(2),
        total_tokens: 10,
        status: 'scheduled',
      },
      {
        id: 'cancelled',
        scheduled_at: hoursFromNow(2),
        total_tokens: 10,
        status: 'cancelled',
      },
    ],
    pooja_bookings: [
      { id: 'booking-1', pooja_id: 'active-after-start', status: 'confirmed', token_number: 1 },
      { id: 'booking-2', pooja_id: 'future', status: 'confirmed', token_number: 1 },
    ],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  const poojas = await poojaService.getUpcomingPoojas();

  assert.deepEqual(poojas.map((pooja) => pooja.id), ['active-after-start', 'future']);
  const activePooja = poojas.find((pooja) => pooja.id === 'active-after-start');
  const futurePooja = poojas.find((pooja) => pooja.id === 'future');
  assert.equal(activePooja.bookedTokens, 1);
  assert.equal(activePooja.bookingStatus, 'open');
  assert.equal(activePooja.canBook, true);
  assert.equal(futurePooja.bookingStatus, 'not_started');
  assert.equal(futurePooja.canBook, false);
  assert.equal(futurePooja.bookingOpensAt, fixture.poojas[2].scheduled_at);
  assert.ok(futurePooja.bookingClosesAt);
});

test('getUserPoojaBookings returns only current user tokens newest first', async () => {
  const fixture = {
    poojas: [
      { id: 'pooja-1', place: 'Temple One', scheduled_at: '2026-07-04T10:00:00.000Z' },
      { id: 'pooja-2', place: 'Temple Two', scheduled_at: '2026-07-05T10:00:00.000Z' },
      { id: 'pooja-3', place: 'Temple Three', scheduled_at: '2026-07-06T10:00:00.000Z' },
    ],
    pooja_bookings: [
      {
        id: 'old-confirmed',
        pooja_id: 'pooja-1',
        user_id: 'user-1',
        name: 'Asha',
        phone: '9999999999',
        member_count: 2,
        city: 'Delhi',
        token_number: 4,
        status: 'confirmed',
        created_at: '2026-07-01T09:00:00.000Z',
      },
      {
        id: 'other-user',
        pooja_id: 'pooja-2',
        user_id: 'user-2',
        name: 'Ravi',
        phone: '8888888888',
        member_count: 1,
        city: 'Noida',
        token_number: 1,
        status: 'confirmed',
        created_at: '2026-07-03T09:00:00.000Z',
      },
      {
        id: 'new-cancelled',
        pooja_id: 'pooja-3',
        user_id: 'user-1',
        name: 'Asha',
        phone: '9999999999',
        member_count: 3,
        city: 'Gurgaon',
        token_number: 2,
        status: 'cancelled',
        cancelled_at: '2026-07-04T12:00:00.000Z',
        created_at: '2026-07-04T09:00:00.000Z',
      },
    ],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  const bookings = await poojaService.getUserPoojaBookings('user-1');

  assert.deepEqual(bookings.map((booking) => booking.id), ['new-cancelled', 'old-confirmed']);
  assert.deepEqual(bookings.map((booking) => booking.status), ['cancelled', 'confirmed']);
  assert.equal(bookings[0].place, 'Temple Three');
  assert.equal(bookings[0].scheduledAt, '2026-07-06T10:00:00.000Z');
  assert.equal(bookings[0].memberCount, 3);
  assert.equal(bookings[0].city, 'Gurgaon');
  assert.equal(bookings[0].cancelledAt, '2026-07-04T12:00:00.000Z');
});

test('getUserPoojaBookings returns an empty list when the user has never booked pooja', async () => {
  const fixture = {
    poojas: [],
    pooja_bookings: [
      {
        id: 'other-user',
        pooja_id: 'pooja-1',
        user_id: 'user-2',
        status: 'confirmed',
        created_at: '2026-07-01T09:00:00.000Z',
      },
    ],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  const bookings = await poojaService.getUserPoojaBookings('user-1');

  assert.deepEqual(bookings, []);
});

test('bookToken consumes one token per booking request and keeps member count as metadata', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: hoursFromNow(-1),
        total_tokens: 2,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [
      {
        id: 'existing',
        pooja_id: 'pooja-1',
        user_id: 'other-user',
        status: 'confirmed',
        member_count: 3,
        token_number: 1,
      },
    ],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  const booking = await poojaService.bookToken('pooja-1', 'user-1', {
    name: 'Asha',
    phone: '9999999999',
    memberCount: 5,
    city: '',
  });

  assert.equal(booking.memberCount, 5);
  assert.equal(booking.city, 'Delhi - NCR');
  assert.equal(booking.tokenNumber, 2);
  assert.equal(await poojaService.getBookedTokensCount('pooja-1'), 2);
});

test('bookToken rejects requests before the pooja scheduled time', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: hoursFromNow(2),
        total_tokens: 2,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  await assert.rejects(
    () => poojaService.bookToken('pooja-1', 'user-1', {
      name: 'Asha',
      phone: '9999999999',
      memberCount: 1,
      city: 'Delhi',
    }),
    /Pooja booking has not started yet/
  );
});

test('bookToken allows requests at the pooja scheduled time', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: new Date().toISOString(),
        total_tokens: 2,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  const booking = await poojaService.bookToken('pooja-1', 'user-1', {
    name: 'Asha',
    phone: '9999999999',
    memberCount: 1,
    city: 'Delhi',
  });

  assert.equal(booking.status, 'confirmed');
});

test('bookToken allows booking after start until the eight-hour window expires', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: hoursFromNow(-2),
        total_tokens: 2,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  const booking = await poojaService.bookToken('pooja-1', 'user-1', {
    name: 'Asha',
    phone: '9999999999',
    memberCount: 1,
    city: 'Delhi',
  });

  assert.equal(booking.status, 'confirmed');
  assert.equal(booking.tokenNumber, 1);
});

test('bookToken rejects booking after the eight-hour pooja window expires', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: hoursFromNow(-9),
        total_tokens: 2,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  await assert.rejects(
    () => poojaService.bookToken('pooja-1', 'user-1', {
      name: 'Asha',
      phone: '9999999999',
      memberCount: 1,
      city: 'Delhi',
    }),
    /Cannot book a pooja after it has expired/
  );
});

test('bookToken rejects only when confirmed booking count reaches capacity', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: hoursFromNow(-1),
        total_tokens: 1,
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
      memberCount: 10,
      city: 'Noida',
    }),
    /No tokens available/
  );

  const pooja = await poojaService.getPoojaById('pooja-1');
  assert.equal(pooja.bookingStatus, 'full');
  assert.equal(pooja.canBook, false);
});

test('bookToken rejects when the same user already has a confirmed token for the pooja', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: hoursFromNow(-1),
        total_tokens: 10,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [
      {
        id: 'existing',
        pooja_id: 'pooja-1',
        user_id: 'same-user',
        status: 'confirmed',
        member_count: 1,
        token_number: 1,
      },
    ],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  await assert.rejects(
    () => poojaService.bookToken('pooja-1', 'same-user', {
      name: 'Asha',
      phone: '9999999999',
      memberCount: 1,
      city: 'Delhi',
    }),
    /already have a token/
  );

  assert.equal(fixture.pooja_bookings.length, 1);
});

test('bookToken allows the same user after their previous token is cancelled', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: hoursFromNow(-1),
        total_tokens: 2,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [
      {
        id: 'cancelled-1',
        pooja_id: 'pooja-1',
        user_id: 'same-user',
        status: 'cancelled',
        member_count: 1,
        token_number: 1,
      },
    ],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  const booking = await poojaService.bookToken('pooja-1', 'same-user', {
    name: 'Asha',
    phone: '9999999999',
    memberCount: 1,
    city: 'Delhi',
  });

  assert.equal(booking.status, 'confirmed');
  assert.equal(booking.tokenNumber, 1);
});

test('bookToken maps confirmed user unique index conflicts to duplicate-token errors', async () => {
  const fixture = {
    __insertErrors: {
      pooja_bookings: {
        code: '23505',
        constraint: 'pooja_bookings_confirmed_user_unique',
      },
    },
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: hoursFromNow(-1),
        total_tokens: 2,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  await assert.rejects(
    () => poojaService.bookToken('pooja-1', 'same-user', {
      name: 'Asha',
      phone: '9999999999',
      memberCount: 1,
      city: 'Delhi',
    }),
    /already have a token/
  );

  assert.equal(fixture.pooja_bookings.length, 0);
});

test('bookToken reuses the lowest token number released by cancelled bookings', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: hoursFromNow(-1),
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
        scheduled_at: hoursFromNow(-1),
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

test('cancelBookingAsAdmin allows cancellation after start until the eight-hour window expires', async () => {
  const fixture = {
    poojas: [
      {
        id: 'pooja-1',
        scheduled_at: hoursFromNow(-2),
        total_tokens: 10,
        status: 'scheduled',
      },
    ],
    pooja_bookings: [
      { id: 'booking-1', pooja_id: 'pooja-1', status: 'confirmed', member_count: 1, token_number: 1 },
    ],
  };
  const poojaService = loadPoojaServiceWithFixture(fixture);

  const cancelled = await poojaService.cancelBookingAsAdmin('pooja-1', 'booking-1', 'admin-1');

  assert.equal(cancelled.status, 'cancelled');
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
    /Cannot cancel a token after the pooja has expired/
  );
});
