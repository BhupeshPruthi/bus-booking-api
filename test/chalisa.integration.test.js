const test = require('node:test');
const assert = require('node:assert/strict');
const { randomUUID } = require('node:crypto');

const testDatabaseUrl = process.env.TEST_DATABASE_URL;
// This test migrates and writes to the database. Make its declared test URL the only
// database connection it can use before the Knex singleton is initialized.
if (testDatabaseUrl) process.env.DATABASE_URL = testDatabaseUrl;
const hasTestDatabase = Boolean(testDatabaseUrl);
const { db } = require('../src/config/database');
const chalisa = require('../src/services/chalisaService');

async function expectCode(promise, code) {
  await assert.rejects(promise, (error) => {
    assert.equal(error.code, code, `expected ${code}, got ${error.code}: ${error.message}`);
    return true;
  });
}

async function createUser(name, role = 'consumer') {
  const [user] = await db('users')
    .insert({ mobile: `9${Math.floor(Math.random() * 1e9)}`, name, role })
    .returning('*');
  return user;
}

async function allowNextRound(participantId) {
  // Stands in for reading a full Chalisa: move the last reading 31 seconds into the past.
  await db('chalisa_participants')
    .where('id', participantId)
    .update({ last_read_at: db.raw("now() - interval '31 seconds'") });
}

