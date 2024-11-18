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
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.R;

public class RegisterActivity extends Activity {

    private static final String TAG = "RegisterActivity";

    private EditText usernameField;
    private EditText emailField;
    private EditText passwordField;
    private EditText confirmPasswordField;
    private Button registerButton;
    private Button backButton;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase Auth and Firestore
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        usernameField = findViewById(R.id.username);
        emailField = findViewById(R.id.email);
        passwordField = findViewById(R.id.password);
        confirmPasswordField = findViewById(R.id.confirm_password);
        registerButton = findViewById(R.id.register_button);
        backButton = findViewById(R.id.back_button);

        registerButton.setOnClickListener(v -> {
            String username = usernameField.getText().toString().trim();
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();
            String confirmPassword = confirmPasswordField.getText().toString().trim();

            if (!password.equals(confirmPassword)) {
                Toast.makeText(RegisterActivity.this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }

            if (username.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(RegisterActivity.this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidEmail(email)) {
                Toast.makeText(RegisterActivity.this, "Invalid email format", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidPassword(password)) {
                Toast.makeText(RegisterActivity.this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            checkUsernameAvailability(username, email, password);
        });

        backButton.setOnClickListener(v -> finish()); // Return to the previous activity
    }

    // Validate email format (basic check)
    private boolean isValidEmail(String email) {
        return email.contains("@");
    }

    // Validate password length
    private boolean isValidPassword(String password) {
        return password.length() >= 6;
    }

    private void checkUsernameAvailability(String username, String email, String password) {
        db.collection("users").document(username).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Toast.makeText(RegisterActivity.this, "Username is already taken", Toast.LENGTH_SHORT).show();
                    } else {
                        registerUser(username, email, password);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking username availability", e);
                    Toast.makeText(RegisterActivity.this, "Error checking username, try again later", Toast.LENGTH_SHORT).show();
                });
    }

    private void registerUser(String username, String email, String password) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            saveUserData(username, email, user.getUid());
                        }
                    } else {
                        Log.w(TAG, "createUserWithEmail:failure", task.getException());
                        Toast.makeText(RegisterActivity.this, "Registration Failed: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserData(String username, String email, String uid) {
        // Format lastLogin date as "Day, Date-Month-Year"
        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, dd-MM-yyyy, HH:mm:ss", Locale.getDefault());
        String lastLogin = dateFormat.format(new Date());

        // Prepare user data
        Map<String, Object> userData = new HashMap<>();
        userData.put("username", username);
        userData.put("email", email);
        userData.put("lastLogin", lastLogin);
        userData.put("UID", uid);

        // Save data with username as document ID and UID stored directly in the document
        db.collection("users").document(username)  // Username as document ID
                .set(userData)  // Store user data directly under the username document
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(RegisterActivity.this, "Registration Successful", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error saving user data", e);
                    Toast.makeText(RegisterActivity.this, "Failed to save user data, try again later", Toast.LENGTH_SHORT).show();
                });
    }
}

