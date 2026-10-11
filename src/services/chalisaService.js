const jwt = require('jsonwebtoken');
const config = require('../config');
const { db } = require('../config/database');
const {
  AppError,
  NotFoundError,
  UnauthorizedError,
  ValidationError,
} = require('../utils/errors');

/** A full Hanuman Chalisa takes at least this long, so a faster second tap isn't counted. */
const MIN_ROUND_SECONDS = 30;
/** How long after a tap the reader can still undo it. */
const UNDO_WINDOW_SECONDS = 120;
/** How long after ending a marathon the super admin can still undo it. */
const RESTORE_WINDOW_SECONDS = 600;
/** Readings done before joining can be entered once per marathon, up to this many. */
const MAX_STARTING_COUNT = 5000;
const BOARD_REFRESH_SECONDS = 30;
const DEFAULT_BOARD_LIMIT = 100;
const MAX_BOARD_LIMIT = 200;
const END_CONFIRM_TEXT = 'END';

// Guest passes use their own key, so one can never be accepted as a sign-in access token.
const guestTokenSecret = () => `${config.jwt.secret}:chalisa-guest`;

function signGuestToken(participantId) {
  return jwt.sign({ typ: 'chalisa_guest', pid: participantId }, guestTokenSecret());
}

function invalidGuestError() {
  return new UnauthorizedError(
    'Your guest pass is no longer valid. Enter your name again to continue.',
    'INVALID_GUEST_TOKEN'
  );
}

function verifyGuestToken(token) {
  try {
    const decoded = jwt.verify(token, guestTokenSecret());
    if (decoded?.typ !== 'chalisa_guest' || !decoded.pid) throw new Error('wrong token type');
    return decoded.pid;
  } catch (_) {
    throw invalidGuestError();
  }
}

function appError(message, statusCode, code, details) {
  const error = new AppError(message, statusCode, code);
  if (details) error.details = details;
  return error;
}

function normalizeText(value) {
  return String(value || '')
    .replace(/[\u0000-\u001f\u007f]/g, '')
    .replace(/\s+/g, ' ')
    .trim();
}

function indiaDateOnly(now = new Date()) {
  return new Date(now.getTime() + (330 * 60 * 1000)).toISOString().slice(0, 10);
}

function addCalendarDays(date, days) {
  const instant = new Date(`${date}T00:00:00.000Z`);
  instant.setUTCDate(instant.getUTCDate() + days);
  return instant.toISOString().slice(0, 10);
}

/** Consecutive reading days ending today (or yesterday, if nothing is logged yet today). */
function computeStreak(readDates, today) {
  const dates = new Set(readDates);
  let day = dates.has(today) ? today : addCalendarDays(today, -1);
  let streak = 0;
  while (dates.has(day)) {
    streak += 1;
    day = addCalendarDays(day, -1);
  }
  return streak;
}

function monthRange(month) {
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) {
    throw new ValidationError('month must be in YYYY-MM format');
  }
  const [year, monthNumber] = month.split('-').map(Number);
  const nextMonth = monthNumber === 12
    ? `${year + 1}-01`
    : `${year}-${String(monthNumber + 1).padStart(2, '0')}`;
  return { fromDate: `${month}-01`, toDateExclusive: `${nextMonth}-01` };
}

function toCount(value) {
  return Number(value || 0);
}

function dateOnlyValue(value) {
  if (value == null) return null;
  if (value instanceof Date) {
    // pg returns DATE columns as local-midnight Date objects.
    const local = new Date(value.getTime() - (value.getTimezoneOffset() * 60 * 1000));
    return local.toISOString().slice(0, 10);
  }
  return String(value).slice(0, 10);
}

function addSeconds(value, seconds) {
  return new Date(new Date(value).getTime() + (seconds * 1000));
}

/** scheduled → running → ended; a cancelled marathon never started and is hidden. */
function marathonState(row, now = new Date()) {
  if (!row) return 'none';
  if (row.cancelled_at) return 'cancelled';
  if (row.ended_at) return 'ended';
  return new Date(row.starts_at) > now ? 'scheduled' : 'running';
}

function formatMarathon(row, now = new Date()) {
  if (!row) return null;
  return {
    id: row.id,
    title: row.title,
    goal: row.goal == null ? null : Number(row.goal),
    startsAt: row.starts_at,
    endedAt: row.ended_at || null,
    state: marathonState(row, now),
  };
}

