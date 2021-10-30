package eu.rekisoft.android.editmail

import android.content.Context
import android.os.Handler
import androidx.lifecycle.Lifecycle
import eu.rekisoft.android.util.ThreadingHelper
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.minidns.dnsmessage.DnsMessage.RESPONSE_CODE
import org.minidns.dnsmessage.Question
import org.minidns.dnssec.DnssecValidationFailedException
import org.minidns.hla.DnssecResolverApi
import org.minidns.record.MX
import org.minidns.record.Record
import java.io.IOException

import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.*

class MxValidatorTest {
    // Extended MxValidator implementation to log the statusUpdates and dnsRequests
    private class LoggingMxValidator(resolver : Resolver) : MxValidator(setup()) {
        val statusUpdates = mutableListOf<Pair<AddressStatus, String?>>()
        val dnsRequests = mutableListOf<String>()

        init {
            resolv = { domain ->
                dnsRequests += domain
                resolver(domain)
            }
        }

        override fun updateStatus(status: AddressStatus, mail: String?) {
            super.updateStatus(status, mail)
            statusUpdates += status to mail
        }

        companion object {
            private fun setup() = Builder().apply {
                context = mock(Context::class.java)
                lifecycle = mock(Lifecycle::class.java)
            }
        }
    }

    // Setup LazyWorker for testing: Execute all tasks directly without delays
    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            val mockHandler = mock(Handler::class.java)
            val runFirstArg = { invocation: InvocationOnMock ->
                val runnable = invocation.arguments[0] as Runnable
                runnable.run()
                true
            }
            `when`(mockHandler.postDelayed(any(), anyLong())).thenAnswer(runFirstArg)
            `when`(mockHandler.post(any())).thenAnswer(runFirstArg)
            ThreadingHelper.mockHandler = mockHandler
            ThreadingHelper.mockIsOnMainThread = true
        }
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

    @Test
    fun doh() {
        val result = DohResolver().query(Question("gmail.com", Record.TYPE.MX))
        assertTrue(result.wasSuccessful())
        assertTrue(result.response.answerSection.size > 0)
    }

    @Test
    fun wellKnownDomain() {
        val validator = LoggingMxValidator { domain ->
            throw AssertionError("No request for well known hosts expected, but got one for $domain")
        }
        validator.validateMailAddress("example@gmail.com")
        assertEquals(0, validator.dnsRequests.size)
        assertEquals(1, validator.statusUpdates.size)
        assertEquals(AddressStatus.valid, validator.statusUpdates.first().first)
        assertNull(validator.statusUpdates.first().second)
    }

    @Test
    fun customDomain() {
        val validator = LoggingMxValidator { domain ->
            throw AssertionError("No request for custom hosts expected, but got one for $domain")
        }
        MxValidator.customDomains += "evil.corp.internal"
        validator.validateMailAddress("example@evil.corp.internal")
        assertEquals(0, validator.dnsRequests.size)
        assertEquals(1, validator.statusUpdates.size)
        assertEquals(AddressStatus.valid, validator.statusUpdates.first().first)
        assertNull(validator.statusUpdates.first().second)
    }

    @Test
    fun unknownExistingDomain() {
        val validator = LoggingMxValidator { domain ->
            assertEquals("existing.com", domain)
            MxValidator.ResolverResult(5, false)
        }

        validator.validateMailAddress("example@existing.com")

        assertEquals(1, validator.dnsRequests.size)
        assertEquals(1, validator.statusUpdates.size)
        assertEquals(AddressStatus.valid, validator.statusUpdates.first().first)
        assertNull(validator.statusUpdates.first().second)
    }

    @Test
    fun typoDomain() {
        val validator = LoggingMxValidator { domain ->
            assertEquals("gnail.com", domain)
            MxValidator.ResolverResult(0, true)
        }

        validator.validateMailAddress("example@gnail.com")

        assertEquals(1, validator.dnsRequests.size)
        assertEquals(1, validator.statusUpdates.size)
        assertEquals(AddressStatus.typoDetected, validator.statusUpdates.first().first)
        assertEquals("gmail.com", validator.statusUpdates.first().second)
    }

    @Test
    fun existingTypoDomain() {
        val validator = LoggingMxValidator { domain ->
            assertEquals("gnail.com", domain)
            MxValidator.ResolverResult(0, false)
        }

        validator.validateMailAddress("example@gnail.com")

        assertEquals(1, validator.dnsRequests.size)
        assertEquals(1, validator.statusUpdates.size)
        assertEquals(AddressStatus.typoDetected, validator.statusUpdates.first().first)
        assertEquals("gmail.com", validator.statusUpdates.first().second)
    }

    @Test
    fun nonExistingDomain() {
        val validator = LoggingMxValidator { domain ->
            assertEquals("non-existend.com", domain)
            MxValidator.ResolverResult(0, true)
        }
        validator.validateMailAddress("example@non-existend.com")

        assertEquals(1, validator.dnsRequests.size)
        assertEquals(1, validator.statusUpdates.size)
        assertEquals(AddressStatus.notRegistered, validator.statusUpdates.first().first)
        assertEquals(null, validator.statusUpdates.first().second)
    }

    @Test
    fun distanceCheck() {
        val wellKnownDomains = arrayOf(
            "gmail.com", "yahoo.com", "hotmail.com", "aol.com", "hotmail.co.uk", "hotmail.fr",
            "msn.com", "yahoo.fr", "wanadoo.fr", "orange.fr", "comcast.net", "yahoo.co.uk",
            "yahoo.com.br", "yahoo.co.in", "live.com", "rediffmail.com", "free.fr", "gmx.de",
            "web.de", "yandex.ru", "ymail.com", "libero.it", "outlook.com", "uol.com.br",
            "bol.com.br", "mail.ru", "cox.net", "hotmail.it", "sbcglobal.net", "sfr.fr", "live.fr",
            "verizon.net", "live.co.uk", "googlemail.com", "yahoo.es", "ig.com.br", "live.nl",
            "bigpond.com", "terra.com.br", "yahoo.it", "neuf.fr", "yahoo.de", "alice.it",
            "rocketmail.com", "att.net", "laposte.net", "facebook.com", "bellsouth.net", "yahoo.in",
            "hotmail.es", "charter.net", "yahoo.ca", "yahoo.com.au", "rambler.ru", "hotmail.de",
            "tiscali.it", "shaw.ca", "yahoo.co.jp", "sky.com", "earthlink.net", "optonline.net",
            "freenet.de", "t-online.de", "aliceadsl.fr", "virgilio.it", "home.nl", "qq.com",
            "telenet.be", "me.com", "yahoo.com.ar", "tiscali.co.uk", "yahoo.com.mx", "voila.fr",
            "gmx.net", "mail.com", "planet.nl", "tin.it", "live.it", "ntlworld.com", "arcor.de",
            "yahoo.co.id", "frontiernet.net", "hetnet.nl", "live.com.au", "yahoo.com.sg",
            "zonnet.nl", "club-internet.fr", "juno.com", "optusnet.com.au", "blueyonder.co.uk",
            "bluewin.ch", "skynet.be", "sympatico.ca", "windstream.net", "mac.com",
            "centurytel.net", "chello.nl", "live.ca", "aim.com", "bigpond.net.au")

        val testCases = mapOf(
            "xmail.com" to "gmail.com",
            "wed.de" to "web.de",
            "live.da" to "live.ca",
            "mail.tu" to "mail.ru",
            "aol.con" to "aol.com",
            "example.com" to null,
            "coox.net" to "cox.net",
            "ain.con" to null,
        )

        testCases.forEach { (input, expected) ->
            assertEquals("Expected $expected for input $input", expected, wellKnownDomains.firstOrNull { MxValidator.damerauLevenshteinDistance(input, it, 128) == 1})
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