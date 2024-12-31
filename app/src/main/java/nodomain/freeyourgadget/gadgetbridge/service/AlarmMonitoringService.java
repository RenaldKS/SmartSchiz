package nodomain.freeyourgadget.gadgetbridge.service;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.activities.AlarmActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.data.DashboardStressData;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;

public class AlarmMonitoringService extends Service {
    private static final String TAG = "AlarmMonitoringService";
    private static final long THREE_MINUTES = 3 * 60 * 1000;
    private long firstAbnormalTimestamp = -1;
    private boolean isAlarmTriggered = false;
    private FusedLocationProviderClient fusedLocationClient;
    private int currentHeartRate = -1; // Store the latest heart rate
    private int currentStressLevel = -1; // Store the latest stress level
    private long heartRateAbnormalStartTime = -1;
    private long stressLevelAbnormalStartTime = -1;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "Intent Received: Action: " + intent.getAction());
            if (DeviceService.ACTION_REALTIME_SAMPLES.equals(intent.getAction())) {
                Log.d(TAG, "Received ACTION_REALTIME_SAMPLES intent");
                if (intent.hasExtra(DeviceService.EXTRA_REALTIME_SAMPLE)) {
                    Object sample = intent.getSerializableExtra(DeviceService.EXTRA_REALTIME_SAMPLE);

                    if (sample instanceof ActivitySample) {
                        currentHeartRate = ((ActivitySample) sample).getHeartRate();
                        checkAndHandleAbnormalReadings(System.currentTimeMillis());
                    } else {
                        Log.d(TAG, "Unknown heartrate sample type received ");
                    }
                }
            } else if (DashboardStressData.ACTION_STRESS_DATA_UPDATED.equals(intent.getAction())) {
                Log.d(TAG, "Received ACTION_STRESS_DATA_UPDATED intent");
                if (intent.hasExtra(DashboardStressData.EXTRA_STRESS_VALUE)) {
                    int stressLevel = intent.getIntExtra(DashboardStressData.EXTRA_STRESS_VALUE, -1);
                    if (stressLevel != -1) {
                        currentStressLevel = stressLevel; // Update current stress level
                        checkAndHandleAbnormalReadings(System.currentTimeMillis()); // Check both HR and Stress
                        Log.d(TAG, "Successfully fetched stress value from broadcast: " + stressLevel);
                    } else {
                        Log.w(TAG, "Invalid stress value received in broadcast"); // Existing log (FAILURE)
                    }
                } else {
                    Log.w(TAG, "Intent is missing EXTRA_STRESS_VALUE"); // New log (FAILURE: Missing extra)
                }
            }
        }
    };
    private void checkAndHandleAbnormalReadings(long currentTime) {
        boolean heartRateAbnormal = currentHeartRate != -1 && (currentHeartRate < 60 || currentHeartRate > 80);
        boolean stressLevelAbnormal = currentStressLevel != -1 && currentStressLevel > 70;

        // Log current values for context
        Log.d(TAG, "Current Heart Rate: " + currentHeartRate + ", Current Stress Level: " + currentStressLevel);

        if (heartRateAbnormal) {
            Log.d(TAG, "Abnormal heart rate detected: " + currentHeartRate); // Log when HR is abnormal

            if (heartRateAbnormalStartTime == -1) {
                heartRateAbnormalStartTime = currentTime;
                Log.d(TAG, "Heart rate abnormal start time set: " + heartRateAbnormalStartTime); // Log when HR abnormal time starts
            } else if (currentTime - heartRateAbnormalStartTime >= THREE_MINUTES) {
                Log.d(TAG, "Heart rate has been abnormal for 3 minutes. Triggering alarm."); // Log before triggering alarm due to HR
                if (!isAlarmTriggered) {
                    triggerAlarm(currentTime, currentHeartRate, currentStressLevel); // Save current stress
                }
            } else {
                // Log the elapsed time
                long elapsedTime = currentTime - heartRateAbnormalStartTime;
                Log.d(TAG, "Heart rate still abnormal. Elapsed time: " + elapsedTime + " ms. Remaining: " + (THREE_MINUTES-elapsedTime) +" ms");
            }
        } else {
            if (heartRateAbnormalStartTime != -1) {
                Log.d(TAG, "Heart rate returned to normal."); // Log when HR returns to normal
            }
            heartRateAbnormalStartTime = -1;
        }

        if (stressLevelAbnormal) {
            Log.d(TAG, "High stress level detected: " + currentStressLevel); // Log when Stress is abnormal

            if (stressLevelAbnormalStartTime == -1) {
                stressLevelAbnormalStartTime = currentTime;
                Log.d(TAG, "Stress level abnormal start time set: " + stressLevelAbnormalStartTime); // Log when Stress abnormal time starts
            } else if (currentTime - stressLevelAbnormalStartTime >= THREE_MINUTES) {
                Log.d(TAG, "Stress level has been high for 3 minutes. Triggering alarm."); // Log before triggering alarm due to Stress
                if (!isAlarmTriggered) {
                    triggerAlarm(currentTime, currentHeartRate, currentStressLevel); // Save current heartrate
                }
            } else {
                long elapsedTime = currentTime - stressLevelAbnormalStartTime;
                Log.d(TAG, "Stress level still high. Elapsed time: " + elapsedTime + " ms. Remaining: " + (THREE_MINUTES-elapsedTime) +" ms");
            }
        } else {
            if (stressLevelAbnormalStartTime != -1) {
                Log.d(TAG, "Stress level returned to normal."); // Log when Stress returns to normal
            }
            stressLevelAbnormalStartTime = -1;
        }
    }


    private void triggerAlarm(long timestamp, int heartRate, int stressLevel) {
        isAlarmTriggered = true;
        Log.d(TAG, "Triggering alarm...");

        saveAlarmTriggeredDataToFirestore(timestamp, heartRate, stressLevel);
        sendNotificationsToConnectedAccounts(heartRate, stressLevel);

        Intent alarmIntent = new Intent(this, AlarmActivity.class);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(alarmIntent);
    }

    private void saveAlarmTriggeredDataToFirestore(long timestamp, int heartRate, int stressLevel) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "User not authenticated, cannot write to Firestore");
            return;
        }

        String userId = user.getUid(); // Use UID as the unique identifier
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String formattedTimestamp = formatTimestamp(timestamp);

        // Query Firestore "users" collection to find the user document based on UID
        db.collection("users")
                .whereEqualTo("UID", userId) // Use UID field to find the user document
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        // Retrieve the username from the query results
                        String username = queryDocumentSnapshots.getDocuments().get(0).getString("username");

                        if (username != null) {
                            // Check location permissions
                            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                                    != PackageManager.PERMISSION_GRANTED &&
                                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                                            != PackageManager.PERMISSION_GRANTED) {
                                Log.w(TAG, "Location permissions are not granted, skipping location data");
                                return;
                            }

                            // Fetch location and save data
                            fusedLocationClient.getLastLocation()
                                    .addOnSuccessListener(location -> {
                                        Map<String, Object> alarmData = new HashMap<>();
                                        alarmData.put("timestamp", formattedTimestamp);
                                        alarmData.put("heartRate", heartRate);
                                        alarmData.put("stressLevel", stressLevel);

                                        if (location != null) {
                                            String mapsLink = "https://www.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
                                            alarmData.put("locationLink", mapsLink);
                                        } else {
                                            Log.w(TAG, "Location data is unavailable");
                                        }

                                        // Save data to Firestore using username as the document ID
                                        db.collection("alarmData")
                                                .document(username) // Use username as the parent document
                                                .collection("data") // Store the data in the "data" subcollection
                                                .add(alarmData) // Add the data with a unique document ID
                                                .addOnSuccessListener(documentReference -> {
                                                    Log.d(TAG, "Data written successfully with ID: " + documentReference.getId());
                                                    Toast.makeText(this, "Alarm data saved successfully", Toast.LENGTH_SHORT).show();
                                                })
                                                .addOnFailureListener(e -> {
                                                    Log.w(TAG, "Failed to write document to Firestore", e);
                                                    Toast.makeText(this, "Failed to save alarm data", Toast.LENGTH_SHORT).show();
                                                });

                                    })
                                    .addOnFailureListener(e -> {
                                        Log.e(TAG, "Failed to fetch location", e);
                                        Toast.makeText(this, "Failed to fetch location data", Toast.LENGTH_SHORT).show();
                                    });
                        } else {
                            Log.w(TAG, "Username not found");
                        }
                    } else {
                        Log.w(TAG, "No user document found for UID");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error fetching user data", e);
                    Toast.makeText(this, "Failed to retrieve user data", Toast.LENGTH_SHORT).show();
                });
    }

    private String formatTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd-MM-yyyy, HH:mm:ss", Locale.getDefault());
        return sdf.format(new Date(timestamp));
    }


    private void sendNotificationsToConnectedAccounts(int heartRate, int stressLevel) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Log.w(TAG, "User not authenticated, cannot fetch connected accounts");
            return;
        }

        String currentUserId = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("connectionRequests")
                .document(currentUserId)
                .collection("requests")
                .whereEqualTo("status", "accepted")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (QueryDocumentSnapshot document : querySnapshot) {
                        String connectedUsername = document.getString("targetUsername");
                        if (connectedUsername != null) {
                            fetchAndSendNotification(connectedUsername, heartRate, stressLevel);
                        } else {
                            Log.w(TAG, "targetUsername field is missing in the document");
                        }
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Failed to fetch connected accounts", e));
    }

    private void fetchAndSendNotification(String username, int heartRate, int stressLevel) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users")
                .document(username)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String token = documentSnapshot.getString("fcmToken");
                        if (token != null) {
                            String notificationBody = (heartRate != -1 ? "Abnormal heart rate: " + heartRate : "") +
                                    (stressLevel != -1 ? " | High stress level: " + stressLevel : "");
                            sendFCMNotification(token, "Health Alert", notificationBody);
                        } else {
                            Log.w(TAG, "fcmToken field is missing for user: " + username);
                        }
                    } else {
                        Log.w(TAG, "User document does not exist: " + username);
                    }
                })
                .addOnFailureListener(e -> Log.w(TAG, "Failed to fetch user data for notification", e));
    }

    private void sendFCMNotification(String token, String title, String body) {
        Log.d(TAG, "Sending FCM notification: " + title + " - " + body + " to token: " + token);
        // Implement FCM notification sending logic here (using FCM library or HTTP request)
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        IntentFilter filter = new IntentFilter(DeviceService.ACTION_REALTIME_SAMPLES);
        filter.addAction(DashboardStressData.ACTION_STRESS_DATA_UPDATED);
        Log.d(TAG, "Receiver registered for actions: " + DeviceService.ACTION_REALTIME_SAMPLES + " and " + DashboardStressData.ACTION_STRESS_DATA_UPDATED);

        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filter);
        Log.d(TAG, "Monitoring service started, Broadcast receiver registered");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Receiver unregistered");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
    }
}