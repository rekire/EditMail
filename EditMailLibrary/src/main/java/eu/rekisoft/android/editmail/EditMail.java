/**
 * @copyright
 * This code is licensed under the Rekisoft Public License.
 * See http://www.rekisoft.eu/licenses/rkspl.html for more informations.
 */
/**
 * @package eu.rekisoft.android.controls
 * This package contains controls provided by [rekisoft.eu](http://rekisoft.eu/). 
 */
package eu.rekisoft.android.editmail;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;

import eu.rekisoft.android.editmail.MailChecker.AddressStatus;
import eu.rekisoft.android.util.LazyWorker;
import eu.rekisoft.android.util.UiWorker;

/**
 * EditText for email addresses.
 *
 * @author René Kilczan
 * @version 1.0
 * @copyright This code is licensed under the Rekisoft Public License.<br/>
 * See http://www.rekisoft.eu/licenses/rkspl.html for more informations.
 */
public class EditMail extends EditText {
    /**
     * The delay for beginning a lookup if the email address is fine
     */
    private static final int SEARCH_DELAY = 300;
    /**
     * The delay for showing the results after getting the lookup data. This should prevent messages while typing
     */
    private static final int SHOW_DELAY = 1200;
    private final ArrayList<StatusChangedListener> observers = new ArrayList<EditMail.StatusChangedListener>();

    private AddressStatus status = AddressStatus.unknown;
    private final Helper helper;

    /**
     * Simple constructor to use when creating a EditMail from code.
     *
     * @param context The Context the view is running in, through which it can access the current theme, resources, etc.
     */
    public EditMail(Context context) {
        this(context, null, 0);
    }

    /**
     * Constructor that is called when inflating a view from XML. This is called when a view is being constructed from an XML file,
     * supplying attributes that were specified in the XML file. This version uses a default style of 0, so the only attribute values
     * applied are those in the Context's Theme and the given AttributeSet.
     * <p/>
     * <p/>
     * The method onFinishInflate() will be called after all children have been added.
     *
     * @param context The Context the view is running in, through which it can access the current theme, resources, etc.
     * @param attrs   The attributes of the XML tag that is inflating the view.
     * @see #EditMail(Context, AttributeSet, int)
     */
    public EditMail(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Perform inflation from XML and apply a class-specific base style. This constructor of View allows subclasses to use their own base
     * style when they are inflating. For example, a Button class's constructor would call this version of the super class constructor and
     * supply <code>R.attr.buttonStyle</code> for <var>defStyle</var>; this allows the theme's button style to modify all of the base view
     * attributes (in particular its background) as well as the Button class's attributes.
     *
     * @param context  The Context the view is running in, through which it can access the current theme, resources, etc.
     * @param attrs    The attributes of the XML tag that is inflating the view.
     * @param defStyle An attribute in the current theme that contains a reference to a style resource to apply to this view. If 0, no default
     *                 style will be applied.
     * @see #EditMail(Context, AttributeSet)
     */
    public EditMail(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, android.R.attr.editTextStyle);

        setInputType(getInputType() | EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);

        if(BuildConfig.DEBUG) {
            if(isInEditMode()) {
                helper = null;
                return;
            }
        }

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EmailPreference, defStyle, 0);

        CharSequence[] domains = a.getTextArray(R.styleable.EmailPreference_domains);

        if(domains != null) {
            MailChecker.setDomainList((String[]) domains);
        }

        a.recycle();

