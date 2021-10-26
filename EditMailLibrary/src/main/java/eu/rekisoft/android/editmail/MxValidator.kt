package eu.rekisoft.android.editmail

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewTreeLifecycleOwner
import com.google.android.material.textfield.TextInputLayout
import java.lang.IllegalArgumentException
import eu.rekisoft.android.util.LazyWorker
import org.minidns.dnsmessage.DnsMessage
import org.minidns.dnsmessage.Question
import org.minidns.record.Record

typealias OnValidatorResultChanged = (AddressStatus) -> Unit

fun View.requireViewLifecycle() = requireNotNull(ViewTreeLifecycleOwner.get(this))

class MxValidator(errorViewer: View): TextWatcher {
    private val resolver = DohResolver()

    var errorViewer: View = errorViewer
        set(value) {
            if (value !is EditText && value !is TextInputLayout) {
                throw IllegalArgumentException("This view type is not yet supported")
            }
            field = value
        }

    private val mxLookup = LazyWorker.createLifeCycleAwareJob(errorViewer.requireViewLifecycle().lifecycle) {
        currentDomain?.let { domain ->
            val response = resolver.query(Question(domain, Record.TYPE.MX)).response
            val result = when {
                response.responseCode == DnsMessage.RESPONSE_CODE.NX_DOMAIN -> AddressStatus.notRegistered
                response.answerSection.isEmpty() -> AddressStatus.noMxRecord
                response.answerSection.isNotEmpty() -> AddressStatus.valid
                else -> AddressStatus.unknown
            }

        }
    }

    val status: LiveData<AddressStatus> = MutableLiveData(AddressStatus.unknown)
    private var currentDomain: String? = null

    private fun <T> LiveData<T>.postValue(value: T) = (status as? MutableLiveData<T>)?.postValue(value)

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        // remove all error states on text changes.
        status.postValue(AddressStatus.pending)
    }
    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
    }
    override fun afterTextChanged(s: Editable) {
        // When the change is done set the internal status to pending and invoke doCheck delayed.
        status.postValue(AddressStatus.pending)
        mxLookup.doLater(150)
    }

    private fun setError(error: String?) {
        when(errorViewer) {
            is EditText -> (errorViewer as EditText).error = error
            is TextInputLayout -> (errorViewer as TextInputLayout).error = error
        }
    }
}