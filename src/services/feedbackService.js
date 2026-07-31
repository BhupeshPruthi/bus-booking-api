const { db } = require('../config/database');

function formatFeedback(row) {
  return {
    id: row.id,
    message: row.message,
    createdAt: row.created_at,
    submittedBy: {
      id: row.user_id,
      name: row.user_name || null,
      email: row.user_email || null,
      phone: row.user_phone || null,
    },
  };
}

async function create(userId, message, database = db) {
  const [row] = await database('feedback')
    .insert({
      user_id: userId,
      message: String(message).trim(),
    })
    .returning(['id', 'user_id', 'message', 'created_at']);

  return {
    id: row.id,
    message: row.message,
    createdAt: row.created_at,
  };
}

async function list(filters = {}, database = db) {
  const page = Math.max(1, Number(filters.page) || 1);
  const limit = Math.min(100, Math.max(1, Number(filters.limit) || 20));
  const base = database('feedback as f')
    .join('users as u', 'f.user_id', 'u.id');

  const countRow = await base.clone().count('f.id as count').first();
  const rows = await base.clone()
    .select(
      'f.id',
      'f.user_id',
      'f.message',
      'f.created_at',
      'u.name as user_name',
      'u.email as user_email',
      'u.mobile as user_phone'
    )
    .orderBy('f.created_at', 'desc')
    .offset((page - 1) * limit)
    .limit(limit);

  return {
    items: rows.map(formatFeedback),
    page,
    limit,
    total: Number(countRow?.count || 0),
  };
}

module.exports = {
  create,
  list,
  formatFeedback,
};