        helper = new Helper(this, new StatusChangedListener() {
            @Override
            public void statusChanged(AddressStatus status) {
                EditMail.this.status = status;
                for(StatusChangedListener l : observers) {
                    l.statusChanged(status);
                }
            }
        });
        addTextChangedListener(helper);
    }

    /**
     * @return <code>true</code> if the resolver status is AddressStatus.valid.
     */
    public boolean isMailAddressValid() {
        return status == AddressStatus.valid;
    }

    /**
     * @return the AddressStatus of the email verification.
     */
    public AddressStatus getMailStatus() {
        return status;
    }

    /**
     * Adds a StatusChangedListener to this EditMail.
     *
     * @param listener The StatusChangedListener to add.
     * @return always <code>true</code>.
     */
    public boolean addStatusChangedListener(StatusChangedListener listener) {
        return observers.add(listener);
    }

    /**
     * Removes a StatusChangedListener from this EditMail.
     *
     * @param listener
     * @return <code>true</code> if the listeners were modified by this operation,
     * <code>false</code> otherwise.
     */
    public boolean removeStatusChangedListener(StatusChangedListener listener) {
        return observers.remove(listener);
    }

    /**
     * A UiWorker implementation which
     *
     * @author René Kilczan
     */
    private final static class Helper extends UiWorker<EditText> implements TextWatcher,
            OnTouchListener {
        private final EditText mail;
        private String suggestion;
        private String currentError;
        private final StatusChangedListener observer;

        /**
         * Creates a new instance of EditMail.java.
         *
         * @param field
         * @param listener
         */
        public Helper(EditText field, StatusChangedListener listener) {
            super(field, false, false);
            mail = field;
            observer = listener;
        }

        /**
         * The email validation which is executed delayed.
         */
        private final Runnable doCheck = new Runnable() {
            @Override
            public void run() {
                Editable txt = mail.getText();
                if(TextUtils.isEmpty(txt)) {
                    observer.statusChanged(AddressStatus.unknown);
                    return;
                }
                final String mail = txt.toString().trim();
                boolean okay = !TextUtils.isEmpty(mail) && TextUtils.isGraphic(mail);
                if(okay) {
                    checkMailAddress(mail);
                }
            }
        };

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // remove all error states on text changes.
            mail.setError(null);
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            // When the change is done set the internal status to pending and invoke doCheck delayed.
            observer.statusChanged(AddressStatus.pending);
            LazyWorker.getSharedInstance().doLater(doCheck, SEARCH_DELAY);
        }

        // this listener is invoked by the popup "Did you mean xyz@example.com?".
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            v.setOnTouchListener(null);
            if(suggestion != null) {
                mail.setText(suggestion);
                mail.setSelection(suggestion.length());
                mail.setError(null);
                suggestion = null;
                observer.statusChanged(AddressStatus.valid);
                // mPositiveButton.setEnabled(true);
            }
            return false;
        }

        // Sets the error popup on the UI thread, together with a onclick observer to the popup.
        // Well a little hackish made but it works :-)
        @Override
        protected void doWork(EditText mail) {
            mail.setError(currentError);
            try {
                Field mErrorPopup;
                Object popupHolder;
                if(Build.VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) {
                    Field mEditor = TextView.class.getDeclaredField("mEditor");
                    mEditor.setAccessible(true);
                    popupHolder = mEditor.get(mail);
                    mErrorPopup = popupHolder.getClass().getDeclaredField("mErrorPopup");
                } else {
                    popupHolder = mail;
                    mErrorPopup = TextView.class.getDeclaredField("mPopup");
                }
                mErrorPopup.setAccessible(true);
                Object errorPopup = mErrorPopup.get(popupHolder);
                if(errorPopup != null) {
                    Field mView = errorPopup.getClass().getDeclaredField("mView");
                    mView.setAccessible(true);
                    TextView view = (TextView) mView.get(errorPopup);
                    view.setOnTouchListener(this);
                }
            } catch(Exception e) {
                Log.e(getClass().getSimpleName(), "Error while hacking: ", e);
            }
        }

        /**
         * The check if the email address is valid. The result is set to currentError.
         *
         * @param address The email address to check.
         */
        private void checkMailAddress(String address) {
            AddressStatus result = MailChecker.validate(address);
            Resources res = mail.getResources();
            suggestion = null;
            switch(result) {
                case noMxRecord:
                    currentError = res.getString(R.string.email_no_mx);
                    break;
                case notRegistered:
                    currentError = res.getString(R.string.email_domain_unknown);
                    break;
                case typoDetected:
                    currentError = res.getString(R.string.email_did_you_mean, result.getMailAddress());
                    suggestion = result.getMailAddress();
                    break;
                case wrongSchema:
                    if(!address.contains("@") || !address.contains(".")) {
                        currentError = res.getString(R.string.email_address_incompleat);
                    } else {
                        currentError = res.getString(R.string.email_schema_error);
                    }
                    break;
                case unknown:
                case valid:
                default:
                    currentError = null;
                    break;
            }

            observer.statusChanged(result);

            // inform the user a little later about errors
            LazyWorker.getSharedInstance().doLater(this, SHOW_DELAY);
        }

    }

    /**
     * A listener for observing email validation changes.
     *
     * @author René Kilczan
     */
    public interface StatusChangedListener {
        /**
         * Invoked when the email validation status has changed.
         *
         * @param status The new AddressStatus.
         */
        void statusChanged(AddressStatus status);
    }
}