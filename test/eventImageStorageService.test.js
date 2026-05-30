const test = require('node:test');
const assert = require('node:assert/strict');

const eventImageStorageService = require('../src/services/eventImageStorageService');

test('getEventImageNameFromUrl extracts event image file names from S3 URLs', () => {
  assert.equal(
    eventImageStorageService.getEventImageNameFromUrl(
      'https://t3.storageapi.dev/app-images/events/de9a74ab-e574-47c5-b867-777a25147335.jpg'
    ),
    'de9a74ab-e574-47c5-b867-777a25147335.jpg'
  );
});

test('getEventImageProxyUrl returns API-hosted event image URL', () => {
  assert.equal(
    eventImageStorageService.getEventImageProxyUrl(
      'https://t3.storageapi.dev/app-images/events/de9a74ab-e574-47c5-b867-777a25147335.jpg',
      'https://api.example.com/api/'
    ),
    'https://api.example.com/api/events/images/de9a74ab-e574-47c5-b867-777a25147335.jpg'
  );
});
