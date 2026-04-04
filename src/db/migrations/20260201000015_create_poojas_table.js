/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = function (knex) {
  return knex.schema.createTable('poojas', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));

    table.timestamp('scheduled_at').notNullable();
    table.string('place', 200).notNullable();
    table.integer('total_tokens').notNullable();

    table
      .enum('status', ['scheduled', 'cancelled', 'completed'])
      .defaultTo('scheduled');

    table
      .uuid('created_by')
      .notNullable()
      .references('id')
      .inTable('users')
      .onDelete('RESTRICT');

    table.timestamp('created_at').defaultTo(knex.fn.now());
    table.timestamp('updated_at').defaultTo(knex.fn.now());

    table.index('scheduled_at');
    table.index('status');
    table.index('created_by');
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = function (knex) {
  return knex.schema.dropTable('poojas');
};

