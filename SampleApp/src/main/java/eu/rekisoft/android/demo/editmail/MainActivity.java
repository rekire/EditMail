package eu.rekisoft.android.demo.editmail;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.google.android.material.textfield.TextInputLayout;

import eu.rekisoft.android.editmail.MxValidator;

public class MainActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EditText basic = findViewById(R.id.basic);
        TextInputLayout input = findViewById(R.id.input_layout);
        EditText nested = findViewById(R.id.nested);
        new MxValidator.Builder(basic).errorViewer(basic).build();
        new MxValidator.Builder(nested).errorViewer(input).build();
        TextView privacy = findViewById(R.id.privacy);
        privacy.setMovementMethod(LinkMovementMethod.getInstance());
    }
}