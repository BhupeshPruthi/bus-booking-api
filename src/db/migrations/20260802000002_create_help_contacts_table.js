exports.up = async function (knex) {
  await knex.schema.createTable('help_contacts', (table) => {
    table.uuid('id').primary().defaultTo(knex.raw('gen_random_uuid()'));
    table.string('category_code', 50).notNullable().unique();
    table.string('title', 120).notNullable();
    table.string('contact_name', 120).notNullable();
    table.string('phone', 30).notNullable();
    table.integer('display_order').notNullable().defaultTo(0);
    table.boolean('is_active').notNullable().defaultTo(true);
  });

  await knex('help_contacts').insert([
    {
      category_code: 'bus',
      title: 'Bus booking and services',
      contact_name: 'Bhupesh Pruthi',
      phone: '9513333839',
      display_order: 1,
    },
    {
      category_code: 'pooja',
      title: 'Pooja token booking',
      contact_name: 'Bhupesh Pruthi',
      phone: '9513333839',
      display_order: 2,
    },
    {
      category_code: 'dharamshala',
      title: 'Dharamshala booking and services',
      contact_name: 'Bhupesh Pruthi',
      phone: '9513333839',
      display_order: 3,
    },
    {
      category_code: 'emergency',
      title: 'Emergency',
      contact_name: 'Bhupesh Pruthi',
      phone: '9513333839',
      display_order: 4,
    },
  ]);
};

exports.down = async function (knex) {
  await knex.schema.dropTableIfExists('help_contacts');
};