function formatParticipant(row) {
  if (!row) return null;
  return { id: row.id, displayName: row.display_name, isGuest: !row.user_id };
}

function nameRequiredError() {
  return appError('Enter your name to start counting.', 400, 'NAME_REQUIRED');
}

/**
 * Finds the participant behind a request.
 * identity = { userId } for a signed-in user, { guestParticipantId } for a guest, or {}.
 * With create, a signed-in user gets a participant row named from their profile.
 */
async function resolveParticipant(identity, database, { create = false, forUpdate = false } = {}) {
  const lock = (query) => (forUpdate ? query.forUpdate() : query);

  if (identity?.userId) {
    const existing = await lock(database('chalisa_participants').where('user_id', identity.userId)).first();
    if (existing || !create) return existing || null;

    // An access token can outlive account deletion by a few minutes; never recreate data then.
    const user = await activeUser(database, identity.userId);
    const displayName = normalizeText(user.name).slice(0, 60);
    if (displayName.length < 2) throw nameRequiredError();

    await database('chalisa_participants')
      .insert({ user_id: user.id, display_name: displayName })
      .onConflict('user_id')
      .ignore();
    return lock(database('chalisa_participants').where('user_id', user.id)).first();
  }

  if (identity?.guestParticipantId) {
    const guest = await lock(
      database('chalisa_participants').where('id', identity.guestParticipantId).whereNull('user_id')
    ).first();
    if (!guest) throw invalidGuestError();
    return guest;
  }

  if (create) throw nameRequiredError();
  return null;
}

async function activeUser(database, userId) {
  const user = await database('users').where('id', userId).whereNull('deleted_at').first('id', 'name');
  if (!user) throw new UnauthorizedError('Please sign in again.', 'SESSION_USER_NOT_FOUND');
  return user;
}

/**
 * The scheduled or running marathon, if any. Counting reads it FOR SHARE: taps don't block
 * each other, but ending the marathon (FOR UPDATE) waits for taps in flight, and a tap that
 * waited on an end sees the marathon as ended.
 */
async function currentMarathon(database, { forUpdate = false, forShare = false } = {}) {
  const query = database('chalisa_marathons').whereNull('ended_at').whereNull('cancelled_at');
  if (forUpdate) return query.forUpdate().first();
  if (forShare) return query.forShare().first();
  return query.first();
}

async function marathonTotals(database, marathonId) {
  const row = await database('chalisa_marathon_totals')
    .where('marathon_id', marathonId)
    .sum('total as total')
    .count('* as participants')
    .first();
  return { total: toCount(row?.total), participantCount: toCount(row?.participants) };
}

async function rankInMarathon(database, marathonId, total) {
  const ahead = await database('chalisa_marathon_totals')
    .where('marathon_id', marathonId)
    .andWhere('total', '>', total)
    .count('* as count')
    .first();
  return toCount(ahead?.count) + 1;
}

async function hasEntryInMarathon(database, participantId, marathonId) {
  return Boolean(await database('chalisa_readings')
    .where({ participant_id: participantId, marathon_id: marathonId })
    .first('id'));
}

/** Counts shown after every tap and undo. */
async function countsFor(participant, marathon, database, now = new Date()) {
  const today = indiaDateOnly(now);
  const [lifetimeRow, todayRow, mine, overall, hasEntry] = await Promise.all([
    database('chalisa_readings').where('participant_id', participant.id).sum('count as total').first(),
    database('chalisa_readings')
      .where({ participant_id: participant.id, read_date: today })
      .sum('count as total')
      .first(),
    marathon
      ? database('chalisa_marathon_totals')
        .where({ marathon_id: marathon.id, participant_id: participant.id })
        .first('total')
      : null,
    marathon ? marathonTotals(database, marathon.id) : null,
    marathon ? hasEntryInMarathon(database, participant.id, marathon.id) : true,
  ]);

  return {
    participant: formatParticipant(participant),
    today: toCount(todayRow?.total),
    lifetime: toCount(lifetimeRow?.total),
    marathon: marathon
      ? {
        ...formatMarathon(marathon, now),
        myCount: toCount(mine?.total),
        total: overall.total,
        participantCount: overall.participantCount,
      }
      : null,
    lastReadAt: participant.last_read_at || null,
    canSetStartingCount: Boolean(marathon && marathonState(marathon, now) === 'running' && !hasEntry),
    maxStartingCount: MAX_STARTING_COUNT,
  };
}

