const CATEGORY_UNIQUE_CONSTRAINT = 'help_contacts_category_code_unique';

const HELP_CONTACTS = [
  {
    category_code: 'bus',
    title: 'Bus booking and services',
    contact_name: 'Pankaj Goyal',
    phone: '9811022926',
    display_order: 1,
  },
  {
    category_code: 'bus',
    title: 'Bus booking and services',
    contact_name: 'Pawan Bhutani',
    phone: '9891076134',
    display_order: 2,
  },
  {
    category_code: 'pooja',
    title: 'Pooja token booking',
    contact_name: 'Chirag Mehta',
    phone: '9899572696',
    display_order: 3,
  },
  {
    category_code: 'pooja',
    title: 'Pooja token booking',
    contact_name: 'Mona Mehta',
    phone: '8920277179',
    display_order: 4,
  },
  {
    category_code: 'emergency',
    title: 'Emergency',
    contact_name: 'Guru Ji',
    phone: '9911055108',
    display_order: 5,
  },
  {
    category_code: 'stay',
    title: 'Stay booking and services',
    contact_name: 'Gopal Bansal',
    phone: '9212666050',
    display_order: 6,
  },
  {
    category_code: 'stay',
    title: 'Stay booking and services',
    contact_name: 'Praveen Jain',
    phone: '9690696969',
    display_order: 7,
  },
  {
    category_code: 'stay',
    title: 'Stay booking and services',
    contact_name: 'Kamal Arya',
    phone: '9990231313',
    display_order: 8,
  },
];

exports.up = async function up(knex) {
  await knex.raw(`
    ALTER TABLE help_contacts
      DROP CONSTRAINT IF EXISTS ${CATEGORY_UNIQUE_CONSTRAINT}
  `);
  await knex('help_contacts').del();
  await knex('help_contacts').insert(HELP_CONTACTS);
};

exports.down = async function down(knex) {
  await knex('help_contacts').del();
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
  await knex.raw(`
    ALTER TABLE help_contacts
      ADD CONSTRAINT ${CATEGORY_UNIQUE_CONSTRAINT} UNIQUE (category_code)
  `);
};
