/**
 * @param { import("knex").Knex } knex
 * @returns { Promise<void> }
 */
exports.seed = async function (knex) {
  // Clear existing data (in reverse order of dependencies)
  await knex('payments').del();
  await knex('bookings').del();
  await knex('buses').del();
  await knex('pickup_points').del();
  await knex('routes').del();
  await knex('refresh_tokens').del();
  await knex('users').del();

  // Superuser is identified by SUPER_USER_MOBILE / SUPER_USER_EMAIL (role stays consumer)
  const [adminUser] = await knex('users')
    .insert({
      mobile: '9513333839',
      role: 'consumer',
      name: 'Super Admin',
      email: 'superadmin@busbooking.com',
    })
    .returning('*');

  console.log('Superuser created:', adminUser.mobile);

  // Create sample routes
  const [route1] = await knex('routes')
    .insert({
      name: 'Delhi to Jaipur',
      source: 'Delhi',
      destination: 'Jaipur',
      is_active: true,
    })
    .returning('*');

  const [route2] = await knex('routes')
    .insert({
      name: 'Mumbai to Pune',
      source: 'Mumbai',
      destination: 'Pune',
      is_active: true,
    })
    .returning('*');

  console.log('Sample routes created');

  // Create pickup points for Delhi-Jaipur route
  await knex('pickup_points').insert([
    { route_id: route1.id, name: 'Kashmere Gate ISBT', address: 'Kashmere Gate, Delhi', sequence: 1 },
    { route_id: route1.id, name: 'Dhaula Kuan', address: 'Dhaula Kuan, Delhi', sequence: 2 },
    { route_id: route1.id, name: 'Gurugram Bus Stand', address: 'Old Bus Stand, Gurugram', sequence: 3 },
  ]);

  // Create pickup points for Mumbai-Pune route
  await knex('pickup_points').insert([
    { route_id: route2.id, name: 'Dadar Station', address: 'Dadar East, Mumbai', sequence: 1 },
    { route_id: route2.id, name: 'Vashi Bus Depot', address: 'Vashi, Navi Mumbai', sequence: 2 },
    { route_id: route2.id, name: 'Panvel Bus Stand', address: 'Panvel, Navi Mumbai', sequence: 3 },
  ]);

  console.log('Sample pickup points created');

  // Create sample buses (departing tomorrow)
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  tomorrow.setHours(6, 0, 0, 0);

  const dayAfterTomorrow = new Date();
  dayAfterTomorrow.setDate(dayAfterTomorrow.getDate() + 2);
  dayAfterTomorrow.setHours(8, 0, 0, 0);

  await knex('buses').insert([
    {
      route_id: route1.id,
      bus_name: 'Volvo AC Sleeper - Morning',
      total_seats: 40,
      price: 850.00,
      departure_time: tomorrow,
      booking_hold_minutes: 180,
      status: 'scheduled',
    },
    {
      route_id: route1.id,
      bus_name: 'Volvo AC Semi-Sleeper - Evening',
      total_seats: 45,
      price: 750.00,
      departure_time: new Date(tomorrow.getTime() + 14 * 60 * 60 * 1000), // 8 PM
      booking_hold_minutes: 120,
      status: 'scheduled',
    },
    {
      route_id: route2.id,
      bus_name: 'Shivneri AC',
      total_seats: 35,
      price: 450.00,
      departure_time: dayAfterTomorrow,
      booking_hold_minutes: 240,
      status: 'scheduled',
    },
  ]);

  console.log('Sample buses created');
  console.log('\n========================================');
  console.log('Seed data created successfully!');
  console.log('Superuser mobile: 9513333839');
  console.log('========================================\n');
};
