package eu.rekisoft.android.editmail

import android.annotation.SuppressLint
import android.content.Context
import android.text.*
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.*
import com.google.android.material.textfield.TextInputLayout
import eu.rekisoft.android.util.LazyWorker
import eu.rekisoft.android.util.ThreadingHelper
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsmessage.Question
import org.minidns.record.MX
import org.minidns.record.Record
import java.lang.IllegalArgumentException
import java.net.IDN
import java.util.*
import java.util.concurrent.Executors

typealias Resolver = (String) -> MxValidator.ResolverResult

open class MxValidator internal constructor(builder: Builder): TextWatcher {
    data class ResolverResult(val resultCount: Int, val notFound: Boolean)

    @VisibleForTesting
    internal var resolv = builder.resolver
    private var currentInput: String? = null
    private val context = builder.context
    private var errorViewer = builder.errorViewer
    private val editText = builder.editText

    private fun typoCheck(domain: String) =
        (customDomains + wellKnownDomains).firstOrNull { user ->
            damerauLevenshteinDistance(user, domain, 128) == 1
        }?.let { mail ->
            updateStatus(AddressStatus.typoDetected, mail)
        }

    private val executorService = Executors.newScheduledThreadPool(0)

    private val mxLookup = LazyWorker.createLifeCycleAwareJob(builder.lifecycle) {
        val localCopy = currentInput
        localCopy?.domain?.let { domain ->
            if (isWellKnownDomain(domain)) {
                updateStatus(AddressStatus.valid)
            } else {
                executorService.submit {
                    val response = resolv(domain)
                    when {
                        response.notFound -> typoCheck(domain) ?: updateStatus(AddressStatus.notRegistered)
                        response.resultCount == 0 -> typoCheck(domain) ?: updateStatus(AddressStatus.noMxRecord)
                        response.resultCount > 0 -> updateStatus(AddressStatus.valid)
                        else -> updateStatus(AddressStatus.unknown)
                    }
                }
            }
        } ?: if (localCopy.isNullOrBlank())
            updateStatus(AddressStatus.unknown)
        else
            updateStatus(AddressStatus.wrongSchema)
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        updateStatus(AddressStatus.pending)
        setError(null)
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable) {
        currentInput = s.toString()
        reset()
        if (hasWellKnownDomain(s.toString())) {
            updateStatus(AddressStatus.valid)
        } else {
            // When the change is done set the internal status to pending and invoke doCheck delayed.
            updateStatus(AddressStatus.pending)
            mxLookup.doLater(150)
        }
    }

    fun validateMailAddress(emailAddress: String) {
        currentInput = emailAddress
        mxLookup.doNow()
    }

    @VisibleForTesting
    internal open fun updateStatus(status: AddressStatus, mail: String? = null) {
        mxLookup.doLater(1000) {
            setError(when (status) {
                AddressStatus.pending,
                AddressStatus.unknown -> null
                AddressStatus.valid -> null
                AddressStatus.notRegistered -> context.getString(R.string.email_domain_unknown)
                AddressStatus.noMxRecord -> context.getString(R.string.email_no_mx)
                AddressStatus.typoDetected -> createClickableSuggestion(mail!!)
                AddressStatus.wrongSchema -> null
            })
        }
        if (status == AddressStatus.valid) {
            markAsValid()
        } else if (status == AddressStatus.wrongSchema) {
            mxLookup.doLater(3000) {
                setError(context.getString(R.string.email_address_incomplete))
            }
        }
    }

    private fun createClickableSuggestion(domain: String) : CharSequence {
        val hint = SpannableStringBuilder(context.getString(R.string.email_did_you_mean, domain))
        val start = hint.indexOf(domain)
        assert(start >= 0)
        hint.setSpan(SuggestionSpan(domain), start, start + domain.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return hint
    }

    private inner class SuggestionSpan(private val domain: String) : URLSpan(domain) {
        @SuppressLint("SetTextI18n")
        override fun onClick(widget: View) {
            editText?.let {
                // get the user part of the email address
                val userPart = editText.text.toString().substringBefore("@")
                // check where the selection is, if the selection goes until the end move it to the
                // end when it became longer
                val start = editText.selectionStart
                val end = editText.selectionEnd
                val last = editText.text.length
                val endWasAtEnd = end == last
                val startWasAtEnd = start == last
                // update mail address
                editText.setText("$userPart@$domain")
                // update selection
                editText.setSelection(
                        if (startWasAtEnd) editText.text.length else start,
                        if (endWasAtEnd) editText.text.length else end)
            }
        }
    }

    private val handler by lazy { ThreadingHelper.createHandler() }

    private fun setError(error: CharSequence?) {
        val update = {
            when (errorViewer) {
                is EditText -> (errorViewer as EditText).error = error
                is TextInputLayout -> (errorViewer as TextInputLayout).apply {
                    this.error = error
                    findViewById<TextView>(R.id.textinput_error).apply {
                        if (movementMethod !is LinkMovementMethod && linksClickable) {
                            movementMethod = LinkMovementMethod.getInstance()
                        }
                    }
                }
                else -> throw IllegalArgumentException()
            }
        }
        if (ThreadingHelper.isOnMainThread) {
            update()
        } else {
            handler.post { update() }
        }
    }

    private val icon by lazy {
        ResourcesCompat.getDrawable(errorViewer!!.resources, R.drawable.ic_valid, null)?.apply {
            setBounds(0,0, intrinsicWidth, intrinsicHeight)
        }
    }

    private fun markAsValid() {
        val update = {
            when (errorViewer) {
                is EditText -> with(errorViewer as EditText) {
                    setCompoundDrawables(null, null, icon, null)
                }
                is TextInputLayout -> with(errorViewer as TextInputLayout) {
                    endIconDrawable = icon
                    isEndIconVisible = true
                }
            }
        }
        if (ThreadingHelper.isOnMainThread) {
            update()
        } else {
            handler.post { update() }
        }
    }

    private fun reset() {
        val update = {
            when (errorViewer) {
                is EditText -> with(errorViewer as EditText) {
                    setCompoundDrawables(null, null, null, null)
                    error = null
                }
                is TextInputLayout -> with(errorViewer as TextInputLayout) {
                    endIconDrawable = null
                    error = null
                }
            }
        }
        if (ThreadingHelper.isOnMainThread) {
            update()
        } else {
            handler.post { update() }
        }
    }

    class Builder @JvmOverloads constructor(val editText: EditText? = null) {
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
                result.wasSuccessful() -> ResolverResult(result.response.answerSection.count {
                    (it.payload as MX).target.ace.lowercase(Locale.ENGLISH) != "localhost" // TODO check if MX-Server exists
                }, notFound = false)
                else -> ResolverResult(-1, notFound = false)
            }
        }

        fun build() = MxValidator(this).also {
            editText?.addTextChangedListener(it)
        }
    }

    companion object {
        private val String.domain: String?
        get() {
            val atPosition = indexOf("@")
            return if (atPosition >= 1 && length > atPosition + 1) {
                try {
                    IDN.toASCII(substring(atPosition + 1).lowercase(Locale.US))
                } catch (e: IllegalArgumentException) {
                    null
                }
            } else null
        }

        fun isWellKnownDomain(domain: String) =
            wellKnownDomains.contains(domain) || customDomains.contains(domain)

        fun hasWellKnownDomain(emailAddress: String) =
            emailAddress.domain?.let(::isWellKnownDomain) ?: false

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