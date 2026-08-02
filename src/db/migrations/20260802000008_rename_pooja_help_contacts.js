exports.up = async function up(knex) {
  await knex('help_contacts')
    .where('category_code', 'pooja')
    .update({ title: 'Puja token booking' });
};

exports.down = async function down(knex) {
  await knex('help_contacts')
    .where('category_code', 'pooja')
    .update({ title: 'Pooja token booking' });
};
