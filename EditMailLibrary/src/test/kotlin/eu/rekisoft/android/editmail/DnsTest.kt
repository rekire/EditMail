package eu.rekisoft.android.editmail

import org.junit.Assert.*
import org.junit.Test
import org.minidns.dnsmessage.DnsMessage.RESPONSE_CODE
import org.minidns.dnsmessage.Question
import org.minidns.dnssec.DnssecValidationFailedException
import org.minidns.hla.DnssecResolverApi
import org.minidns.record.MX
import org.minidns.record.Record
import java.io.IOException

class DnsTest {
    enum class AddressStatus {
        valid,
        notRegistered,
        noMxRecord,
        unknown
    }

        @Test
    fun evaluating() {
        val cases = mapOf(
            "github.com" to AddressStatus.valid,
            "github.io" to AddressStatus.noMxRecord,
            "github.xxx" to AddressStatus.notRegistered,
        )
        cases.forEach { (domain, expected) ->
            val result = checkDomain(domain)
            assertEquals("Unexpected result for $domain", expected, result)
        }
    }

    private fun checkDomain(domain: String) : AddressStatus {
        return try {
            val result = DnssecResolverApi.INSTANCE.resolve<MX>(Question(domain, Record.TYPE.MX))
            when {
                result.wasSuccessful() && result.answersOrEmptySet.isEmpty() -> AddressStatus.noMxRecord
                result.wasSuccessful() && result.answersOrEmptySet.isNotEmpty() -> AddressStatus.valid
                result.responseCode == RESPONSE_CODE.NX_DOMAIN -> AddressStatus.notRegistered
                else -> AddressStatus.unknown
            }
        } catch (err: DnssecValidationFailedException) {
            println("Error: ${err.message}")
            err.printStackTrace()
            AddressStatus.unknown
        } catch (t: IOException) {
            AddressStatus.unknown
        }
    }
}