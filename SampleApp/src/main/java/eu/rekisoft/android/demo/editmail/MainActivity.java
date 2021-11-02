package eu.rekisoft.android.demo.editmail;

import android.os.Bundle;
import android.widget.EditText;

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
        MxValidator validator1 = new MxValidator.Builder().errorViewer(basic).build();
        basic.addTextChangedListener(validator1);
        MxValidator validator2 = new MxValidator.Builder().errorViewer(input).build();
        nested.addTextChangedListener(validator2);
    }
}