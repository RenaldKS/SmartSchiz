package nodomain.freeyourgadget.gadgetbridge.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import nodomain.freeyourgadget.gadgetbridge.R;

public class RegisterActivity extends Activity {

    private static final String TAG = "RegisterActivity";

    private EditText emailField;
    private EditText passwordField;
    private EditText confirmPasswordField;
    private Button registerButton;
    private FirebaseAuth mAuth;
    private Button backButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        emailField = findViewById(R.id.email);
        passwordField = findViewById(R.id.password);
        confirmPasswordField = findViewById(R.id.confirm_password);
        registerButton = findViewById(R.id.register_button);
        backButton = findViewById(R.id.back_button);

        registerButton.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();
            String confirmPassword = confirmPasswordField.getText().toString().trim();

            if (!password.equals(confirmPassword)) {
                Toast.makeText(RegisterActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            registerUser(email, password);
        });
        backButton.setOnClickListener(v -> {
            finish(); // Finish the current activity and return to the previous one
        });
    }

    private void registerUser(String email, String password) {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(RegisterActivity.this, "Form tidak boleh kosong", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Sign up success
                        FirebaseUser user = mAuth.getCurrentUser();
                        Log.d(TAG, "createUserWithEmail:success");
                        Toast.makeText(RegisterActivity.this, "Registration Successful", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                        finish(); // Close the registration activity after success
                    } else {
                        // If sign up fails, display a message to the user.
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        Toast.makeText(RegisterActivity.this, "Registration Failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
