const cron = require('node-cron');
const bookingService = require('../services/bookingService');
const stayService = require('../services/stayService');
const logger = require('../utils/logger');

/**
 * Cron job to expire pending bookings that have exceeded their hold time
 * Runs every 5 minutes
 */
const startExpireBookingsJob = () => {
  // Run every 5 minutes
  cron.schedule('*/5 * * * *', async () => {
    try {
      const expiredCount = await bookingService.expirePendingBookings();
      const completedStayCount = await stayService.completePastBookings();
      
      if (expiredCount > 0) {
        logger.info(`Expired ${expiredCount} pending bookings`);
      }
      if (completedStayCount > 0) {
        logger.info(`Completed ${completedStayCount} past stay bookings`);
      }
    } catch (error) {
      logger.error('Error in expire bookings job:', error);
    }
  });

  logger.info('Expire bookings cron job scheduled (every 5 minutes)');
};

module.exports = { startExpireBookingsJob };