function formatReading(row) {
  return {
    clientRequestId: row.client_request_id,
    kind: row.kind,
    count: Number(row.count),
    readAt: row.read_at,
    undoUntil: addSeconds(row.read_at, UNDO_WINDOW_SECONDS),
  };
}

/** Sets or changes the name shown on the board. Creates a guest pass for new guests. */
async function saveParticipant(identity, rawName, database = db) {
  const displayName = normalizeText(rawName);
  if (displayName.length < 2 || displayName.length > 40) {
    throw new ValidationError('Name must be between 2 and 40 characters');
  }

  if (identity?.userId) {
    await activeUser(database, identity.userId);
    await database('chalisa_participants')
      .insert({ user_id: identity.userId, display_name: displayName })
      .onConflict('user_id')
      .merge({ display_name: displayName, updated_at: database.fn.now() });
    const participant = await database('chalisa_participants').where('user_id', identity.userId).first();
    return { participant: formatParticipant(participant), guestToken: null };
  }

  if (identity?.guestParticipantId) {
    const [updated] = await database('chalisa_participants')
      .where('id', identity.guestParticipantId)
      .whereNull('user_id')
      .update({ display_name: displayName, updated_at: database.fn.now() })
      .returning('*');
    if (updated) return { participant: formatParticipant(updated), guestToken: signGuestToken(updated.id) };
  }

  const [created] = await database('chalisa_participants')
    .insert({ user_id: null, display_name: displayName })
    .returning('*');
  return { participant: formatParticipant(created), guestToken: signGuestToken(created.id) };
}

async function addToMarathonTotal(trx, marathonId, participantId, count) {
  await trx('chalisa_marathon_totals')
    .insert({ marathon_id: marathonId, participant_id: participantId, total: count, last_read_at: trx.fn.now() })
    .onConflict(['marathon_id', 'participant_id'])
    .merge({
      total: trx.raw('chalisa_marathon_totals.total + ?', [count]),
      last_read_at: trx.fn.now(),
    });
}

function requireRunning(marathon, now = new Date()) {
  const state = marathonState(marathon, now);
  if (state === 'scheduled') {
    throw appError('Counting opens when the marathon starts.', 409, 'MARATHON_NOT_STARTED', {
      startsAt: marathon.starts_at,
    });
  }
  if (state !== 'running') {
    throw appError('There is no marathon running right now.', 409, 'MARATHON_NOT_RUNNING');
  }
}

/**
 * Records one completed round, or (with startingCount) the readings done before joining
 * the running marathon, allowed once and before the first round. Retrying with the same
 * clientRequestId never counts twice. A round within MIN_ROUND_SECONDS of the previous
 * entry isn't counted.
 */
