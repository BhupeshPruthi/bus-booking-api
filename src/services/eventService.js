const { db } = require('../config/database');
const { NotFoundError, ValidationError } = require('../utils/errors');

class EventService {
  async createEvent(adminUserId, data) {
    const [event] = await db('events')
      .insert({
        header: data.header,
        sub_header: data.subHeader,
        event_at: data.eventDate,
        status: 'scheduled',
        created_by: adminUserId,
      })
      .returning('*');

    return this.getEventById(event.id);
  }

  async getUpcomingEvents() {
    const now = new Date();
    const events = await db('events')
      .where('status', 'scheduled')
      .andWhere('event_at', '>', now)
      .orderBy('event_at', 'asc');

    return events.map((e) => this.formatEvent(e));
  }

  async getEventById(eventId) {
    const event = await db('events').where('id', eventId).first();
    if (!event) throw new NotFoundError('Event');
    return this.formatEvent(event);
  }

  formatEvent(event) {
    return {
      id: event.id,
      header: event.header,
      subHeader: event.sub_header,
      eventDate: event.event_at,
      status: event.status,
      createdAt: event.created_at,
    };
  }
}

module.exports = new EventService();

