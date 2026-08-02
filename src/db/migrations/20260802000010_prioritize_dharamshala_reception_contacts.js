async function updateOrder(knex, contactName, displayOrder, phone = null) {
  const query = knex('help_contacts').where('contact_name', contactName);
  if (phone) query.where('phone', phone);
  await query.update({ display_order: displayOrder });
}

exports.up = async function up(knex) {
  await Promise.all([
    updateOrder(knex, 'Reception', 1, '01420297100'),
    updateOrder(knex, 'Reception', 2, '8890355108'),
    updateOrder(knex, 'Kamal Arya', 3),
    updateOrder(knex, 'Gopal Bansal', 4),
    updateOrder(knex, 'Praveen Jain', 5),
  ]);
};

exports.down = async function down(knex) {
  await Promise.all([
    updateOrder(knex, 'Gopal Bansal', 1),
    updateOrder(knex, 'Praveen Jain', 2),
    updateOrder(knex, 'Kamal Arya', 3),
    updateOrder(knex, 'Reception', 4, '01420297100'),
    updateOrder(knex, 'Reception', 5, '8890355108'),
  ]);
};
