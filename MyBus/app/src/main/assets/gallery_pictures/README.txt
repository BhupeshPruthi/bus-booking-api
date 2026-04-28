Gallery pictures (home screen carousel)

Images live in: app/src/main/res/drawable-nodpi/
  Name files: gallery_01.jpeg, gallery_02.jpg, ... (lowercase, underscores OK; use .jpg/.jpeg for JPEG and .png for real PNG — JPEG saved as .png breaks release AAPT2.)

To add a picture:
  1. Copy the image into drawable-nodpi/ with the next name (e.g. gallery_11.jpg).
  2. Register it in GalleryImages.kt (drawableIds list).

To remove:
  1. Delete the file from drawable-nodpi/.
  2. Remove its id from GalleryImages.kt.

This folder is kept so the path "gallery_pictures" stays documented; bitmaps are in drawable-nodpi for Compose painterResource.
