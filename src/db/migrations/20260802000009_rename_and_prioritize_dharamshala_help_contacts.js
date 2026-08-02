const DHARAMSHALA_TITLE = 'Dharamshala booking and services';
const STAY_TITLE = 'Stay booking and services';

async function applyContactOrder(knex, orders) {
  await Promise.all(
    orders.map(({ contactName, phone, displayOrder }) => {
      const query = knex('help_contacts').where('contact_name', contactName);
      if (phone) query.where('phone', phone);
      return query.update({ display_order: displayOrder });
    })
  );
}

exports.up = async function up(knex) {
  await knex('help_contacts')
    .where('category_code', 'stay')
    .update({ title: DHARAMSHALA_TITLE });

  await knex('help_contacts').insert([
    {
      category_code: 'stay',
      title: DHARAMSHALA_TITLE,
      contact_name: 'Reception',
      phone: '01420297100',
      display_order: 4,
    },
    {
      category_code: 'stay',
      title: DHARAMSHALA_TITLE,
      contact_name: 'Reception',
      phone: '8890355108',
      display_order: 5,
    },
  ]);

  await applyContactOrder(knex, [
    { contactName: 'Gopal Bansal', displayOrder: 1 },
    { contactName: 'Praveen Jain', displayOrder: 2 },
    { contactName: 'Kamal Arya', displayOrder: 3 },
    { contactName: 'Reception', phone: '01420297100', displayOrder: 4 },
    { contactName: 'Reception', phone: '8890355108', displayOrder: 5 },
    { contactName: 'Pankaj Goyal', displayOrder: 6 },
    { contactName: 'Pawan Bhutani', displayOrder: 7 },
    { contactName: 'Chirag Mehta', displayOrder: 8 },
    { contactName: 'Mona Mehta', displayOrder: 9 },
    { contactName: 'Guru Ji', displayOrder: 10 },
  ]);
};

exports.down = async function down(knex) {
  await knex('help_contacts')
    .where('category_code', 'stay')
    .whereIn('phone', ['01420297100', '8890355108'])
    .del();

  await knex('help_contacts')
    .where('category_code', 'stay')
    .update({ title: STAY_TITLE });

  await applyContactOrder(knex, [
    { contactName: 'Pankaj Goyal', displayOrder: 1 },
    { contactName: 'Pawan Bhutani', displayOrder: 2 },
    { contactName: 'Chirag Mehta', displayOrder: 3 },
    { contactName: 'Mona Mehta', displayOrder: 4 },
    { contactName: 'Gopal Bansal', displayOrder: 5 },
    { contactName: 'Praveen Jain', displayOrder: 6 },
    { contactName: 'Kamal Arya', displayOrder: 7 },
    { contactName: 'Guru Ji', displayOrder: 8 },
  ]);
};