async function recordReading(identity, clientRequestId, { startingCount } = {}, database = db) {
  return database.transaction(async (trx) => {
    // Locking the participant serialises their taps, starting count and retries.
    const participant = await resolveParticipant(identity, trx, { create: true, forUpdate: true });

    // A tap that was undone and is then retried (e.g. a delayed resend) must not count again.
    const undone = await trx('chalisa_undone_requests').where('client_request_id', clientRequestId).first();
    if (undone) {
      if (undone.participant_id !== participant.id) throw new ValidationError('clientRequestId is already used');
      return { reading: null, ...(await countsFor(participant, await currentMarathon(trx), trx)), duplicate: true };
    }

    const existing = await trx('chalisa_readings').where('client_request_id', clientRequestId).first();
    if (existing) {
      if (existing.participant_id !== participant.id) {
        throw new ValidationError('clientRequestId is already used');
      }
      const marathon = await trx('chalisa_marathons').where('id', existing.marathon_id).first();
      return {
        reading: formatReading(existing),
        ...(await countsFor(participant, marathon, trx)),
        duplicate: true,
      };
    }

    const marathon = await currentMarathon(trx, { forShare: true });
    const now = new Date();
    requireRunning(marathon, now);

    let kind = 'round';
    let count = 1;

    if (startingCount != null) {
      if (!Number.isInteger(startingCount) || startingCount < 1 || startingCount > MAX_STARTING_COUNT) {
        throw new ValidationError(`Starting count must be between 1 and ${MAX_STARTING_COUNT}`);
      }
      if (await hasEntryInMarathon(trx, participant.id, marathon.id)) {
        throw appError(
          'A starting count can only be added before your first round.',
          400,
          'STARTING_COUNT_NOT_ALLOWED'
        );
      }
      kind = 'opening';
      count = startingCount;
    } else {
      const gate = await trx.raw(
        `SELECT (last_read_at IS NOT NULL AND last_read_at > now() - (? * interval '1 second')) AS too_soon,
                GREATEST(0, EXTRACT(EPOCH FROM (last_read_at + (? * interval '1 second') - now()))) AS wait_seconds
           FROM chalisa_participants WHERE id = ?`,
        [MIN_ROUND_SECONDS, MIN_ROUND_SECONDS, participant.id]
      );
      const { too_soon: tooSoon, wait_seconds: waitSeconds } = gate.rows[0];
      if (tooSoon) {
        throw appError(
          `That was quick. A full Chalisa takes at least ${MIN_ROUND_SECONDS} seconds, so this tap wasn't counted.`,
          429,
          'ROUND_TOO_SOON',
          { retryAfterSeconds: Math.max(1, Math.ceil(Number(waitSeconds))) }
        );
      }
    }

    const [reading] = await trx('chalisa_readings')
      .insert({
        participant_id: participant.id,
        marathon_id: marathon.id,
        kind,
        count,
        client_request_id: clientRequestId,
      })
      .returning('*');
    await addToMarathonTotal(trx, marathon.id, participant.id, count);
    const [updated] = await trx('chalisa_participants')
      .where('id', participant.id)
      .update({ last_read_at: reading.read_at, updated_at: trx.fn.now() })
      .returning('*');

    return {
      reading: formatReading(reading),
      ...(await countsFor(updated, marathon, trx)),
      duplicate: false,
    };
  });
}

/** Removes a mistaken round or starting count made in the last UNDO_WINDOW_SECONDS. */
async function undoReading(identity, clientRequestId, database = db) {
  return database.transaction(async (trx) => {
    const participant = await resolveParticipant(identity, trx, { forUpdate: true });
    if (!participant) throw new NotFoundError('Reading');

    const reading = await trx('chalisa_readings')
      .where({ client_request_id: clientRequestId, participant_id: participant.id })
      .first();
    if (!reading) throw new NotFoundError('Reading');

    const marathon = await trx('chalisa_marathons').where('id', reading.marathon_id).forShare().first();
    const tooOld = (Date.now() - new Date(reading.read_at).getTime()) / 1000 > UNDO_WINDOW_SECONDS;
    if (tooOld || marathonState(marathon) !== 'running') {
      throw appError('This round can no longer be undone.', 400, 'UNDO_EXPIRED');
    }

    await trx('chalisa_readings').where('id', reading.id).del();
    await trx('chalisa_undone_requests').insert({ client_request_id: reading.client_request_id, participant_id: participant.id });
    await trx('chalisa_marathon_totals')
      .where({ marathon_id: reading.marathon_id, participant_id: participant.id })
      .decrement('total', reading.count);
    await trx('chalisa_marathon_totals')
      .where({ marathon_id: reading.marathon_id, participant_id: participant.id })
      .andWhere('total', '<=', 0)
      .del();

    const latest = await trx('chalisa_readings')
      .where('participant_id', participant.id)
      .max('read_at as last')
      .first();
    const [updated] = await trx('chalisa_participants')
      .where('id', participant.id)
      .update({ last_read_at: latest?.last || null, updated_at: trx.fn.now() })
      .returning('*');

    return countsFor(updated, marathon, trx);
  });
}

