package eu.rekisoft.android.editmail

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.lifecycle.*
import com.google.android.material.textfield.TextInputLayout
import eu.rekisoft.android.util.LazyWorker
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsmessage.Question
import org.minidns.record.Record
import java.net.IDN
import java.util.*

typealias Resolver = (String) -> MxValidator.ResolverResult

class MxValidator private constructor(builder: Builder): TextWatcher {
    data class ResolverResult(val resultCount: Int, val notFound: Boolean)

    private var resolv: Resolver = builder.resolver
    private var currentInput: String? = null
    private val context: Context = builder.context
    val status: LiveData<AddressStatus> = MutableLiveData(AddressStatus.unknown)

    private var errorViewer: View? = builder.errorViewer

    private val mxLookup = LazyWorker.createLifeCycleAwareJob(builder.lifecycle) {
        val localCopy = currentInput
        localCopy?.domain?.let { domain ->
            if (hasWellknownDomain(domain)) {
                updateStatus(AddressStatus.valid)
            } else {
                val response = resolv(domain)
                when {
                    response.notFound -> updateStatus(AddressStatus.notRegistered)
                    response.resultCount == 0 -> updateStatus(AddressStatus.noMxRecord)
                    response.resultCount > 0 -> updateStatus(AddressStatus.valid)
                    else ->
                        (customDomains + wellKnownDomains).firstOrNull { user ->
                            damerauLevenshteinDistance(user, domain, 128) == 1
                        }?.let { mail ->
                            updateStatus(AddressStatus.typoDetected, mail)
                        } ?: updateStatus(AddressStatus.unknown)
                }
            }
        }
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        // remove all error states on text changes.
        updateStatus(AddressStatus.pending)
        setError(null)
    }
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
    }
    override fun afterTextChanged(s: Editable) {
        currentInput = s.toString()
        if (hasWellknownDomain(s.toString())) {
            updateStatus(AddressStatus.valid)
        } else {
            // When the change is done set the internal status to pending and invoke doCheck delayed.
            updateStatus(AddressStatus.pending)
            mxLookup.doLater(150)
        }
    }

    private fun updateStatus(status: AddressStatus, mail: String? = null) {
        //(this.status as MutableLiveData).postValue(status)
        setError(when(status) {
            AddressStatus.pending,
            AddressStatus.unknown -> null
            AddressStatus.valid -> null
            AddressStatus.notRegistered -> context.getString(R.string.email_domain_unknown)
            AddressStatus.noMxRecord -> context.getString(R.string.email_no_mx)
            AddressStatus.typoDetected -> context.getString(R.string.email_did_you_mean, mail)
            AddressStatus.wrongSchema -> context.getString(R.string.email_schema_error)
        })
    }

    private fun setError(error: String?) {
        when(errorViewer) {
            is EditText -> (errorViewer as EditText).error = error
            is TextInputLayout -> (errorViewer as TextInputLayout).error = error
        }
    }

    class Builder {
        internal lateinit var lifecycle: Lifecycle
        internal lateinit var context: Context
        internal var errorViewer: View? = null
        internal var resolver: Resolver = ::defaultResolver

        fun lifecycleOwner(lifecycleOwner: LifecycleOwner) = apply {
            lifecycle = lifecycleOwner.lifecycle
        }
        fun context(context: Context) = apply {
            this.context = context
        }
        fun errorViewer(editText: EditText) = apply {
            errorViewer = editText
            context = editText.context
            lifecycle = requireNotNull(ViewTreeLifecycleOwner.get(editText)).lifecycle
        }
        fun errorViewer(textInputLayout: TextInputLayout) = apply {
            errorViewer = textInputLayout
            context = textInputLayout.context
            lifecycle = requireNotNull(ViewTreeLifecycleOwner.get(textInputLayout)).lifecycle
        }

        private val dohResolver by lazy { DohResolver() }
        private fun defaultResolver(domain: String): ResolverResult {
            val result = dohResolver.query(Question(domain, Record.TYPE.MX))
            return when {
                result.response.responseCode == DnsMessage.RESPONSE_CODE.NX_DOMAIN -> ResolverResult(0, notFound = true)
                result.wasSuccessful() -> ResolverResult(result.response.answerSection.size, notFound = false)
                else -> ResolverResult(-1, notFound = false)
            }
        }

        fun build() = MxValidator(this)
    }

    companion object {
        private val String.domain: String?
        get() {
            val atPosition = indexOf("@")
            return if (atPosition >= 1 && length > atPosition + 1) {
                IDN.toASCII(substring(atPosition + 1).lowercase(Locale.US))
            } else null
        }

        fun hasWellknownDomain(emailAddress: String) : Boolean =
            emailAddress.domain?.let { domain ->
                wellKnownDomains.contains(domain) || customDomains.contains(domain)
            } ?: false

        val customDomains = mutableListOf<String>()

        /**
         * A small list of well known email addresses.
         */
        private val wellKnownDomains = arrayOf(
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
            "centurytel.net", "chello.nl", "live.ca", "aim.com", "bigpond.net.au"
        )

        /**
         * Calculated the Damerau-Levenshtein-Distance.
         *
         * @param a              input string one.
         * @param b              input string two.
         * @param alphabetLength the length of the alphabet.
         * @return the distance.
         * @author M. Jessup (https://stackoverflow.com/a/6035519/995926)
         */
        fun damerauLevenshteinDistance(a: String, b: String, alphabetLength: Int): Int {
            val INFINITY = a.length + b.length
            val H = Array(a.length + 2) {
                IntArray(
                    b.length + 2
                )
            }
            H[0][0] = INFINITY
            for (i in 0..a.length) {
                H[i + 1][1] = i
                H[i + 1][0] = INFINITY
            }
            for (j in 0..b.length) {
                H[1][j + 1] = j
                H[0][j + 1] = INFINITY
            }
            val DA = IntArray(alphabetLength)
            Arrays.fill(DA, 0)
            for (i in 1..a.length) {
                var DB = 0
                for (j in 1..b.length) {
                    val i1 = DA[b[j - 1].code]
                    val j1 = DB
                    val d = if (a[i - 1] == b[j - 1]) 0 else 1
                    if (d == 0) DB = j
                    H[i + 1][j + 1] = min(
                        H[i][j] + d,
                        H[i + 1][j] + 1,
                        H[i][j + 1] + 1,
                        H[i1][j1] + (i - i1 - 1) + 1 + (j - j1 - 1)
                    )
                }
                DA[a[i - 1].code] = i
            }
            return H[a.length + 1][b.length + 1]
        }

        private fun min(vararg numbers: Int): Int {
            var min = Int.MAX_VALUE
            for (num in numbers) {
                min = if (min < num) min else num
            }
            return min
        }
    }
}