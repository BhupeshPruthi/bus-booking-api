const { db } = require('../config/database');
const { NotFoundError } = require('../utils/errors');
const eventImageStorageService = require('./eventImageStorageService');

class EventService {
  async createEvent(adminUserId, data, imageFile = null) {
    let uploadedImage = null;

    try {
      uploadedImage = await eventImageStorageService.uploadEventImage(imageFile);

      const eventData = {
        header: data.header,
        sub_header: data.subHeader,
        event_at: data.eventDate,
        status: 'scheduled',
        created_by: adminUserId,
      };
      if (uploadedImage?.url) {
        eventData.image_url = uploadedImage.url;
      }

      const [event] = await db('events')
        .insert(eventData)
        .returning('*');

      return this.getEventById(event.id);
    } catch (error) {
      if (uploadedImage?.key) {
        await eventImageStorageService.deleteEventImage(uploadedImage.key).catch(() => {});
      }
      throw error;
    }
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
      imageUrl: event.image_url || null,
      createdAt: event.created_at,
    };
  }
}

module.exports = new EventService();