/** Personal totals for My path and Profile. Works between marathons too. */
async function getMyStats(identity, filters = {}, database = db, now = new Date()) {
  const today = indiaDateOnly(now);
  const month = filters.month || today.slice(0, 7);
  const range = monthRange(month);
  const thisMonth = monthRange(today.slice(0, 7));
  const participant = await resolveParticipant(identity, database);
  const marathon = await currentMarathon(database);

  const empty = {
    participant: null,
    lifetime: 0,
    today: 0,
    thisMonth: 0,
    streakDays: 0,
    firstReadDate: null,
    month,
    days: [],
    marathons: [],
    lastReadAt: null,
    canSetStartingCount: marathonState(marathon, now) === 'running',
    maxStartingCount: MAX_STARTING_COUNT,
    minRoundSeconds: MIN_ROUND_SECONDS,
  };
  if (!participant) return empty;

  const readings = () => database('chalisa_readings').where('participant_id', participant.id);
  const [counts, monthRow, firstRow, dayRows, recentDates, marathonRows] = await Promise.all([
    countsFor(participant, marathon, database, now),
    readings()
      .andWhere('read_date', '>=', thisMonth.fromDate)
      .andWhere('read_date', '<', thisMonth.toDateExclusive)
      .sum('count as total')
      .first(),
    readings().min('read_date as first').first(),
    readings()
      .andWhere('read_date', '>=', range.fromDate)
      .andWhere('read_date', '<', range.toDateExclusive)
      .groupBy('read_date')
      .select('read_date')
      .sum('count as total')
      .orderBy('read_date', 'desc'),
    readings()
      .andWhere('read_date', '>', addCalendarDays(today, -400))
      .distinct('read_date')
      .orderBy('read_date', 'desc'),
    database('chalisa_marathon_totals as t')
      .join('chalisa_marathons as m', 'm.id', 't.marathon_id')
      .where('t.participant_id', participant.id)
      .whereNull('m.cancelled_at')
      .select('m.*', 't.total as my_total')
      .orderBy('m.starts_at', 'desc'),
  ]);

  const marathons = await Promise.all(marathonRows.map(async (row) => ({
    id: row.id,
    title: row.title,
    count: toCount(row.my_total),
    rank: await rankInMarathon(database, row.id, row.my_total),
    state: marathonState(row, now),
    startsAt: row.starts_at,
    endedAt: row.ended_at || null,
  })));

  return {
    ...empty,
    participant: counts.participant,
    lifetime: counts.lifetime,
    today: counts.today,
    thisMonth: toCount(monthRow?.total),
    streakDays: computeStreak(recentDates.map((row) => dateOnlyValue(row.read_date)), today),
    firstReadDate: dateOnlyValue(firstRow?.first),
    days: dayRows.map((row) => ({ date: dateOnlyValue(row.read_date), count: toCount(row.total) })),
    marathons,
    lastReadAt: counts.lastReadAt,
    canSetStartingCount: counts.canSetStartingCount,
  };
}

async function optionalParticipant(identity, database) {
  try {
    return await resolveParticipant(identity, database);
  } catch (_) {
    return null; // An invalid guest pass shouldn't hide public data.
  }
}

/** The Group marathon tab: the scheduled or running marathon and its board, highest first. */
async function getMarathonBoard(identity, filters = {}, database = db, now = new Date()) {
  const limit = Math.min(MAX_BOARD_LIMIT, Math.max(1, Number(filters.limit) || DEFAULT_BOARD_LIMIT));
  const offset = Math.max(0, Number(filters.offset) || 0);
  const marathon = await currentMarathon(database);
  const base = { serverTime: now, refreshAfterSeconds: BOARD_REFRESH_SECONDS };
  if (!marathon) {
    return { ...base, marathon: null, total: 0, participantCount: 0, leaderboard: [], me: null };
  }

  const [totals, rows, participant] = await Promise.all([
    marathonTotals(database, marathon.id),
    database
      .select('*')
      .from(
        database('chalisa_marathon_totals as t')
          .join('chalisa_participants as p', 'p.id', 't.participant_id')
          .where('t.marathon_id', marathon.id)
          .select(
            't.participant_id',
            't.total',
            't.last_read_at',
            'p.display_name',
            'p.user_id',
            database.raw('RANK() OVER (ORDER BY t.total DESC) AS rank')
          )
          .as('ranked')
      )
      .orderBy([
        { column: 'total', order: 'desc' },
        { column: 'last_read_at', order: 'asc' },
        { column: 'participant_id', order: 'asc' },
      ])
      .limit(limit)
      .offset(offset),
    optionalParticipant(identity, database),
  ]);

  let me = null;
  if (participant) {
    const mine = await database('chalisa_marathon_totals')
      .where({ marathon_id: marathon.id, participant_id: participant.id })
      .first('total');
    me = mine
      ? { participantId: participant.id, rank: await rankInMarathon(database, marathon.id, mine.total), count: toCount(mine.total) }
      : { participantId: participant.id, rank: null, count: 0 };
  }

  return {
    ...base,
    marathon: formatMarathon(marathon, now),
    total: totals.total,
    participantCount: totals.participantCount,
    leaderboard: rows.map((row) => ({
      rank: toCount(row.rank),
      participantId: row.participant_id,
      displayName: row.display_name,
      isGuest: !row.user_id,
      count: toCount(row.total),
      lastReadAt: row.last_read_at,
    })),
    me,
  };
}

