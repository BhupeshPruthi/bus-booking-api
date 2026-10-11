/**
 * Hanuman Chalisa counter.
 *
 * - chalisa_marathons: group marathons scheduled by the super admin. One at a time can be
 *   scheduled or running (ended_at and cancelled_at both null); it runs from starts_at until
 *   the super admin ends it, and is then archived with its final totals.
 * - chalisa_participants: one row per signed-in user (user_id) or guest (user_id null).
 * - chalisa_readings: permanent log of every reading (tapped rounds plus one optional
 *   starting count per marathon). Never cleared when a marathon ends.
 * - chalisa_marathon_totals: running total per participant per marathon, for the board.
 * - chalisa_undone_requests: ids of undone taps, so a late retry of one is never re-counted.
 *
 * @param { import("knex").Knex } knex
 */
exports.up = async function (knex) {
  await knex.schema.createTable('chalisa_marathons', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.string('title', 120).notNullable();
    table.integer('goal').nullable();
    table.timestamp('starts_at', { useTz: true }).notNullable();
    table.uuid('created_by').nullable().references('id').inTable('users').onDelete('SET NULL');
    table.timestamp('ended_at', { useTz: true }).nullable();
    table.uuid('ended_by').nullable().references('id').inTable('users').onDelete('SET NULL');
    table.timestamp('cancelled_at', { useTz: true }).nullable();
    table.bigInteger('final_total').nullable();
    table.integer('final_participant_count').nullable();
    table.timestamp('created_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
    table.timestamp('updated_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());

    table.index(['ended_at']);
  });
  await knex.raw(`
    CREATE UNIQUE INDEX chalisa_marathons_one_current
      ON chalisa_marathons ((true))
      WHERE ended_at IS NULL AND cancelled_at IS NULL
  `);

  await knex.schema.createTable('chalisa_participants', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.uuid('user_id').nullable().unique().references('id').inTable('users').onDelete('CASCADE');
    table.string('display_name', 60).notNullable();
    // Time of the latest accepted reading; enforces the minimum time between rounds.
    table.timestamp('last_read_at', { useTz: true }).nullable();
    table.timestamp('created_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
    table.timestamp('updated_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
  });

  await knex.schema.createTable('chalisa_readings', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.uuid('participant_id').notNullable()
      .references('id').inTable('chalisa_participants').onDelete('CASCADE');
    // RESTRICT: marathons are archived, never deleted, and deleting one must not erase lifetime counts.
    table.uuid('marathon_id').notNullable()
      .references('id').inTable('chalisa_marathons').onDelete('RESTRICT');
    // 'round' = one tapped reading (count 1). 'opening' = readings done before joining,
    // entered once per marathon as a starting count.
    table.string('kind', 10).notNullable().defaultTo('round');
    table.integer('count').notNullable().defaultTo(1);
    table.timestamp('read_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
    table.date('read_date').notNullable()
      .defaultTo(knex.raw("((now() AT TIME ZONE 'Asia/Kolkata')::date)"));
    table.uuid('client_request_id').notNullable().unique();
    table.timestamp('created_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());

    table.index(['participant_id', 'read_date']);
    table.index(['participant_id', 'marathon_id']);
  });
  await knex.raw(`
    ALTER TABLE chalisa_readings ADD CONSTRAINT chalisa_readings_kind_count CHECK (
      (kind = 'round' AND count = 1) OR (kind = 'opening' AND count BETWEEN 1 AND 5000)
    )
  `);

  await knex.schema.createTable('chalisa_marathon_totals', (table) => {
    table.uuid('marathon_id').notNullable()
      .references('id').inTable('chalisa_marathons').onDelete('CASCADE');
    table.uuid('participant_id').notNullable()
      .references('id').inTable('chalisa_participants').onDelete('CASCADE');
    table.bigInteger('total').notNullable().defaultTo(0);
    table.timestamp('last_read_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());

    table.primary(['marathon_id', 'participant_id']);
  });
  await knex.raw(
    'CREATE INDEX chalisa_marathon_totals_board ON chalisa_marathon_totals (marathon_id, total DESC, last_read_at ASC)'
  );

  await knex.schema.createTable('chalisa_undone_requests', (table) => {
    table.uuid('client_request_id').primary();
    table.uuid('participant_id').notNullable()
      .references('id').inTable('chalisa_participants').onDelete('CASCADE');
    table.timestamp('created_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
  });
};

/**
 * @param { import("knex").Knex } knex
 */
exports.down = async function (knex) {
  await knex.schema.dropTableIfExists('chalisa_undone_requests');
  await knex.schema.dropTableIfExists('chalisa_marathon_totals');
  await knex.schema.dropTableIfExists('chalisa_readings');
  await knex.schema.dropTableIfExists('chalisa_participants');
  await knex.schema.dropTableIfExists('chalisa_marathons');
};
