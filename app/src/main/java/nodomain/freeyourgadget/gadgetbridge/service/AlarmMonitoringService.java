package nodomain.freeyourgadget.gadgetbridge.service;

import android.Manifest;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.activities.AlarmActivity;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.model.StressSample;

public class AlarmMonitoringService extends Service {
    private static final String TAG = "AlarmMonitoringService";
    private static final long THREE_MINUTE_IN_MILLISECONDS = 60 * 1000 * 3;
    private long firstAbnormalTimestamp = -1;
    private static boolean isAlarmTriggered = false;
    private static final String PREFS_NAME = "AlarmPreferences";
    private static final String PREF_ALARM_TRIGGERED = "isAlarmTriggered";

    private FusedLocationProviderClient fusedLocationClient;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DeviceService.ACTION_REALTIME_SAMPLES.equals(intent.getAction())) {
                Log.d(TAG, "Received ACTION_REALTIME_SAMPLES intent");

                if (intent.hasExtra(DeviceService.EXTRA_REALTIME_SAMPLE)) {
                    Object sample = intent.getSerializableExtra(DeviceService.EXTRA_REALTIME_SAMPLE);

                    if (sample instanceof ActivitySample) {
                        ActivitySample activitySample = (ActivitySample) sample;
                        int heartRate = activitySample.getHeartRate();
                        long timestamp = System.currentTimeMillis();
                        handleAbnormalHeartRate(heartRate, timestamp);
                    } else if (sample instanceof StressSample) {
                        StressSample stressSample = (StressSample) sample;
                        int stressLevel = stressSample.getStress();
                        long timestamp = System.currentTimeMillis();
                        handleAbnormalStress(stressLevel, timestamp);
                    } else {
                        Log.d(TAG, "Unknown sample type received");
                    }
                }
            }
        }
    };

    private void handleAbnormalHeartRate(int heartRate, long timestamp) {
        long currentTime = System.currentTimeMillis();

        if (heartRate < 60 || heartRate > 80) {
            Log.d(TAG, "Abnormal heart rate detected: " + heartRate);

            if (firstAbnormalTimestamp == -1) {
                firstAbnormalTimestamp = currentTime;
            } else if (currentTime - firstAbnormalTimestamp >= THREE_MINUTE_IN_MILLISECONDS) {
                if (!isAlarmActive()) {
                    Log.d(TAG, "Abnormal heart rate sustained for 3 minutes. Triggering alarm.");
                    triggerAlarm(timestamp, heartRate, -1);  // No stress data available
                } else {
                    Log.d(TAG, "Alarm already triggered. Skipping...");
                }
            }
        } else {
            firstAbnormalTimestamp = -1;
            resetAlarmState();
            Log.d(TAG, "Heart rate back to normal: " + heartRate);
        }
    }

    private void handleAbnormalStress(int stressLevel, long timestamp) {
        long currentTime = System.currentTimeMillis();

        if (stressLevel > 70) {  // Example threshold for high stress
            Log.d(TAG, "High stress level detected: " + stressLevel);

            if (firstAbnormalTimestamp == -1) {
                firstAbnormalTimestamp = currentTime;
            } else if (currentTime - firstAbnormalTimestamp >= THREE_MINUTE_IN_MILLISECONDS) {
                if (!isAlarmActive()) {
                    Log.d(TAG, "High stress sustained for 3 minutes. Triggering alarm.");
                    triggerAlarm(timestamp, -1, stressLevel);  // No heart rate data available
                } else {
                    Log.d(TAG, "Alarm already triggered. Skipping...");
                }
            }
        } else {
            firstAbnormalTimestamp = -1;
            resetAlarmState();
            Log.d(TAG, "Stress level back to normal: " + stressLevel);
        }
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

    private boolean isAlarmActive() {
        if (!isAlarmTriggered) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            isAlarmTriggered = prefs.getBoolean(PREF_ALARM_TRIGGERED, false);
        }
        return isAlarmTriggered;
    }

    private void setAlarmTriggered(boolean triggered) {
        isAlarmTriggered = triggered;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_ALARM_TRIGGERED, triggered);
        editor.apply();
    }

    private void resetAlarmState() {
        if (isAlarmTriggered) {
            setAlarmTriggered(false);
            Log.d(TAG, "Alarm state reset as conditions returned to normal.");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(DeviceService.ACTION_REALTIME_SAMPLES);
        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filter);
        Log.d(TAG, "Monitoring service started, Broadcast receiver registered");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        Log.d(TAG, "Monitoring service stopped, Broadcast receiver unregistered");
    }

    private void triggerAlarm(long timestamp, int heartRate, int stressLevel) {
        Intent alarmIntent = new Intent(this, AlarmActivity.class);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(alarmIntent);

        saveAlarmTriggeredDataToFirestore(timestamp, heartRate, stressLevel);
        setAlarmTriggered(true);
        Log.d(TAG, "AlarmActivity started from service, alarm state set to triggered.");
    }
}
