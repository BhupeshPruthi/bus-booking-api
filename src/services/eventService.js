const { db } = require('../config/database');
const { NotFoundError, ValidationError } = require('../utils/errors');
const eventImageStorageService = require('./eventImageStorageService');

function normalizeEventDate(value) {
  const eventDate = new Date(value);
  if (Number.isNaN(eventDate.getTime())) {
    throw new ValidationError('Invalid event date', [
      { field: 'eventDate', message: 'Event date must be a valid ISO date' },
    ]);
  }

  const todayStartUtc = new Date();
  todayStartUtc.setUTCHours(0, 0, 0, 0);
  if (eventDate < todayStartUtc) {
    throw new ValidationError('Event date must be today or a future date', [
      { field: 'eventDate', message: 'Event date must be today or a future date' },
    ]);
  }

  return eventDate;
}

class EventService {
  async createEvent(adminUserId, data, imageFile = null, options = {}) {
    let uploadedImage = null;

    try {
      const eventDate = normalizeEventDate(data.eventDate);
      uploadedImage = await eventImageStorageService.uploadEventImage(imageFile);

      const eventData = {
        header: data.header,
        sub_header: data.subHeader,
        event_at: eventDate,
        status: 'scheduled',
        created_by: adminUserId,
      };
      if (uploadedImage?.url) {
        eventData.image_url = uploadedImage.url;
      }

      const [event] = await db('events')
        .insert(eventData)
        .returning('*');

      return this.getEventById(event.id, options);
    } catch (error) {
      if (uploadedImage?.key) {
        await eventImageStorageService.deleteEventImage(uploadedImage.key).catch(() => {});
      }
      throw error;
    }
  }

  async getUpcomingEvents(options = {}) {
    const now = new Date();
    const events = await db('events')
      .where('status', 'scheduled')
      .andWhere('event_at', '>', now)
      .orderBy('event_at', 'asc');

    return events.map((e) => this.formatEvent(e, options));
  }

  async getEventById(eventId, options = {}) {
    const event = await db('events').where('id', eventId).first();
    if (!event) throw new NotFoundError('Event');
    return this.formatEvent(event, options);
  }

  formatEvent(event, options = {}) {
    const storedImageUrl = event.image_url || null;
    return {
      id: event.id,
      header: event.header,
      subHeader: event.sub_header,
      eventDate: event.event_at,
      status: event.status,
      imageUrl: options.imageBaseUrl
        ? eventImageStorageService.getEventImageProxyUrl(storedImageUrl, options.imageBaseUrl)
        : storedImageUrl,
      createdAt: event.created_at,
    };
  }
}

module.exports = new EventService();
