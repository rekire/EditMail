package eu.rekisoft.android.editmail

import okhttp3.OkHttpClient
import okhttp3.Request
import org.minidns.DnsClient
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsqueryresult.DnsQueryResult
import org.minidns.util.Base64
import java.io.ByteArrayOutputStream
import java.net.Inet6Address
import java.net.NetworkInterface

internal class DohResolver : DnsClient() {
    private val wellKnownV4Servers = listOf(
        // Cloudflare
        "1.1.1.1", "1.0.0.1",
        // Google Public DNS
        "8.8.8.8", "8.8.4.4",
        // Quad9
        "9.9.9.9", "149.112.112.112",
        // Digitale Gesellschaft Schweiz
        // "185.95.218.42", "185.95.218.43",
    )
    private val wellKnownV6Servers = listOf(
        // Cloudflare
        "2606:4700:4700::1111", "2606:4700:4700::1001",
        // Google Public DNS
        "2001:4860:4860::8888", "2001:4860:4860::8844",
        // Quad9
        "2620:fe::fe", "2620:fe::9",
        // Digitale Gesellschaft Schweiz
        // "2a05:fc84::42", "2a05:fc84::43",
    )

    override fun query(queryBuilder: DnsMessage.Builder): DnsQueryResult {
        val q = queryBuilder.setId(0).build()
        val output = ByteArrayOutputStream()
        q.writeTo(output, false)

        val client = OkHttpClient()
        val query = Base64.encodeToString(output.toByteArray()).trimEnd('=')
        val host = if (hasInet6) {
            "[${wellKnownV6Servers.random()}]"
        } else {
            wellKnownV4Servers.random()
        }
        println("Using host $host")
        val request = Request.Builder()
            .url("https://$host/dns-query?dns=$query")
            .header("Content-Type", "application/dns-message")
            .build()

        val result = client.newCall(request).execute()
        return DohResult(q, result.body?.bytes())
    }

    val hasInet6: Boolean
        get() {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val ni = networkInterfaces.nextElement()
                for (interfaceAddress in ni.interfaceAddresses) {
                    if (interfaceAddress.address is Inet6Address) {
                        return true
                    }
                }
            }
            return false
        }
}