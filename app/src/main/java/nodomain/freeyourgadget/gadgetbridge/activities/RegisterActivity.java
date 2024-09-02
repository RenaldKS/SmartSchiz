package nodomain.freeyourgadget.gadgetbridge.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import nodomain.freeyourgadget.gadgetbridge.R;

public class RegisterActivity extends Activity {

    private EditText emailField;
    private EditText passwordField;
    private Button registerButton;
    private EditText nameField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        emailField = (EditText) findViewById(R.id.emailEditText);
        nameField = (EditText) findViewById(R.id.nameEditText);
        passwordField = (EditText) findViewById(R.id.passwordEditText);
        registerButton = (Button) findViewById(R.id.registerButton);

        // Initially disable the button
        registerButton.setEnabled(false);

        // Create a TextWatcher
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Enable the button if all fields are filled
                if (!emailField.getText().toString().trim().isEmpty() &&
                        !nameField.getText().toString().trim().isEmpty() &&
                        !passwordField.getText().toString().trim().isEmpty()) {
                    registerButton.setEnabled(true);
                } else {
                    registerButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        // Add the TextWatcher to each EditText
        emailField.addTextChangedListener(textWatcher);
        nameField.addTextChangedListener(textWatcher);
        passwordField.addTextChangedListener(textWatcher);

        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            }
        });
    }
}