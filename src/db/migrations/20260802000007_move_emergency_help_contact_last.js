const EMERGENCY_ORDER = 8;

exports.up = async function up(knex) {
  await knex('help_contacts').where('contact_name', 'Gopal Bansal').update({ display_order: 5 });
  await knex('help_contacts').where('contact_name', 'Praveen Jain').update({ display_order: 6 });
  await knex('help_contacts').where('contact_name', 'Kamal Arya').update({ display_order: 7 });
  await knex('help_contacts').where('contact_name', 'Guru Ji').update({ display_order: EMERGENCY_ORDER });
};

exports.down = async function down(knex) {
  await knex('help_contacts').where('contact_name', 'Guru Ji').update({ display_order: 5 });
  await knex('help_contacts').where('contact_name', 'Gopal Bansal').update({ display_order: 6 });
  await knex('help_contacts').where('contact_name', 'Praveen Jain').update({ display_order: 7 });
  await knex('help_contacts').where('contact_name', 'Kamal Arya').update({ display_order: 8 });
};
