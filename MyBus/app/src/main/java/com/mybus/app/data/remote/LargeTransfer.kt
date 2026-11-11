package com.mybus.app.data.remote

/**
 * Request tag (Retrofit `@Tag`) for big uploads and downloads such as Aarti & Path PDFs.
 * Debug logging prints only their headers, so a 20 MB body is not copied into memory and
 * logcat, and upload progress is not run twice.
 */
object LargeTransfer