function formatArchived(row) {
  return {
    ...formatMarathon(row),
    finalTotal: toCount(row.final_total),
    finalParticipantCount: toCount(row.final_participant_count),
  };
}

async function lastEndedMarathon(database) {
  const row = await database('chalisa_marathons')
    .whereNotNull('ended_at')
    .orderBy('ended_at', 'desc')
    .first();
  return row ? formatArchived(row) : null;
}

/** Decides the Home page entry: none, scheduled or running. */
async function getSummary(identity, database = db, now = new Date()) {
  const marathon = await currentMarathon(database);
  const lastEnded = await lastEndedMarathon(database);
  if (!marathon) {
    return { state: 'none', marathon: null, total: 0, participantCount: 0, myToday: 0, serverTime: now, lastEnded };
  }

  const [totals, participant] = await Promise.all([
    marathonTotals(database, marathon.id),
    optionalParticipant(identity, database),
  ]);
  let myToday = 0;
  if (participant) {
    const row = await database('chalisa_readings')
      .where({ participant_id: participant.id, read_date: indiaDateOnly(now) })
      .sum('count as total')
      .first();
    myToday = toCount(row?.total);
  }

  return {
    state: marathonState(marathon, now),
    marathon: formatMarathon(marathon, now),
    total: totals.total,
    participantCount: totals.participantCount,
    myToday,
    serverTime: now,
    lastEnded,
  };
}

/** Past marathons, newest first, with the totals they ended on. */
async function listArchivedMarathons(filters = {}, database = db) {
  const limit = Math.min(50, Math.max(1, Number(filters.limit) || 20));
  const rows = await database('chalisa_marathons')
    .whereNotNull('ended_at')
    .orderBy('ended_at', 'desc')
    .limit(limit);
  return rows.map(formatArchived);
}

async function findMarathon(trx, id) {
  const marathon = await trx('chalisa_marathons').where('id', id).forUpdate().first();
  if (!marathon || marathon.cancelled_at) throw new NotFoundError('Marathon');
  return marathon;
}

function alreadyActiveError() {
  return appError(
    'A marathon is already scheduled or running. Edit or end it first.',
    409,
    'MARATHON_ALREADY_ACTIVE'
  );
}

function parseStart(startsAt, now) {
  if (!startsAt) return now;
  const start = new Date(startsAt);
  if (Number.isNaN(start.getTime())) throw new ValidationError('startsAt must be a date and time');
  // A minute of slack so "start now" from a phone with a slightly fast clock isn't refused.
  if (start.getTime() < now.getTime() - 60 * 1000) throw new ValidationError('The start time is in the past');
  return start;
}

/** Super admin: schedules the next marathon. It shows on everyone's Home page immediately. */
async function scheduleMarathon(adminUserId, { title, startsAt, goal } = {}, database = db, now = new Date()) {
  const cleanTitle = normalizeText(title);
  if (cleanTitle.length < 2) throw new ValidationError('Enter a name for the marathon');
  const start = parseStart(startsAt, now);

  return database.transaction(async (trx) => {
    if (await currentMarathon(trx, { forUpdate: true })) throw alreadyActiveError();
    try {
      const [created] = await trx('chalisa_marathons')
        .insert({ title: cleanTitle, goal: goal || null, starts_at: start, created_by: adminUserId })
        .returning('*');
      return { marathon: formatMarathon(created, now) };
    } catch (error) {
      if (error.code === '23505') throw alreadyActiveError();
      throw error;
    }
  });
}

/** Super admin: edits the name or goal; the start time only before the marathon starts. */
async function updateMarathon(id, changes = {}, database = db, now = new Date()) {
  return database.transaction(async (trx) => {
    const marathon = await findMarathon(trx, id);
    const state = marathonState(marathon, now);
    if (state === 'ended') throw appError('This marathon has already ended.', 409, 'MARATHON_NOT_RUNNING');

    const update = { updated_at: trx.fn.now() };
    if (changes.title !== undefined) {
      const cleanTitle = normalizeText(changes.title);
      if (cleanTitle.length < 2) throw new ValidationError('Enter a name for the marathon');
      update.title = cleanTitle;
    }
    if (changes.goal !== undefined) update.goal = changes.goal || null;
    if (changes.startsAt !== undefined) {
      if (state !== 'scheduled') {
        throw appError('The start time can\'t change after the marathon has started.', 409, 'MARATHON_ALREADY_STARTED');
      }
      update.starts_at = parseStart(changes.startsAt, now);
    }

    const [updated] = await trx('chalisa_marathons').where('id', id).update(update).returning('*');
    return { marathon: formatMarathon(updated, now) };
  });
}

