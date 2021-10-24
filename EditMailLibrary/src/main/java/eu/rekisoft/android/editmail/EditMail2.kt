package eu.rekisoft.android.editmail

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.EditText

class EditMail2 @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : EditText(context, attrs, defStyle) {
    class Validator : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, p1: Int, p2: Int, p3: Int) {
            TODO("Not yet implemented")
        }

        override fun afterTextChanged(p0: Editable?) {
            TODO("Not yet implemented")
        }

    }
    init {

    }
}