test('Hanuman Chalisa marathon lifecycle and counting rules', { skip: !hasTestDatabase }, async (t) => {
  await db.migrate.latest({ directory: './src/db/migrations' });
  await db('chalisa_marathon_totals').del();
  await db('chalisa_readings').del();
  await db('chalisa_participants').del();
  await db('chalisa_marathons').del();

  const admin = await createUser('Super Admin', 'admin');
  const bhupesh = await createUser('Bhupesh');
  const bhupeshId = { userId: bhupesh.id };

  t.after(async () => {
    await db.destroy();
  });

  await t.test('no marathon: Home has no entry and counting is closed', async () => {
    const summary = await chalisa.getSummary({});
    assert.equal(summary.state, 'none');
    await expectCode(chalisa.recordReading(bhupeshId, randomUUID()), 'MARATHON_NOT_RUNNING');
  });

  const inTwoDays = new Date(Date.now() + 2 * 24 * 3600 * 1000);
  const { marathon } = await chalisa.scheduleMarathon(admin.id, {
    title: 'Hanuman Jayanti marathon',
    startsAt: inTwoDays,
    goal: 200000,
  });

  await t.test('scheduled: visible, but counting waits for the start', async () => {
    assert.equal(marathon.state, 'scheduled');
    assert.equal((await chalisa.getSummary({})).state, 'scheduled');
    await expectCode(chalisa.recordReading(bhupeshId, randomUUID()), 'MARATHON_NOT_STARTED');
    await expectCode(
      chalisa.scheduleMarathon(admin.id, { title: 'Second', startsAt: inTwoDays }),
      'MARATHON_ALREADY_ACTIVE'
    );
    await expectCode(chalisa.endMarathon(admin.id, marathon.id, { confirmText: 'END' }), 'MARATHON_NOT_STARTED');
  });

  await t.test('editing the start time makes it run now', async () => {
    const { marathon: edited } = await chalisa.updateMarathon(marathon.id, { startsAt: new Date() });
    assert.equal(edited.state, 'running');
    await expectCode(
      chalisa.updateMarathon(marathon.id, { startsAt: inTwoDays }),
      'MARATHON_ALREADY_STARTED'
    );
  });

  const guestJoin = await chalisa.saveParticipant({}, '  Meera  ');
  const meeraId = { guestParticipantId: chalisa.verifyGuestToken(guestJoin.guestToken) };

  await t.test('guests join with a name and get a pass that is not a sign-in token', async () => {
    assert.equal(guestJoin.participant.displayName, 'Meera');
    assert.equal(guestJoin.participant.isGuest, true);
    assert.throws(() => chalisa.verifyGuestToken('not-a-token'), { code: 'INVALID_GUEST_TOKEN' });
  });

  await t.test('one tap counts once, a fast second tap is refused, a retry is not double-counted', async () => {
    const tap = randomUUID();
    const first = await chalisa.recordReading(meeraId, tap);
    assert.equal(first.duplicate, false);
    assert.equal(first.today, 1);
    assert.equal(first.marathon.total, 1);

    const retry = await chalisa.recordReading(meeraId, tap);
    assert.equal(retry.duplicate, true);
    assert.equal(retry.marathon.total, 1);

    await assert.rejects(chalisa.recordReading(meeraId, randomUUID()), (error) => {
      assert.equal(error.code, 'ROUND_TOO_SOON');
      assert.ok(error.details.retryAfterSeconds >= 1 && error.details.retryAfterSeconds <= 30);
      return true;
    });

    await expectCode(
      chalisa.recordReading(meeraId, randomUUID(), { startingCount: 10 }),
      'STARTING_COUNT_NOT_ALLOWED'
    );
  });

  await t.test('a starting count can be undone and entered again', async () => {
    const wrong = randomUUID();
    const added = await chalisa.recordReading(bhupeshId, wrong, { startingCount: 200 });
    assert.equal(added.reading.kind, 'opening');
    assert.equal(added.marathon.myCount, 200);

    const undone = await chalisa.undoReading(bhupeshId, wrong);
    assert.equal(undone.marathon.myCount, 0);
    assert.equal(undone.canSetStartingCount, true);

    const right = await chalisa.recordReading(bhupeshId, randomUUID(), { startingCount: 20 });
    assert.equal(right.marathon.myCount, 20);
    assert.equal(right.marathon.total, 21);
  });

  await t.test('the board is highest first and shows the caller rank', async () => {
    const board = await chalisa.getMarathonBoard(meeraId);
    assert.deepEqual(board.leaderboard.map((row) => [row.rank, row.displayName, row.count]), [
      [1, 'Bhupesh', 20],
      [2, 'Meera', 1],
    ]);
    assert.deepEqual(board.me, { participantId: meeraId.guestParticipantId, rank: 2, count: 1 });
    assert.equal(board.participantCount, 2);
  });

  await t.test('parallel taps from one person count only once', async () => {
    const meera = await db('chalisa_participants').where('id', meeraId.guestParticipantId).first();
    await allowNextRound(meera.id);
    const results = await Promise.allSettled(
      Array.from({ length: 5 }, () => chalisa.recordReading(meeraId, randomUUID()))
    );
    assert.equal(results.filter((r) => r.status === 'fulfilled').length, 1);
    assert.ok(results.filter((r) => r.status === 'rejected').every((r) => r.reason.code === 'ROUND_TOO_SOON'));
    const board = await chalisa.getMarathonBoard({});
    assert.equal(board.total, 22);
  });

  await t.test('an undone tap that is retried later is not counted again', async () => {
    const meera = await db('chalisa_participants').where('id', meeraId.guestParticipantId).first();
    await allowNextRound(meera.id);
    const tap = randomUUID();
    const before = (await chalisa.getMarathonBoard({})).total;
    await chalisa.recordReading(meeraId, tap);
    await chalisa.undoReading(meeraId, tap);
    const retry = await chalisa.recordReading(meeraId, tap);
    assert.equal(retry.duplicate, true);
    assert.equal((await chalisa.getMarathonBoard({})).total, before);
    await expectCode(chalisa.recordReading(bhupeshId, tap), 'VALIDATION_ERROR');
  });

  await t.test('personal stats include lifetime and each marathon', async () => {
    const me = await chalisa.getMyStats(bhupeshId);
    assert.equal(me.lifetime, 20);
    assert.equal(me.today, 20);
    assert.equal(me.marathons.length, 1);
    assert.deepEqual(
      [me.marathons[0].title, me.marathons[0].count, me.marathons[0].rank],
      ['Hanuman Jayanti marathon', 20, 1]
    );
  });

  await t.test('ending archives the totals, closes counting and keeps lifetime', async () => {
    await expectCode(chalisa.endMarathon(admin.id, marathon.id, { confirmText: 'end' }), 'VALIDATION_ERROR');
    const { archived } = await chalisa.endMarathon(admin.id, marathon.id, { confirmText: 'END' });
    assert.equal(archived.finalTotal, 22);
    assert.equal('endedBy' in archived, false, 'the super admin name must not be in responses');
    assert.equal(archived.finalParticipantCount, 2);

    const summary = await chalisa.getSummary({});
    assert.equal(summary.state, 'none');
    assert.equal(summary.lastEnded.finalTotal, 22);
    await expectCode(chalisa.recordReading(bhupeshId, randomUUID()), 'MARATHON_NOT_RUNNING');
    await expectCode(chalisa.endMarathon(admin.id, marathon.id, { confirmText: 'END' }), 'MARATHON_NOT_RUNNING');

    const me = await chalisa.getMyStats(bhupeshId);
    assert.equal(me.lifetime, 20);
    assert.equal(me.marathons[0].state, 'ended');
    const archive = await chalisa.listArchivedMarathons();
    assert.equal(archive.length, 1);
    assert.equal('endedBy' in archive[0], false);
    assert.equal('endedBy' in (await chalisa.getSummary({})).lastEnded, false);
  });

  await t.test('ending can be undone within 10 minutes only', async () => {
    const { marathon: restored } = await chalisa.restoreMarathon(marathon.id);
    assert.equal(restored.state, 'running');
    assert.equal((await chalisa.getMarathonBoard({})).total, 22);

    await chalisa.endMarathon(admin.id, marathon.id, { confirmText: 'END' });
    const elevenMinutesLater = new Date(Date.now() + 11 * 60 * 1000);
    await expectCode(chalisa.restoreMarathon(marathon.id, db, elevenMinutesLater), 'RESTORE_EXPIRED');
  });

  await t.test('restoring while another marathon is scheduled is refused cleanly', async () => {
    const ended = await chalisa.scheduleMarathon(admin.id, { title: 'Short marathon' });
    await chalisa.endMarathon(admin.id, ended.marathon.id, { confirmText: 'END' });
    const { marathon: blocker } = await chalisa.scheduleMarathon(admin.id, { title: 'Blocker', startsAt: inTwoDays });
    await expectCode(chalisa.restoreMarathon(ended.marathon.id), 'MARATHON_ALREADY_ACTIVE');
    await chalisa.cancelMarathon(blocker.id);
  });

  await t.test('a scheduled marathon can be cancelled and disappears without archiving', async () => {
    const { marathon: next } = await chalisa.scheduleMarathon(admin.id, { title: 'Diwali marathon', startsAt: inTwoDays });
    await chalisa.cancelMarathon(next.id);
    assert.equal((await chalisa.getSummary({})).state, 'none');
    assert.equal((await chalisa.listArchivedMarathons()).length, 2);
    await expectCode(chalisa.restoreMarathon(next.id), 'NOT_FOUND');
  });

  await t.test('deleting an account removes that person\'s readings, and their old token cannot bring them back', async () => {
    const authService = require('../src/services/authService');
    await authService.deleteAccount(bhupesh.id);
    assert.equal(await db('chalisa_participants').where('user_id', bhupesh.id).first(), undefined);
    const me = await chalisa.getMyStats(bhupeshId);
    assert.equal(me.lifetime, 0);
    const archived = await chalisa.listArchivedMarathons();
    assert.equal(archived.find((m) => m.id === marathon.id).finalTotal, 22);

    // A still-valid access token after deletion must not recreate data.
    const { marathon: again } = await chalisa.scheduleMarathon(admin.id, { title: 'After deletion' });
    await expectCode(chalisa.recordReading(bhupeshId, randomUUID()), 'SESSION_USER_NOT_FOUND');
    await expectCode(chalisa.saveParticipant(bhupeshId, 'Ghost'), 'SESSION_USER_NOT_FOUND');
    assert.equal(await db('chalisa_participants').where('user_id', bhupesh.id).first(), undefined);
    await chalisa.endMarathon(admin.id, again.id, { confirmText: 'END' });
  });

  await t.test('a tap or rename racing with account deletion never leaves the person on the board', async () => {
    const authService = require('../src/services/authService');
    const { marathon: open } = await chalisa.scheduleMarathon(admin.id, { title: 'Deletion race' });
    for (let i = 0; i < 15; i += 1) {
      const user = await createUser(`Victim ${i}`);
      const ops = await Promise.allSettled([
        chalisa.recordReading({ userId: user.id }, randomUUID()),
        authService.deleteAccount(user.id),
        chalisa.saveParticipant({ userId: user.id }, `Victim ${i} renamed`),
      ]);
      assert.equal(ops[1].status, 'fulfilled');
      assert.equal(await db('chalisa_participants').where('user_id', user.id).first(), undefined, `round ${i}`);
    }
    await chalisa.endMarathon(admin.id, open.id, { confirmText: 'END' });
  });

  await t.test('a tap racing with End is either counted in the final total or refused', async () => {
    const { marathon: race } = await chalisa.scheduleMarathon(admin.id, { title: 'Race marathon' });
    const runner = await chalisa.saveParticipant({}, 'Runner');
    const runnerId = { guestParticipantId: chalisa.verifyGuestToken(runner.guestToken) };
    const results = await Promise.allSettled([
      chalisa.recordReading(runnerId, randomUUID()),
      chalisa.endMarathon(admin.id, race.id, { confirmText: 'END' }),
    ]);
    const tap = results[0];
    assert.equal(results[1].status, 'fulfilled');
    const archivedRace = (await chalisa.listArchivedMarathons()).find((m) => m.id === race.id);
    const counted = await db('chalisa_readings').where('marathon_id', race.id).sum('count as total').first();
    assert.equal(archivedRace.finalTotal, Number(counted.total || 0), 'archive must match the readings');
    if (tap.status === 'rejected') assert.equal(tap.reason.code, 'MARATHON_NOT_RUNNING');
  });
});