/** Super admin: cancels a marathon that hasn't started. Nobody read in it, so it isn't archived. */
async function cancelMarathon(id, database = db, now = new Date()) {
  return database.transaction(async (trx) => {
    const marathon = await findMarathon(trx, id);
    const state = marathonState(marathon, now);
    if (state !== 'scheduled') {
      throw appError(
        state === 'running' ? 'This marathon has started. End it instead.' : 'This marathon has already ended.',
        409,
        state === 'running' ? 'MARATHON_ALREADY_STARTED' : 'MARATHON_NOT_RUNNING'
      );
    }
    await trx('chalisa_marathons').where('id', id).update({ cancelled_at: trx.fn.now(), updated_at: trx.fn.now() });
    return { cancelled: true };
  });
}

/** Super admin: ends the running marathon, saves its final totals and archives it. */
async function endMarathon(adminUserId, id, { confirmText } = {}, database = db, now = new Date()) {
  if (confirmText !== END_CONFIRM_TEXT) {
    throw new ValidationError(`Type ${END_CONFIRM_TEXT} to confirm`);
  }
  return database.transaction(async (trx) => {
    const marathon = await findMarathon(trx, id);
    const state = marathonState(marathon, now);
    if (state === 'scheduled') {
      throw appError('This marathon hasn\'t started yet. Cancel it instead.', 409, 'MARATHON_NOT_STARTED');
    }
    if (state !== 'running') throw appError('This marathon has already ended.', 409, 'MARATHON_NOT_RUNNING');

    const totals = await marathonTotals(trx, id);
    const [ended] = await trx('chalisa_marathons')
      .where('id', id)
      .update({
        ended_at: trx.fn.now(),
        ended_by: adminUserId,
        final_total: totals.total,
        final_participant_count: totals.participantCount,
        updated_at: trx.fn.now(),
      })
      .returning('*');
    return {
      archived: {
        ...formatArchived(ended),
        restoreUntil: addSeconds(ended.ended_at, RESTORE_WINDOW_SECONDS),
      },
    };
  });
}

/** Super admin: undoes ending a marathon within RESTORE_WINDOW_SECONDS. */
async function restoreMarathon(id, database = db, now = new Date()) {
  return database.transaction(async (trx) => {
    const marathon = await findMarathon(trx, id);
    if (!marathon.ended_at) throw appError('This marathon is still running.', 409, 'MARATHON_ALREADY_ACTIVE');
    if (now.getTime() - new Date(marathon.ended_at).getTime() > RESTORE_WINDOW_SECONDS * 1000) {
      throw appError('The 10 minutes to undo ending this marathon have passed.', 400, 'RESTORE_EXPIRED');
    }
    if (await currentMarathon(trx, { forUpdate: true })) throw alreadyActiveError();

    try {
      const [restored] = await trx('chalisa_marathons')
        .where('id', id)
        .update({
          ended_at: null,
          ended_by: null,
          final_total: null,
          final_participant_count: null,
          updated_at: trx.fn.now(),
        })
        .returning('*');
      return { marathon: formatMarathon(restored, now) };
    } catch (error) {
      if (error.code === '23505') throw alreadyActiveError();
      throw error;
    }
  });
}

module.exports = {
  MIN_ROUND_SECONDS,
  UNDO_WINDOW_SECONDS,
  RESTORE_WINDOW_SECONDS,
  MAX_STARTING_COUNT,
  END_CONFIRM_TEXT,
  signGuestToken,
  verifyGuestToken,
  saveParticipant,
  recordReading,
  undoReading,
  getMyStats,
  getMarathonBoard,
  getSummary,
  listArchivedMarathons,
  scheduleMarathon,
  updateMarathon,
  cancelMarathon,
  endMarathon,
  restoreMarathon,
  helpers: {
    computeStreak,
    monthRange,
    normalizeText,
    indiaDateOnly,
    dateOnlyValue,
    marathonState,
  },
};
