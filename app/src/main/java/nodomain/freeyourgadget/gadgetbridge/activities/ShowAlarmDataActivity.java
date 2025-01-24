package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import org.json.JSONException;
import org.json.JSONObject;

import nodomain.freeyourgadget.gadgetbridge.service.AlarmMonitoringService;
import nodomain.freeyourgadget.gadgetbridge.util.FCMAccessTokenProvider;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.util.FCMAccessTokenProvider;
import okhttp3.MediaType;

public class ShowAlarmDataActivity extends AppCompatActivity {
    private static final String TAG = "ShowAlarmDataActivity";
    private Spinner dropdownMenu;
    private List<String> connectedUsers = new ArrayList<>();
    private String currentUsername;
    private AlarmMonitoringService alarmMonitoringService;
    private boolean isMonitoring = false;
    private AlertDialog turnOffDialog;
    private AlertDialog turnOnDialog;
    private Button monitoringServiceButton;

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

        Button viewConnectedAccountsButton = findViewById(R.id.viewConnectedAccountsButton);
        viewConnectedAccountsButton.setOnClickListener(v -> {
            Log.d(TAG, "Navigating to ConnectedAccountsActivity");
            Toast.makeText(this, "Opening connected accounts", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, ConnectedAccountActivity.class);
            startActivity(intent);
        });

        // Initialize the monitoring service button
        monitoringServiceButton = findViewById(R.id.monitoringservice);
        SharedPreferences prefs = getSharedPreferences("monitoring_state", MODE_PRIVATE);
        isMonitoring = prefs.getBoolean("is_monitoring", false);
        updateMonitoringServiceButtonText();
        // Set up the monitoring service button to toggle between turning on or off the service
        monitoringServiceButton.setOnClickListener(v -> {
            if (isMonitoring) {
                showTurnOffMonitoringDialog();
            } else {
                showTurnOnMonitoringDialog();
            }
        });

        // Set up the turn off dialog
        turnOffDialog = new AlertDialog.Builder(this)
                .setView(R.layout.dialog_turn_off_monitoring)
                .create();

        turnOffDialog.setOnShowListener(dialogInterface -> {
            Button cancelButton = turnOffDialog.findViewById(R.id.button_cancel);
            Button yesButton = turnOffDialog.findViewById(R.id.button_yes);

            cancelButton.setOnClickListener(view -> turnOffDialog.dismiss());
            yesButton.setOnClickListener(view -> {
                stopMonitoringService();
                turnOffDialog.dismiss();
            });
        });

        // Set up the turn on dialog
        turnOnDialog = new AlertDialog.Builder(this)
                .setView(R.layout.dialog_turn_on_monitoring)
                .create();

        turnOnDialog.setOnShowListener(dialogInterface -> {
            Button cancelButton = turnOnDialog.findViewById(R.id.button_cancel);
            Button turnOnButton = turnOnDialog.findViewById(R.id.button_turn_on);
            CheckBox checkbox = turnOnDialog.findViewById(R.id.checkbox_understand);

            // Enable the "Turn On" button only when the checkbox is checked
            turnOnButton.setEnabled(checkbox.isChecked());

            checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                turnOnButton.setEnabled(isChecked);
            });

