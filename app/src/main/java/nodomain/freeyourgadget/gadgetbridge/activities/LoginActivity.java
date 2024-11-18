package nodomain.freeyourgadget.gadgetbridge.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.R;

public class LoginActivity extends Activity {

    private static final int INTERNET_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "LoginActivity";

    private EditText emailField;
    private EditText passwordField;
    private Button loginButton;
    private Button registerButton;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db; // Firestore instance

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance(); // Initialize Firebase Auth
        db = FirebaseFirestore.getInstance(); // Initialize Firestore

        emailField = findViewById(R.id.username);
        passwordField = findViewById(R.id.password);
        loginButton = findViewById(R.id.login_button);
        registerButton = findViewById(R.id.registerButton);

        registerButton.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
        });

        loginButton.setOnClickListener(v -> {
            String email = emailField.getText().toString().trim();
            String password = passwordField.getText().toString().trim();
            loginUser(email, password);
        });

        checkInternetPermission();
    }

    private void checkInternetPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.INTERNET},
                    INTERNET_PERMISSION_REQUEST_CODE);
        }
    }

    private void loginUser(String email, String password) {
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(LoginActivity.this, "Form tidak boleh kosong", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            Log.d(TAG, "signInWithEmail:success. User ID: " + user.getUid());

                            // Save user data to Firestore
                            saveUserToFirestore(user);

                            // Show welcome toast
                            Toast.makeText(LoginActivity.this, "Welcome, " + user.getEmail(), Toast.LENGTH_SHORT).show();

                            // Navigate to next activity
                            startActivity(new Intent(LoginActivity.this, ControlCenterv2.class));
                            finish();  // Close the login activity
                        }
                    } else {
                        Log.w(TAG, "signInWithEmail:failure", task.getException());
                        Toast.makeText(LoginActivity.this, "Authentication Gagal: " + task.getException().getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void saveUserToFirestore(FirebaseUser user) {
        // Extract user's email and username
        String email = user.getEmail();
        String username = email != null ? email.split("@")[0] : "unknown"; // Use part of email as username

        // Create a user data map
        Map<String, Object> userData = new HashMap<>();
        userData.put("UID", user.getUid());
        userData.put("email", email);

        // Format the current time as "Day, DD-MM-YYYY, HH:mm:ss"
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd-MM-yyyy, HH:mm:ss", Locale.getDefault());
        String formattedLastLogin = sdf.format(new Date());

        userData.put("lastLogin", formattedLastLogin); // Add formatted timestamp

        // Save data to Firestore under the updated structure
        db.collection("users")
                .document(username) // Use username as document ID
                .set(userData, SetOptions.merge()) // Directly store user data under the username document
                .addOnSuccessListener(aVoid -> Log.d(TAG, "User data successfully written to Firestore."))
                .addOnFailureListener(e -> Log.w(TAG, "Error writing user data to Firestore", e));
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == INTERNET_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, you can proceed with network-related tasks
            } else {
                Toast.makeText(this, "Internet permission is required for this app to function.", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
