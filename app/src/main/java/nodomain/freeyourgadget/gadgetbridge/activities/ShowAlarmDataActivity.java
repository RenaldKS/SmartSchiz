package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.R;

public class ShowAlarmDataActivity extends AppCompatActivity {
    private static final String TAG = "ShowAlarmDataActivity";
    private String currentUserId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_alarm_data);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUserId = user.getUid();
            fetchAlarmData(currentUserId);
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Set up the button to open email input dialog to send connection request
        Button sendRequestButton = findViewById(R.id.sendRequestButton);
        sendRequestButton.setOnClickListener(v -> {
            Log.d(TAG, "Send request button clicked");
            showEmailInputDialog();
        });

        Button viewPendingRequestButton = findViewById(R.id.viewPendingRequestButton);
        viewPendingRequestButton.setOnClickListener(v -> {
            Log.d(TAG, "Navigating to PendingRequestActivity");
            Toast.makeText(this, "Opening pending requests", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, PendingRequestActivity.class);
            startActivity(intent);
        });

        Button switchUserButton = findViewById(R.id.switchUserButton);
        switchUserButton.setOnClickListener(v -> {
            Log.d(TAG, "Switch user button clicked");
            Toast.makeText(this, "Switching to your data", Toast.LENGTH_SHORT).show();
            switchUserData();
        });

        Button viewConnectedAccountsButton = findViewById(R.id.viewConnectedAccountsButton);
        viewConnectedAccountsButton.setOnClickListener(v -> {
            Log.d(TAG, "Navigating to ConnectedAccountsActivity");
            Toast.makeText(this, "Opening connected accounts", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ConnectedAccountActivity.class);
            startActivity(intent);
        });
        Button showDeclinedButton = findViewById(R.id.show_declined_button);
        showDeclinedButton.setOnClickListener(v -> {
            Log.d(TAG, "Navigating to DeclinedAccountsActivity");
            Toast.makeText(this, "Opening Declined Accounts", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(ShowAlarmDataActivity.this, DeclinedAccountsActivity.class);
            startActivity(intent);
        });

    }

    private void fetchAlarmData(String currentUserId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String email = user.getEmail();
        if (email == null) {
            Log.w(TAG, "User email is null");
            Toast.makeText(this, "User email not found", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fetch the username from Firestore
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        String username = task.getResult().getDocuments().get(0).getId();

                        // Query alarmData -> username -> data
                        db.collection("alarmData")
                                .document(username)
                                .collection("data")
                                .get()
                                .addOnCompleteListener(fetchTask -> {
                                    if (fetchTask.isSuccessful()) {
                                        QuerySnapshot querySnapshot = fetchTask.getResult();
                                        if (!querySnapshot.isEmpty()) {
                                            List<String> alarmDataList = new ArrayList<>();
                                            for (QueryDocumentSnapshot document : querySnapshot) {
                                                String timestamp = document.getString("timestamp");
                                                Long heartRate = document.getLong("heartRate");
                                                Long stressLevel = document.getLong("stressLevel");
                                                String locationLink = document.getString("locationLink");

                                                if (heartRate != null && stressLevel != null) {
                                                    String alarmData = "Timestamp: " + timestamp +
                                                            "\nHeart Rate: " + heartRate +
                                                            "\nStress Level: " + stressLevel;
                                                    if (locationLink != null) {
                                                        alarmData += "\nLocation: " + locationLink;
                                                    }
                                                    alarmDataList.add(alarmData);
                                                }
                                            }
                                            displayAlarmData(alarmDataList);
                                        } else {
                                            Log.d(TAG, "No alarm data found for username: " + username);
                                            Toast.makeText(this, "No alarm data found", Toast.LENGTH_SHORT).show();
                                        }
                                    } else {
                                        Log.w(TAG, "Error getting documents: ", fetchTask.getException());
                                        Toast.makeText(this, "Error fetching data", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    } else {
                        Log.w(TAG, "Username not found for email: " + email);
                        Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to query username", e);
                    Toast.makeText(this, "Error finding user", Toast.LENGTH_SHORT).show();
                });
    }


    private void displayAlarmData(List<String> alarmDataList) {
        LinearLayout dataContainer = findViewById(R.id.dataContainer);
        dataContainer.removeAllViews();

        for (String data : alarmDataList) {
            TextView dataTextView = new TextView(this);
            dataTextView.setText(data);
            dataTextView.setAutoLinkMask(Linkify.WEB_URLS);
            dataTextView.setMovementMethod(LinkMovementMethod.getInstance());
            dataTextView.setPadding(8, 8, 8, 8);
            dataTextView.setTextSize(16);

            dataContainer.addView(dataTextView);

            View divider = new View(this);
            divider.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1));
            divider.setBackgroundColor(Color.GRAY);
            dataContainer.addView(divider);
        }
    }

    private void switchUserData() {
        Log.d(TAG, "Switching to display current user's data");
        fetchAlarmData(currentUserId);
        Toast.makeText(this, "Displaying your data", Toast.LENGTH_SHORT).show();
    }

    private void showEmailInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Target User Email");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setHint("Enter the target user's email");
        builder.setView(input);

        builder.setPositiveButton("Send Request", (dialog, which) -> {
            String targetUserEmail = input.getText().toString().trim();
            if (!targetUserEmail.isEmpty()) {
                if (android.util.Patterns.EMAIL_ADDRESS.matcher(targetUserEmail).matches()) {
                    Log.d(TAG, "Sending connection request to " + targetUserEmail);
                    sendConnectionRequest(targetUserEmail);
                    dialog.dismiss();  // Dismiss dialog after sending request
                } else {
                    Log.d(TAG, "Invalid email input: " + targetUserEmail);
                    Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "Email input is empty");
                Toast.makeText(this, "Email cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            Log.d(TAG, "Connection request cancelled");
            dialog.cancel();  // Cancel the dialog if the user presses Cancel
        });

        builder.show();
    }


    private void sendConnectionRequest(String targetEmailOrUsername) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String requesterId = user.getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            // Step 1: Look up the user by email or username
            db.collection("users")
                    .whereEqualTo("email", targetEmailOrUsername) // Try looking by email first
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            // Found the target user by email, retrieve their UID
                            String targetUserId = task.getResult().getDocuments().get(0).getId();
                            sendConnectionRequestToFirestore(requesterId, targetUserId);
                        } else {
                            // If email lookup fails, try username lookup
                            db.collection("users")
                                    .whereEqualTo("username", targetEmailOrUsername) // Fallback to username lookup
                                    .get()
                                    .addOnCompleteListener(task2 -> {
                                        if (task2.isSuccessful() && !task2.getResult().isEmpty()) {
                                            // Found the target user by username, retrieve their UID
                                            String targetUserId = task2.getResult().getDocuments().get(0).getId();
                                            sendConnectionRequestToFirestore(requesterId, targetUserId);
                                        } else {
                                            Log.w(TAG, "No user found with email or username: " + targetEmailOrUsername);
                                            Toast.makeText(this, "No user found with that email or username", Toast.LENGTH_SHORT).show();
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        Log.w(TAG, "Failed to query user by username", e);
                                        Toast.makeText(this, "Error finding target user", Toast.LENGTH_SHORT).show();
                                    });
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "Failed to query user by email", e);
                        Toast.makeText(this, "Error finding target user", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    // Helper method to send the request after looking up the target user
    private void sendConnectionRequestToFirestore(String requesterId, String targetUserId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Log.w(TAG, "No authenticated user");
            Toast.makeText(this, "You need to log in to send a connection request", Toast.LENGTH_SHORT).show();
            return;
        }

        String requesterEmail = currentUser.getEmail();
        if (requesterEmail == null) {
            Log.w(TAG, "Requester email is null");
            Toast.makeText(this, "Your account information is incomplete", Toast.LENGTH_SHORT).show();
            return;
        }

        // Log the requester's email
        Log.d(TAG, "Requester email: " + requesterEmail);

        // Fetch the requester's username from the `users` collection
        Log.d(TAG, "Fetching requester username from 'users' collection using email: " + requesterEmail);
        db.collection("users")
                .whereEqualTo("email", requesterEmail)
                .get()
                .addOnSuccessListener(requesterQuerySnapshot -> {
                    if (!requesterQuerySnapshot.isEmpty()) {
                        String requesterUsername = requesterQuerySnapshot.getDocuments().get(0).getId();
                        Log.d(TAG, "Requester username found: " + requesterUsername);

                        // Fetch the target user's data from the `users` collection
                        Log.d(TAG, "Fetching target user from 'users' collection using UID or username: " + targetUserId);
                        db.collection("users")
                                .whereEqualTo("UID", targetUserId)  // First attempt with UID
                                .get()
                                .addOnSuccessListener(targetQuerySnapshot -> {
                                    if (!targetQuerySnapshot.isEmpty()) {
                                        // Target user found by UID
                                        handleTargetUserFound(targetQuerySnapshot, db, requesterId, requesterUsername, requesterEmail, targetUserId);
                                    } else {
                                        // Retry fetching using the username if UID fails
                                        Log.w(TAG, "Target user not found for UID: " + targetUserId + ". Retrying with username...");
                                        db.collection("users")
                                                .whereEqualTo("username", targetUserId) // Retry with username
                                                .get()
                                                .addOnSuccessListener(usernameQuerySnapshot -> {
                                                    if (!usernameQuerySnapshot.isEmpty()) {
                                                        // Target user found by username
                                                        handleTargetUserFound(usernameQuerySnapshot, db, requesterId, requesterUsername, requesterEmail, targetUserId);
                                                    } else {
                                                        // Target user not found by either UID or username
                                                        Log.w(TAG, "Target user not found for UID or username: " + targetUserId);
                                                        Toast.makeText(this, "Target user not found", Toast.LENGTH_SHORT).show();
                                                    }
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.w(TAG, "Failed to fetch target user data by username: " + targetUserId, e);
                                                    Toast.makeText(this, "Failed to find target user", Toast.LENGTH_SHORT).show();
                                                });
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.w(TAG, "Failed to fetch target user data by UID: " + targetUserId, e);
                                    Toast.makeText(this, "Failed to find target user", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        Log.w(TAG, "Requester user data not found for email: " + requesterEmail);
                        Toast.makeText(this, "Your account information is incomplete", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to fetch requester user data for email: " + requesterEmail, e);
                    Toast.makeText(this, "Failed to retrieve your account details", Toast.LENGTH_SHORT).show();
                });
    }

    // Helper method to handle the target user found
    private void handleTargetUserFound(QuerySnapshot querySnapshot, FirebaseFirestore db, String requesterId,
                                       String requesterUsername, String requesterEmail, String targetUserId) {
        DocumentSnapshot targetUserDocument = querySnapshot.getDocuments().get(0);
        String targetUsername = targetUserDocument.getId();
        String targetEmail = targetUserDocument.getString("email");

        Log.d(TAG, "Target user found: Username=" + targetUsername + ", Email=" + targetEmail);

        // Create the request data
        Map<String, Object> request = new HashMap<>();
        request.put("requesterId", requesterId);
        request.put("requesterUsername", requesterUsername);
        request.put("requesterEmail", requesterEmail);
        request.put("targetUserId", targetUserId);
        request.put("targetUsername", targetUsername);
        request.put("targetEmail", targetEmail);
        request.put("status", "pending");
        request.put("timestamp", FieldValue.serverTimestamp());

        // Log the request data before saving
        Log.d(TAG, "Connection request data: " + request);

        // Save the request in the requester's `connectionRequests` subcollection
        db.collection("connectionRequests")
                .document(targetUsername)
                .collection("requests")
                .add(request)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Connection request sent successfully with ID: " + documentReference.getId());
                    Toast.makeText(this, "Request sent successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to send connection request", e);
                    Toast.makeText(this, "Failed to send request", Toast.LENGTH_SHORT).show();
                });
    }
}