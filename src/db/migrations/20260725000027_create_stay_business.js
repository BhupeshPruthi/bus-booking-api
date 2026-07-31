/**
 * Add scoped administration and the aggregate Stay booking model.
 *
 * This migration has not been deployed, so it intentionally replaces the
 * earlier physical-room prototype. Existing admins retain Bus, Pooja, and
 * Event access through `bus_admin`.
 *
 * @param { import("knex").Knex } knex
 */
exports.up = async function (knex) {
  await knex.schema.alterTable('users', (table) => {
    table.string('admin_type', 40).nullable();
    table.index('admin_type');
  });

  await knex('users')
    .where('role', 'admin')
    .whereNull('admin_type')
    .update({ admin_type: 'bus_admin' });

  await knex.raw(`
    ALTER TABLE users
      ADD CONSTRAINT users_admin_type_check
      CHECK (
        admin_type IS NULL OR (
          role = 'admin' AND
          admin_type IN ('bus_admin', 'stay_admin', 'super_admin')
        )
      )
  `);

  await knex.schema.createTable('stay_unit_types', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.string('code', 40).unique().notNullable();
    table.string('display_name', 100).notNullable();
    table.integer('capacity').nullable();
    table.integer('total_inventory').notNullable();
    table.decimal('nightly_rate', 12, 2).notNullable();
    table.integer('display_order').notNullable().defaultTo(0);
    table.boolean('is_active').notNullable().defaultTo(true);
    table.timestamps(true, true);
  });

  await knex.schema.createTable('stay_bookings', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.string('reference', 32).unique().notNullable();
    table.uuid('user_id').nullable().references('id').inTable('users').onDelete('SET NULL');
    table.enum('status', [
      'pending',
      'confirmed',
      'cancellation_requested',
      'rejected',
      'cancelled',
      'completed',
    ]).notNullable().defaultTo('pending');
    table.date('check_in_date').notNullable();
    table.date('check_out_date').notNullable();
    table.integer('night_count').notNullable();
    table.integer('guest_count').notNullable();
    table.string('contact_name', 200).notNullable();
    table.string('contact_email', 320).notNullable();
    table.string('contact_phone', 20).notNullable();
    table.decimal('total_amount', 12, 2).notNullable();
    table.boolean('mattress_requested').notNullable().defaultTo(false);
    table.text('customer_note').nullable();
    table.boolean('cancellation_policy_accepted').notNullable().defaultTo(false);
    table.uuid('confirmed_by').nullable().references('id').inTable('users').onDelete('SET NULL');
    table.timestamp('confirmed_at', { useTz: true }).nullable();
    table.uuid('rejected_by').nullable().references('id').inTable('users').onDelete('SET NULL');
    table.timestamp('rejected_at', { useTz: true }).nullable();
    table.text('rejection_reason').nullable();
    table.timestamp('completed_at', { useTz: true }).nullable();
    table.timestamps(true, true);
    table.index(['status', 'check_in_date']);
    table.index(['user_id', 'created_at']);
    table.index(['check_out_date', 'status']);
  });

  await knex.schema.createTable('stay_booking_items', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.uuid('booking_id').notNullable()
      .references('id').inTable('stay_bookings').onDelete('CASCADE');
    table.uuid('unit_type_id').notNullable()
      .references('id').inTable('stay_unit_types').onDelete('RESTRICT');
    table.string('unit_type_code', 40).notNullable();
    table.string('unit_type_name', 100).notNullable();
    table.integer('quantity').notNullable();
    table.decimal('nightly_rate', 12, 2).notNullable();
    table.integer('night_count').notNullable();
    table.decimal('line_total', 12, 2).notNullable();
    table.timestamps(true, true);
    table.unique(['booking_id', 'unit_type_id']);
    table.index('booking_id');
  });

  await knex.schema.createTable('stay_cancellation_requests', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.uuid('booking_id').notNullable()
      .references('id').inTable('stay_bookings').onDelete('CASCADE');
    table.enum('status', ['pending', 'approved', 'rejected']).notNullable().defaultTo('pending');
    table.string('previous_booking_status', 40).notNullable();
    table.text('reason').nullable();
    table.timestamp('requested_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());
    table.boolean('standard_full_refund_eligible').notNullable();
    table.decimal('hours_before_check_in', 12, 2).notNullable();
    table.enum('refund_decision', ['full', 'partial', 'none']).nullable();
    table.decimal('refund_amount', 12, 2).nullable();
    table.text('decision_reason').nullable();
    table.uuid('decided_by').nullable().references('id').inTable('users').onDelete('SET NULL');
    table.timestamp('decided_at', { useTz: true }).nullable();
    table.timestamps(true, true);
    table.index(['status', 'requested_at']);
    table.index('booking_id');
    table.unique(['booking_id'], {
      indexName: 'stay_one_pending_cancellation_per_booking',
      predicate: knex.whereRaw("status = 'pending'"),
    });
  });

  await knex('stay_unit_types').insert([
    {
      code: 'three_bed_room', display_name: '3 Bed Room', capacity: 3,
      total_inventory: 13, nightly_rate: 1200, display_order: 1,
    },
    {
      code: 'four_bed_room', display_name: '4 Bed Room', capacity: 4,
      total_inventory: 1, nightly_rate: 1500, display_order: 2,
    },
    {
      code: 'five_bed_room', display_name: '5 Bed Room', capacity: 5,
      total_inventory: 1, nightly_rate: 1600, display_order: 3,
    },
    {
      code: 'hall', display_name: 'Hall', capacity: 25,
      total_inventory: 3, nightly_rate: 3500, display_order: 4,
    },
  ]);
};

/**
 * @param { import("knex").Knex } knex
 */
exports.down = async function (knex) {
  await knex.schema.dropTableIfExists('stay_cancellation_requests');
  await knex.schema.dropTableIfExists('stay_booking_items');
  await knex.schema.dropTableIfExists('stay_bookings');
  await knex.schema.dropTableIfExists('stay_unit_types');
  await knex.raw('ALTER TABLE users DROP CONSTRAINT IF EXISTS users_admin_type_check');
  await knex.schema.alterTable('users', (table) => {
    table.dropIndex('admin_type');
    table.dropColumn('admin_type');
  });
};
