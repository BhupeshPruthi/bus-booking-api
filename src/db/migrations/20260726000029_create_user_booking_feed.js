/**
 * Create a read-only, canonical feed over all customer booking domains.
 * Domain tables remain the source of truth; adding a future line of business
 * only requires extending this view and the API formatter.
 */
exports.up = async function (knex) {
  await knex.raw(`
    CREATE INDEX IF NOT EXISTS bookings_user_status_created_idx
      ON bookings (user_id, status, created_at DESC);
    CREATE INDEX IF NOT EXISTS pooja_bookings_user_status_created_idx
      ON pooja_bookings (user_id, status, created_at DESC);
    CREATE INDEX IF NOT EXISTS stay_bookings_user_status_dates_idx
      ON stay_bookings (user_id, status, check_in_date, check_out_date);
  `);

  await knex.raw(`
    CREATE OR REPLACE VIEW user_booking_feed AS
    SELECT
      'bus'::text AS booking_type,
      booking.id AS booking_id,
      booking.user_id,
      booking.id::text AS reference,
      booking.status::text AS raw_status,
      CASE booking.status::text
        WHEN 'pending' THEN 'requested'
        WHEN 'payment_uploaded' THEN 'payment_submitted'
        WHEN 'confirmed' THEN 'confirmed'
        WHEN 'cancellation_requested' THEN 'cancellation_pending'
        WHEN 'rejected' THEN 'rejected'
        WHEN 'expired' THEN 'expired'
        WHEN 'cancelled' THEN 'cancelled'
        ELSE booking.status::text
      END AS normalized_status,
      bus.departure_time AS starts_at,
      COALESCE(return_bus.arrival_time, bus.arrival_time, bus.departure_time) AS ends_at,
      booking.created_at AT TIME ZONE 'UTC' AS created_at,
      booking.updated_at AT TIME ZONE 'UTC' AS updated_at,
      booking.total_amount,
      'INR'::text AS currency,
      CONCAT(route.source, ' → ', route.destination) AS title,
      bus.bus_name AS subtitle,
      jsonb_build_object(
        'busName', bus.bus_name,
        'source', route.source,
        'destination', route.destination,
        'pickupPoint', pickup.name,
        'seatCount', booking.seat_count,
        'assignedSeats', booking.assigned_seats,
        'passengerName', booking.passenger_name,
        'passengerPhone', booking.passenger_phone,
        'tripType', bus.trip_type
      ) AS details
    FROM bookings booking
    JOIN buses bus ON bus.id = booking.bus_id
    LEFT JOIN buses return_bus ON return_bus.id = bus.return_bus_id
    JOIN routes route ON route.id = bus.route_id
    JOIN pickup_points pickup ON pickup.id = booking.pickup_point_id

    UNION ALL

    SELECT
      'stay'::text AS booking_type,
      booking.id AS booking_id,
      booking.user_id,
      booking.reference,
      booking.status::text AS raw_status,
      CASE booking.status::text
        WHEN 'pending' THEN 'requested'
        WHEN 'confirmed' THEN 'confirmed'
        WHEN 'cancellation_requested' THEN 'cancellation_pending'
        WHEN 'completed' THEN 'completed'
        WHEN 'rejected' THEN 'rejected'
        WHEN 'cancelled' THEN 'cancelled'
        ELSE booking.status::text
      END AS normalized_status,
      (booking.check_in_date::timestamp + TIME '12:00') AT TIME ZONE 'Asia/Kolkata' AS starts_at,
      (booking.check_out_date::timestamp + TIME '11:00') AT TIME ZONE 'Asia/Kolkata' AS ends_at,
      booking.created_at AS created_at,
      booking.updated_at AS updated_at,
      booking.total_amount,
      'INR'::text AS currency,
      'Stay booking'::text AS title,
      COALESCE(item_summary.subtitle, 'Accommodation') AS subtitle,
      jsonb_build_object(
        'checkInDate', booking.check_in_date,
        'checkOutDate', booking.check_out_date,
        'nightCount', booking.night_count,
        'guestCount', booking.guest_count,
        'contactName', booking.contact_name,
        'contactEmail', booking.contact_email,
        'contactPhone', booking.contact_phone,
        'mattressQuantity', booking.mattress_quantity,
        'mattressTotal', booking.mattress_total,
        'items', COALESCE(item_summary.items, '[]'::jsonb),
        'rejectionReason', booking.rejection_reason
      ) AS details
    FROM stay_bookings booking
    LEFT JOIN LATERAL (
      SELECT
        STRING_AGG(item.quantity::text || ' × ' || item.unit_type_name, ', ' ORDER BY item.created_at) AS subtitle,
        JSONB_AGG(
          jsonb_build_object(
            'unitTypeCode', item.unit_type_code,
            'unitTypeName', item.unit_type_name,
            'quantity', item.quantity,
            'capacityPerUnit', item.capacity_per_unit,
            'nightlyRate', item.nightly_rate,
            'nightCount', item.night_count,
            'lineTotal', item.line_total
          )
          ORDER BY item.created_at
        ) AS items
      FROM stay_booking_items item
      WHERE item.booking_id = booking.id
    ) item_summary ON TRUE

    UNION ALL

    SELECT
      'pooja'::text AS booking_type,
      booking.id AS booking_id,
      booking.user_id,
      COALESCE('Token #' || booking.token_number::text, booking.id::text) AS reference,
      CASE
        WHEN pooja.status::text = 'cancelled' THEN 'cancelled'
        ELSE booking.status::text
      END AS raw_status,
      CASE
        WHEN pooja.status::text = 'cancelled' THEN 'cancelled'
        ELSE CASE booking.status::text
        WHEN 'confirmed' THEN 'confirmed'
        WHEN 'cancelled' THEN 'cancelled'
        ELSE booking.status::text
        END
      END AS normalized_status,
      pooja.scheduled_at AT TIME ZONE 'UTC' AS starts_at,
      (pooja.scheduled_at + INTERVAL '8 hours') AT TIME ZONE 'UTC' AS ends_at,
      booking.created_at AT TIME ZONE 'UTC' AS created_at,
      booking.updated_at AT TIME ZONE 'UTC' AS updated_at,
      NULL::numeric AS total_amount,
      NULL::text AS currency,
      CONCAT('Pooja at ', pooja.place) AS title,
      COALESCE('Token #' || booking.token_number::text, 'Token confirmed') AS subtitle,
      jsonb_build_object(
        'poojaId', booking.pooja_id,
        'place', pooja.place,
        'tokenNumber', booking.token_number,
        'memberCount', booking.member_count,
        'city', booking.city,
        'name', booking.name,
        'phone', booking.phone
      ) AS details
    FROM pooja_bookings booking
    JOIN poojas pooja ON pooja.id = booking.pooja_id;
  `);
};

exports.down = async function (knex) {
  await knex.raw('DROP VIEW IF EXISTS user_booking_feed');
  await knex.raw(`
    DROP INDEX IF EXISTS bookings_user_status_created_idx;
    DROP INDEX IF EXISTS pooja_bookings_user_status_created_idx;
    DROP INDEX IF EXISTS stay_bookings_user_status_dates_idx;
  `);
};
