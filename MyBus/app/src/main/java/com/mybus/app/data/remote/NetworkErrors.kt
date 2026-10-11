package com.mybus.app.data.remote

import java.io.IOException
import java.net.SocketTimeoutException

/** A message users can act on, instead of raw exception text such as "Unable to resolve host …". */
fun networkErrorMessage(error: Throwable): String = when (error) {
    is SocketTimeoutException -> "The server is taking too long to respond. Please try again."
    is IOException -> "Can't reach the server. Check your internet connection, or try Wi-Fi, and tap Retry."
    else -> "Something went wrong. Please try again."
}
