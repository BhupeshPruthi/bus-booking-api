exports.up = async function (knex) {
  await knex.schema.createTable('stay_coupons', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.string('code', 50).notNullable().unique();
    table.decimal('discount_amount', 12, 2).notNullable();
    table.date('start_date').notNullable();
    table.date('end_date').notNullable();
    table.boolean('is_active').notNullable().defaultTo(true);
    table.uuid('created_by').nullable()
      .references('id').inTable('users').onDelete('SET NULL');
    table.timestamps(true, true);
    table.index(['is_active', 'start_date', 'end_date']);
  });

  await knex.raw(`
    ALTER TABLE stay_coupons
      ADD CONSTRAINT stay_coupons_code_uppercase_check CHECK (code = UPPER(code)),
      ADD CONSTRAINT stay_coupons_discount_positive_check CHECK (discount_amount > 0),
      ADD CONSTRAINT stay_coupons_date_order_check CHECK (end_date >= start_date)
  `);

  await knex.schema.alterTable('stay_bookings', (table) => {
    table.decimal('subtotal_amount', 12, 2).nullable();
    table.decimal('discount_amount', 12, 2).notNullable().defaultTo(0);
    table.uuid('coupon_id').nullable()
      .references('id').inTable('stay_coupons').onDelete('SET NULL');
    table.string('coupon_code', 50).nullable();
    table.index('coupon_id');
  });

  await knex.raw('UPDATE stay_bookings SET subtotal_amount = total_amount');
  await knex.raw('ALTER TABLE stay_bookings ALTER COLUMN subtotal_amount SET NOT NULL');
  await knex.raw(`
    ALTER TABLE stay_bookings
      ADD CONSTRAINT stay_bookings_discount_nonnegative_check CHECK (discount_amount >= 0),
      ADD CONSTRAINT stay_bookings_coupon_total_check CHECK (
        subtotal_amount >= discount_amount AND
        total_amount = subtotal_amount - discount_amount
      )
  `);
};

exports.down = async function (knex) {
  await knex.raw(`
    ALTER TABLE stay_bookings
      DROP CONSTRAINT IF EXISTS stay_bookings_coupon_total_check,
      DROP CONSTRAINT IF EXISTS stay_bookings_discount_nonnegative_check
  `);
  await knex.schema.alterTable('stay_bookings', (table) => {
    table.dropIndex('coupon_id');
    table.dropColumn('coupon_code');
    table.dropColumn('coupon_id');
    table.dropColumn('discount_amount');
    table.dropColumn('subtotal_amount');
  });
  await knex.schema.dropTableIfExists('stay_coupons');
};
