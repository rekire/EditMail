package eu.rekisoft.android.editmail

import android.content.Context
import android.os.Handler
import android.text.Editable
import androidx.lifecycle.Lifecycle
import eu.rekisoft.android.util.ThreadingHelper
import org.junit.Assert.*
import org.junit.Test
import org.minidns.dnsmessage.DnsMessage.RESPONSE_CODE
import org.minidns.dnsmessage.Question
import org.minidns.dnssec.DnssecValidationFailedException
import org.minidns.hla.DnssecResolverApi
import org.minidns.record.MX
import org.minidns.record.Record
import java.io.IOException

import org.mockito.ArgumentCaptor

import org.mockito.Mockito.*

class DnsTest {
    enum class AddressStatus2 {
        valid,
        notRegistered,
        noMxRecord,
        unknown
    }

    @Test
    fun evaluating() {
        val cases = mapOf(
            "github.com" to AddressStatus2.valid,
            "github.io" to AddressStatus2.noMxRecord,
            "github.xxx" to AddressStatus2.notRegistered,
        )
        cases.forEach { (domain, expected) ->
            val result = checkDomain(domain)
            assertEquals("Unexpected result for $domain", expected, result)
        }
    }

    @Test
    fun doh() {
        println(DohResolver().query(Question("github.xxx", Record.TYPE.MX)))
    }

    fun <T> capture(argumentCaptor: ArgumentCaptor<T>): T = argumentCaptor.capture()
    fun <T> ArgumentCaptor<T>.captureNonNull(): T = requireNotNull(capture())

    @Test
    fun mxValidatorTest() {
        val domain = mock(Editable::class.java)
        `when`(domain.toString()).thenReturn("example@gmail.com")
        val mockHandler = mock(Handler::class.java)
        `when`(mockHandler.postDelayed(any(), anyLong())).thenAnswer { invocation ->
            println("TEST")
            val runnable = invocation.arguments[0] as Runnable
            runnable.run()
            true
        }
        ThreadingHelper.mockHandler = mockHandler
        ThreadingHelper.mockIsOnMainThread = true
        val validator = spy(MxValidator.Builder().apply {
            context = mock(Context::class.java)
            lifecycle = mock(Lifecycle::class.java)
            resolver = { domain ->
                println("got request for $domain")
                MxValidator.ResolverResult(1, false)
            }
        }.build())
        validator.afterTextChanged(domain)
        val statusArg : ArgumentCaptor<AddressStatus> = ArgumentCaptor.forClass(AddressStatus::class.java)
        val mailArg : ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)
        //verify(validator).updateStatus(statusArg.captureNonNull(), mailArg.capture())
        verify(validator).updateStatus(statusArg.capture(), mailArg.capture())
        assertEquals(AddressStatus.valid, statusArg.value)
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

    private fun checkDomain(domain: String) : AddressStatus2 {
        return try {
            val result = DnssecResolverApi.INSTANCE.resolve<MX>(Question(domain, Record.TYPE.MX))
            when {
                result.wasSuccessful() && result.answersOrEmptySet.isEmpty() -> AddressStatus2.noMxRecord
                result.wasSuccessful() && result.answersOrEmptySet.isNotEmpty() -> AddressStatus2.valid
                result.responseCode == RESPONSE_CODE.NX_DOMAIN -> AddressStatus2.notRegistered
                else -> AddressStatus2.unknown
            }
        } catch (err: DnssecValidationFailedException) {
            println("Error: ${err.message}")
            err.printStackTrace()
            AddressStatus2.unknown
        } catch (t: IOException) {
            AddressStatus2.unknown
        }
    }
}