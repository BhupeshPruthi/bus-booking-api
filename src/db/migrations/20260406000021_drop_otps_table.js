/**
 * OTP login removed — drop otps table.
 * @param { import("knex").Knex } knex
 */
exports.up = function (knex) {
  return knex.schema.dropTableIfExists('otps');
};

exports.down = function (knex) {
  return knex.schema.createTable('otps', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.string('mobile', 20).notNullable();
    table.string('otp', 6).notNullable();
    table.integer('attempts').defaultTo(0);
    table.timestamp('expires_at').notNullable();
    table.timestamp('created_at').defaultTo(knex.fn.now());
    table.string('target_role', 20);
    table.index('mobile');
  });
};
