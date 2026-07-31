const { db } = require('../config/database');

function formatHelpContact(row) {
  return {
    id: row.id,
    code: row.category_code,
    title: row.title,
    contactName: row.contact_name,
    phone: row.phone,
  };
}

async function list(database = db) {
  const rows = await database('help_contacts')
    .select('id', 'category_code', 'title', 'contact_name', 'phone')
    .where('is_active', true)
    .orderBy('display_order', 'asc');

  return rows.map(formatHelpContact);
}

module.exports = {
  list,
  formatHelpContact,
};
