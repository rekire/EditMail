/**
* Not sure about the licence yet, come back later for updates.
* It will be something like Apache 2 (but never [L]GPL!) you must quote somewhere my name, that is all for now.
*/
package eu.rekisoft.android.controls;

import java.lang.reflect.Field;
import java.util.ArrayList;

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

import com.bangster.android.BuildConfig;
import com.bangster.android.R;

import eu.rekisoft.android.util.LazyWorker;
import eu.rekisoft.android.util.MailChecker;
import eu.rekisoft.android.util.MailChecker.AddressStatus;
import eu.rekisoft.android.util.UiWorker;

/**
 * EditText for email addresses.
 * 
 * @author rekire
 */
public class EditMail extends EditText {
  /** The delay for beginning a lookup if the email address is fine */
	private static final int SEARCH_DELAY = 300;
	/** The delay for showing the results after getting the lookup data. This should prevent messages while typing */
	private static final int SHOW_DELAY = 1200;
	private final ArrayList<StatusChangedListener> observers = new ArrayList<EditMail.StatusChangedListener>();

	private AddressStatus status = AddressStatus.unknown;
	private final Helper helper;

    public EditMail(Context context) {
		this(context, null, 0);
    }

    public EditMail(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
    }

    public EditMail(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

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
			MailChecker.setDomainList((String[])domains);
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
	
    public boolean isMailAddressValid() {
		return status == AddressStatus.valid;
	}

	public AddressStatus getMailStatus() {
		return status;
    }

	public boolean addStatusChangedListener(StatusChangedListener listener) {
		return observers.add(listener);
	}
    
	public boolean removeStatusChangedListener(StatusChangedListener listener) {
		return observers.remove(listener);
	}

	private static class Helper extends UiWorker<Void> implements TextWatcher, OnTouchListener {
		private final EditText mail;
		private String suggestion;
		private String currentError;
		private final StatusChangedListener observer;

		public Helper(EditText field, StatusChangedListener listener) {
			super(null, false, false);
			mail = field;
			observer = listener;
		}

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
			mail.setError(null);
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}

		@Override
		public void afterTextChanged(Editable s) {
			observer.statusChanged(AddressStatus.pending);
			LazyWorker.getSharedInstance().doLater(doCheck, SEARCH_DELAY);
		}

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

		@Override
		protected void doWork(Void data) {
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
					TextView view = (TextView)mView.get(errorPopup);
					view.setOnTouchListener(this);
				}
			} catch(Exception e) {
				Log.e(getClass().getSimpleName(), "Error while hacking: ", e);
			}
		}

		private void checkMailAddress(String address) {
			AddressStatus result = MailChecker.validate(address);
			Resources res = mail.getResources();
			suggestion = null;
			switch(result) {
			case noMxRecord:
				currentError = res.getString(R.string.email_no_mx);
				break;
			case notRegisted:
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

	public interface StatusChangedListener {
		void statusChanged(AddressStatus status);
	}
}