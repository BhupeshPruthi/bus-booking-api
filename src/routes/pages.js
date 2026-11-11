const express = require('express');
const path = require('path');

// Public web pages linked from the App Store and Play Store listings.
const router = express.Router();
const pagesDir = path.join(__dirname, '..', 'pages');

const page = (file) => (req, res) => {
  res.set('Cache-Control', 'public, max-age=3600');
  res.sendFile(path.join(pagesDir, file));
};

router.get(['/privacy', '/privacy-policy'], page('privacy.html'));
router.get('/support', page('support.html'));

module.exports = router;
