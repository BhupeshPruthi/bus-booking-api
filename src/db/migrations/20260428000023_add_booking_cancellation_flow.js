const BOOKING_STATUSES = [
  'pending',
  'payment_uploaded',
  'confirmed',
  'cancellation_requested',
  'rejected',
  'expired',
  'cancelled',
];

function quoteIdent(value) {
  return `"${String(value).replace(/"/g, '""')}"`;
}

function quoteLiteral(value) {
  return `'${String(value).replace(/'/g, "''")}'`;
}

async function getBookingStatusType(knex) {
  const result = await knex.raw(`
    SELECT
      type_namespace.nspname AS type_schema,
      status_type.typname AS type_name,
      status_type.typtype AS type_kind
    FROM pg_attribute attr
    JOIN pg_class booking_table
      ON booking_table.oid = attr.attrelid
    JOIN pg_namespace table_namespace
      ON table_namespace.oid = booking_table.relnamespace
    JOIN pg_type status_type
      ON status_type.oid = attr.atttypid
    JOIN pg_namespace type_namespace
      ON type_namespace.oid = status_type.typnamespace
    WHERE table_namespace.nspname = current_schema()
      AND booking_table.relname = 'bookings'
      AND attr.attname = 'status'
      AND attr.attisdropped = false
    LIMIT 1
  `);

  return result.rows[0] || null;
}

async function refreshTextStatusCheck(knex) {
  const result = await knex.raw(`
    SELECT constraint_info.conname
    FROM pg_constraint constraint_info
    JOIN pg_class booking_table
      ON booking_table.oid = constraint_info.conrelid
    JOIN pg_namespace table_namespace
      ON table_namespace.oid = booking_table.relnamespace
    WHERE table_namespace.nspname = current_schema()
      AND booking_table.relname = 'bookings'
      AND constraint_info.contype = 'c'
      AND pg_get_constraintdef(constraint_info.oid) ILIKE '%status%'
  `);

  for (const row of result.rows) {
    await knex.raw(
      `ALTER TABLE ${quoteIdent('bookings')} DROP CONSTRAINT ${quoteIdent(row.conname)}`
    );
  }

  const statusList = BOOKING_STATUSES.map(quoteLiteral).join(', ');
  await knex.raw(
    `ALTER TABLE ${quoteIdent('bookings')}
      ADD CONSTRAINT ${quoteIdent('bookings_status_check')}
      CHECK (${quoteIdent('status')} IN (${statusList}))
      NOT VALID`
  );
}

async function ensureStatusAllowsCancellationRequested(knex) {
  const statusType = await getBookingStatusType(knex);
  if (!statusType) return;

  if (statusType.type_kind === 'e') {
    const enumName = `${quoteIdent(statusType.type_schema)}.${quoteIdent(statusType.type_name)}`;
    await knex.raw(`ALTER TYPE ${enumName} ADD VALUE IF NOT EXISTS 'cancellation_requested'`);
    return;
  }

  await refreshTextStatusCheck(knex);
}

/**
 * Add a pending cancellation state and audit fields for user/admin cancellation flows.
 *
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function (knex) {
  await ensureStatusAllowsCancellationRequested(knex);

  await knex.raw(`
    ALTER TABLE bookings
      ADD COLUMN IF NOT EXISTS cancellation_requested_at timestamp NULL,
      ADD COLUMN IF NOT EXISTS cancellation_reason text NULL,
      ADD COLUMN IF NOT EXISTS cancellation_rejection_reason text NULL,
      ADD COLUMN IF NOT EXISTS cancellation_previous_status varchar(50) NULL,
      ADD COLUMN IF NOT EXISTS cancelled_at timestamp NULL,
      ADD COLUMN IF NOT EXISTS cancelled_by uuid NULL
  `);

  await knex.raw(`
    DO $$
    BEGIN
      IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint constraint_info
        WHERE constraint_info.conrelid = 'bookings'::regclass
          AND constraint_info.contype = 'f'
          AND constraint_info.conname IN (
            'bookings_cancelled_by_fkey',
            'bookings_cancelled_by_foreign'
          )
      ) THEN
        ALTER TABLE bookings
          ADD CONSTRAINT bookings_cancelled_by_fkey
          FOREIGN KEY (cancelled_by)
          REFERENCES users(id)
          ON DELETE SET NULL;
      END IF;
    END $$;
  `);
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function (knex) {
  await knex.raw(`
    ALTER TABLE bookings
      DROP CONSTRAINT IF EXISTS bookings_cancelled_by_fkey,
      DROP CONSTRAINT IF EXISTS bookings_cancelled_by_foreign,
      DROP COLUMN IF EXISTS cancelled_by,
      DROP COLUMN IF EXISTS cancelled_at,
      DROP COLUMN IF EXISTS cancellation_previous_status,
      DROP COLUMN IF EXISTS cancellation_rejection_reason,
      DROP COLUMN IF EXISTS cancellation_reason,
      DROP COLUMN IF EXISTS cancellation_requested_at
  `);
};

exports.config = { transaction: false };
