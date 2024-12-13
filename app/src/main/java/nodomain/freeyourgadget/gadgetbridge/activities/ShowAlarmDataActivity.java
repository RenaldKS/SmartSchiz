package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
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
    private Spinner dropdownMenu;
    private List<String> connectedUsers = new ArrayList<>();
    private String currentUsername;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_show_alarm_data);

        dropdownMenu = findViewById(R.id.spinnerConnectedUsers);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            fetchCurrentUsernameAndData(user.getEmail());
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
        }

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

    private void fetchCurrentUsernameAndData(String email) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        currentUsername = task.getResult().getDocuments().get(0).getId();
                        fetchAlarmData(currentUsername);
                        fetchConnectedAccounts(currentUsername);
                    } else {
                        Log.e(TAG, "Failed to fetch current username");
                        Toast.makeText(this, "Error fetching user data", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchAlarmData(String username) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("alarmData")
                .document(username)
                .collection("data")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<String> alarmDataList = new ArrayList<>();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String timestamp = document.getString("timestamp");
                            Long heartRate = document.getLong("heartRate");
                            Long stressLevel = document.getLong("stressLevel");
                            String locationLink = document.getString("locationLink");

                            String data = "Timestamp: " + timestamp +
                                    "\nHeart Rate: " + heartRate +
                                    "\nStress Level: " + stressLevel +
                                    (locationLink != null ? "\nLocation: " + locationLink : "");
                            alarmDataList.add(data);
                        }
                        displayAlarmData(alarmDataList);
                    } else {
                        Log.w(TAG, "No alarm data found for " + username);
                        Toast.makeText(this, "No alarm data available", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchConnectedAccounts(String username) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("connectionRequests")
                .document(username)
                .collection("requests")
                .whereEqualTo("status", "accepted")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        connectedUsers.clear();
                        connectedUsers.add("My Data"); // Add logged-in user's data as the first option

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            String requesterUsername = document.getString("requesterUsername");
                            String targetUsername = document.getString("targetUsername");

                            // Add the connected username (opposite user in the connection)
                            connectedUsers.add(
                                    username.equals(requesterUsername) ? targetUsername : requesterUsername
                            );
                        }

                        setupDropdownMenu();
                    } else {
                        Log.w(TAG, "No connected accounts found");
                        Toast.makeText(this, "No connected accounts", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupDropdownMenu() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, connectedUsers);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        dropdownMenu.setAdapter(adapter);

        dropdownMenu.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedUser = connectedUsers.get(position);
                if (selectedUser.equals("My Data")) {
                    fetchAlarmData(currentUsername);
                } else {
                    fetchAlarmData(selectedUser);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // No action required
            }
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

    private void showEmailInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Target User Email");

        final EditText input = new EditText(this);
        input.setHint("Enter the target user's email");
        builder.setView(input);

        builder.setPositiveButton("Send Request", (dialog, which) -> {
            String targetEmail = input.getText().toString().trim();
            if (!targetEmail.isEmpty()) {
                sendConnectionRequest(targetEmail);
            } else {
                Toast.makeText(this, "Email cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void sendConnectionRequest(String targetEmail) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        db.collection("users")
                .whereEqualTo("email", targetEmail)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        String targetUsername = task.getResult().getDocuments().get(0).getId();
                        Map<String, Object> request = new HashMap<>();
                        request.put("requesterUsername", currentUsername);
                        request.put("targetUsername", targetUsername);
                        request.put("status", "pending");

                        db.collection("connectionRequests")
                                .document(currentUsername)
                                .collection("requests")
                                .add(request)
                                .addOnSuccessListener(unused -> {
                                    Toast.makeText(this, "Request sent successfully", Toast.LENGTH_SHORT).show();
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to send request", e));
                    } else {
                        Toast.makeText(this, "Target user not found", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
