/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.up = async function (knex) {
  await knex.schema.alterTable('pickup_points', (table) => {
    table.uuid('bus_id').nullable().references('id').inTable('buses').onDelete('CASCADE');
    table.index('bus_id');
  });

  // Drop the old unique constraint on (route_id, sequence)
  await knex.schema.alterTable('pickup_points', (table) => {
    table.dropUnique(['route_id', 'sequence']);
  });

  // Migrate existing data: for each bus, copy its route's pickup points as bus-specific rows
  const buses = await knex('buses').select('id', 'route_id');

  for (const bus of buses) {
    const routeStops = await knex('pickup_points')
      .where('route_id', bus.route_id)
      .whereNull('bus_id')
      .orderBy('sequence', 'asc');

    if (routeStops.length > 0) {
      const newStops = routeStops.map((stop) => ({
        route_id: bus.route_id,
        bus_id: bus.id,
        name: stop.name,
        address: stop.address,
        sequence: stop.sequence,
      }));
      await knex('pickup_points').insert(newStops);
    }
  }

  // Update bookings to point to the bus-specific pickup points
  const bookings = await knex('bookings').select('id', 'bus_id', 'pickup_point_id');
  for (const booking of bookings) {
    const oldPoint = await knex('pickup_points').where('id', booking.pickup_point_id).first();
    if (oldPoint) {
      const busPoint = await knex('pickup_points')
        .where('bus_id', booking.bus_id)
        .where('name', oldPoint.name)
        .where('sequence', oldPoint.sequence)
        .first();
      if (busPoint) {
        await knex('bookings').where('id', booking.id).update({ pickup_point_id: busPoint.id });
      }
    }
  }

  // Delete the old route-only pickup points (bus_id IS NULL)
  await knex('pickup_points').whereNull('bus_id').del();

  // Make bus_id NOT NULL now that all rows have it
  await knex.schema.alterTable('pickup_points', (table) => {
    table.uuid('bus_id').notNullable().alter();
  });

  // Add new unique constraint on (bus_id, sequence)
  await knex.schema.alterTable('pickup_points', (table) => {
    table.unique(['bus_id', 'sequence']);
  });
};

/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.down = async function (knex) {
  await knex.schema.alterTable('pickup_points', (table) => {
    table.dropUnique(['bus_id', 'sequence']);
    table.dropIndex('bus_id');
    table.dropColumn('bus_id');
    table.unique(['route_id', 'sequence']);
  });
};
