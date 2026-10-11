package com.mybus.app.data.remote

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Uses the phone's normal DNS first. If the network's DNS refuses a name
 * (Jio blocks `*.up.railway.app`), retries over HTTPS with Cloudflare, then Google.
 * Both resolvers are reached by IP address, so they don't depend on the network's DNS.
 */
class FallbackDns private constructor(
    private val resolvers: List<Dns>
) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val systemError = try {
            return Dns.SYSTEM.lookup(hostname)
        } catch (error: UnknownHostException) {
            error
        }

        for (resolver in resolvers) {
            try {
                val addresses = resolver.lookup(hostname)
                if (addresses.isNotEmpty()) return addresses
            } catch (_: Exception) {
                // Try the next resolver.
            }
        }
        throw systemError
    }

    companion object {
        fun create(): FallbackDns {
            val bootstrapClient = OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            val cloudflare = DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1")
                )
                .build()

            val google = DnsOverHttps.Builder()
                .client(bootstrapClient)
                .url("https://dns.google/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("8.8.8.8"),
                    InetAddress.getByName("8.8.4.4")
                )
                .build()

            return FallbackDns(listOf(cloudflare, google))
        }
    }
}