            cancelButton.setOnClickListener(view -> turnOnDialog.dismiss());
            turnOnButton.setOnClickListener(view -> {
                if (checkbox.isChecked()) {
                    startMonitoringService();
                    turnOnDialog.dismiss();
                }
            });
        });

    }
    private void showTurnOffMonitoringDialog() {
        if (!turnOffDialog.isShowing()) {
            turnOffDialog.show();
        }
    }

    private void showTurnOnMonitoringDialog() {
        if (!turnOnDialog.isShowing()) {
            turnOnDialog.show();
        }
    }
    private void startMonitoringService() {
        Intent serviceIntent = new Intent(this, AlarmMonitoringService.class);
        startService(serviceIntent);
        isMonitoring = true;

        SharedPreferences prefs = getSharedPreferences("monitoring_state", MODE_PRIVATE);
        prefs.edit().putBoolean("is_monitoring", true).apply();
        updateMonitoringServiceButtonText();
    }

    private void stopMonitoringService() {
        Intent serviceIntent = new Intent(this, AlarmMonitoringService.class);
        stopService(serviceIntent);
        isMonitoring = false;

        SharedPreferences prefs = getSharedPreferences("monitoring_state", MODE_PRIVATE);
        prefs.edit().putBoolean("is_monitoring", false).apply();
        updateMonitoringServiceButtonText();
    }
    private void updateMonitoringServiceButtonText() {
        if (isMonitoring) {
            monitoringServiceButton.setText(R.string.turn_off_monitoring);
        } else {
            monitoringServiceButton.setText(R.string.turn_on_monitoring);
        }
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
                        Toast.makeText(this, "Terjadi Kesalahan saat mengambil data pengguna", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchAlarmData(String username) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("alarmData")
                .document(username)
                .collection("data")
                .orderBy("timestamp", Query.Direction.DESCENDING)
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
                        Toast.makeText(this, "TIdak ada Data tersedia", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(this, "Tidak ada akun terhubung", Toast.LENGTH_SHORT).show();
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
        builder.setTitle("Masukan email akun yang dituju");

        final EditText input = new EditText(this);
        input.setHint("Email Tujuan");
        builder.setView(input);

        builder.setPositiveButton("Kirim Permintaan", (dialog, which) -> {
            String targetEmail = input.getText().toString().trim();
            if (!targetEmail.isEmpty()) {
                sendConnectionRequest(targetEmail);
            } else {
                Toast.makeText(this, "Email tidak boleh kosong", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Batalkan", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void sendConnectionRequest(String targetEmail) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            Log.e(TAG, "sendConnectionRequest: Current user is null. User not authenticated.");
            Toast.makeText(this, "User tidak login", Toast.LENGTH_SHORT).show();
            return;
        }

        // Extract current user's details
        String requesterUsername = user.getEmail().split("@")[0];
        String requesterEmail = user.getEmail();
        String requesterId = user.getUid();

        Log.d(TAG, "sendConnectionRequest: Current username: " + requesterUsername);

        // Fetch the requester's FCM token
        db.collection("users")
                .document(requesterUsername)
                .get()
                .addOnSuccessListener(requesterDoc -> {
                    if (requesterDoc.exists() && requesterDoc.contains("fcmToken")) {
                        String requesterFCMToken = requesterDoc.getString("fcmToken");
                        Log.d(TAG, "sendConnectionRequest: Requester's FCM token: " + requesterFCMToken);

                        // Search for the target user's information
                        Log.d(TAG, "sendConnectionRequest: Searching for target user with email: " + targetEmail);
                        db.collection("users")
                                .whereEqualTo("email", targetEmail)
                                .get()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                                        Log.d(TAG, "sendConnectionRequest: Target user found for email: " + targetEmail);

                                        // Extract target user's details
                                        String targetUsername = task.getResult().getDocuments().get(0).getId();
                                        String targetUserId = task.getResult().getDocuments().get(0).getString("UID");
                                        String targetFcmToken = task.getResult().getDocuments().get(0).getString("fcmToken");

                                        Log.d(TAG, "sendConnectionRequest: Target username: " + targetUsername);
                                        Log.d(TAG, "sendConnectionRequest: Target FCM token: " + targetFcmToken);

                                        // Build request data
                                        Map<String, Object> request = new HashMap<>();
                                        request.put("requesterUsername", requesterUsername);
                                        request.put("requesterEmail", requesterEmail);
                                        request.put("requesterId", requesterId);
                                        request.put("requesterFCMToken", requesterFCMToken);
                                        request.put("targetUsername", targetUsername);
                                        request.put("targetEmail", targetEmail);
                                        request.put("targetUserId", targetUserId);
                                        request.put("status", "pending");
                                        request.put("timestamp", FieldValue.serverTimestamp());

                                        Log.d(TAG, "sendConnectionRequest: Saving connection request to Firestore...");
                                        db.collection("connectionRequests")
                                                .document(targetUsername)
                                                .collection("requests")
                                                .add(request)
                                                .addOnSuccessListener(unused -> {
                                                    Log.d(TAG, "sendConnectionRequest: Request saved for user: " + targetUsername);
                                                    Toast.makeText(this, "Permintaan Sukses dikirim", Toast.LENGTH_SHORT).show();

                                                    if (targetFcmToken != null && !targetFcmToken.isEmpty()) {
                                                        Log.d(TAG, "sendConnectionRequest: Sending FCM notification...");
                                                        sendFCMNotification(this, targetFcmToken,
                                                                "Connection Request",
                                                                "You have a new connection request from " + requesterUsername);
                                                    } else {
                                                        Log.w(TAG, "sendConnectionRequest: No valid FCM token for user: " + targetUsername);
                                                    }
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.e(TAG, "sendConnectionRequest: Firestore request save failed", e);
                                                    Toast.makeText(this, "Gagal mengirim permintaan. Coba lagi.", Toast.LENGTH_SHORT).show();
                                                });
                                    } else {
                                        Log.w(TAG, "sendConnectionRequest: No user found with email: " + targetEmail);
                                        Toast.makeText(this, "Email tidak ditemukan", Toast.LENGTH_SHORT).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "sendConnectionRequest: Error querying user by email", e);
                                    Toast.makeText(this, "Gagal mencari akun yang dituju, coba lagi.", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        Log.w(TAG, "sendConnectionRequest: Requester document not found or missing FCM token.");
                        Toast.makeText(this, "Gagal mendapatkan FCM token pengirim.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "sendConnectionRequest: Error fetching requester's FCM token", e);
                    Toast.makeText(this, "Gagal mendapatkan data pengirim. Coba lagi.", Toast.LENGTH_SHORT).show();
                });
    }


    private void sendFCMNotification(Context context, String token, String title, String body) {
        Log.d(TAG, "sendFCMNotification: Preparing to send notification. Title: " + title + ", Body: " + body);

        String FCM_API_URL = "https://fcm.googleapis.com/v1/projects/smartschiz-6a1d3/messages:send";

        JSONObject payload = new JSONObject();
        try {
            JSONObject message = new JSONObject();

            // Notification object (optional, for displaying a system notification)
            JSONObject notification = new JSONObject();
            notification.put("title", title);
            notification.put("body", body);
            message.put("notification", notification);

            // Data object (for sending data to the app and triggering the intent)
            JSONObject data = new JSONObject();
            data.put("click_action", "OPEN_PENDING_REQUESTS"); // This will be sent as an extra
            message.put("data", data);

            message.put("token", token);

            payload.put("message", message);
        } catch (JSONException e) {
            Log.e(TAG, "sendFCMNotification: Failed to create JSON payload", e);
            return;
        }

        // Execute in a background thread
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String accessToken = FCMAccessTokenProvider.getAccessToken(context);
                Log.d(TAG, "sendFCMNotification: Retrieved access token: " + accessToken);

                OkHttpClient client = new OkHttpClient();
                RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), payload.toString());
                Request request = new Request.Builder()
                        .url(FCM_API_URL)
                        .post(requestBody)
                        .addHeader("Authorization", "Bearer " + accessToken)
                        .addHeader("Content-Type", "application/json; charset=utf-8")
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e(TAG, "sendFCMNotification: Failed to send notification", e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "sendFCMNotification: Notification sent successfully");
                        } else {
                            Log.e(TAG, "sendFCMNotification: Failed with code: " + response.code() + ", message: " + response.body().string());
                        }
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "sendFCMNotification: Failed to retrieve access token", e);
            }
        });
    }
}

