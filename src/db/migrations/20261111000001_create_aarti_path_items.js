/**
 * Aarti & Path: PDFs (aartis, chalisa, stotras) that admins upload with a title and
 * everyone can read from Home. An item never changes: to fix it, delete it and add it again,
 * so apps can cache each PDF by id for good. The list is shown oldest first.
 *
 * @param { import("knex").Knex } knex
 */
exports.up = async function (knex) {
  await knex.schema.createTable('aarti_path_items', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.string('title', 80).notNullable();
    table.string('file_key', 255).notNullable();
    table.integer('size_bytes').notNullable();
    table.integer('page_count').nullable();
    table.uuid('created_by').nullable().references('id').inTable('users').onDelete('SET NULL');
    table.timestamp('created_at', { useTz: true }).notNullable().defaultTo(knex.fn.now());

    table.index(['created_at']);
  });
};

/**
 * @param { import("knex").Knex } knex
 */
exports.down = async function (knex) {
  await knex.schema.dropTableIfExists('aarti_path_items');
};
