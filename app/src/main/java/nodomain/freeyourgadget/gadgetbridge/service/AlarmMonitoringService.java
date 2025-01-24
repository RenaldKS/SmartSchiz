package nodomain.freeyourgadget.gadgetbridge.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AlarmActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.data.DashboardStressData;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.model.StressSample;
import nodomain.freeyourgadget.gadgetbridge.util.FCMAccessTokenProvider;
import nodomain.freeyourgadget.gadgetbridge.util.StressDataUtils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
    private static final long ONE_MINUTE = 60 * 1000; // 1 minute in milliseconds

    private Handler handler; // Handler for periodic tasks
    private Runnable stressFetchRunnable;
    private long lastAlarmTriggeredTime = -1;
    private static final long COOLDOWN_PERIOD = 5 * 60 * 1000; // 1-minute cooldow

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
        boolean heartRateAbnormal = currentHeartRate != -1 && (currentHeartRate < 60 || currentHeartRate > 130);
        boolean stressLevelAbnormal = currentStressLevel != -1 && currentStressLevel > 75;

        Log.d(TAG, "Current Heart Rate: " + currentHeartRate + ", Current Stress Level: " + currentStressLevel);

        if (heartRateAbnormal) {
            Log.d(TAG, "Abnormal heart rate detected: " + currentHeartRate);

            if (heartRateAbnormalStartTime == -1) {
                heartRateAbnormalStartTime = currentTime;
                Log.d(TAG, "Heart rate abnormal start time set: " + heartRateAbnormalStartTime);
            } else if (currentTime - heartRateAbnormalStartTime >= THREE_MINUTES) {
                Log.d(TAG, "Heart rate has been abnormal for 3 minutes. Triggering alarm.");
                if (!isAlarmTriggered) {
                    triggerAlarm(currentTime, currentHeartRate, currentStressLevel);
                    heartRateAbnormalStartTime = -1; // Reset the timer after triggering the alarm
                }
            } else {
                long elapsedTime = currentTime - heartRateAbnormalStartTime;
                Log.d(TAG, "Heart rate still abnormal. Elapsed time: " + elapsedTime + " ms. Remaining: " + (THREE_MINUTES - elapsedTime) + " ms");
            }
        } else {
            if (heartRateAbnormalStartTime != -1) {
                Log.d(TAG, "Heart rate returned to normal.");
            }
            heartRateAbnormalStartTime = -1; // Reset if heart rate becomes normal
        }

        if (stressLevelAbnormal) {
            Log.d(TAG, "High stress level detected: " + currentStressLevel);

            if (stressLevelAbnormalStartTime == -1) {
                stressLevelAbnormalStartTime = currentTime;
                Log.d(TAG, "Stress level abnormal start time set: " + stressLevelAbnormalStartTime);
            } else if (currentTime - stressLevelAbnormalStartTime >= THREE_MINUTES) {
                Log.d(TAG, "Stress level has been high for 3 minutes. Triggering alarm.");
                if (!isAlarmTriggered) {
                    triggerAlarm(currentTime, currentHeartRate, currentStressLevel);
                    stressLevelAbnormalStartTime = -1; // Reset the timer after triggering the alarm
                }
            } else {
                long elapsedTime = currentTime - stressLevelAbnormalStartTime;
                Log.d(TAG, "Stress level still high. Elapsed time: " + elapsedTime + " ms. Remaining: " + (THREE_MINUTES - elapsedTime) + " ms");
            }
        } else {
            if (stressLevelAbnormalStartTime != -1) {
                Log.d(TAG, "Stress level returned to normal.");
            }
            stressLevelAbnormalStartTime = -1; // Reset if stress level becomes normal
        }
    }


    private void triggerAlarm(long timestamp, int heartRate, int stressLevel) {
        if (lastAlarmTriggeredTime != -1 && (timestamp - lastAlarmTriggeredTime) < COOLDOWN_PERIOD) {
            Log.d(TAG, "Cooldown period active. Alarm not triggered.");
            return;
        }

        lastAlarmTriggeredTime = timestamp; // Update the last alarm time
        isAlarmTriggered = true;
        Log.d(TAG, "Triggering alarm...");

        saveAlarmTriggeredDataToFirestore(timestamp, heartRate, stressLevel);
        sendNotificationsToConnectedAccounts(heartRate, stressLevel);

        Intent alarmIntent = new Intent(this, AlarmActivity.class);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(alarmIntent);
    }
    private void showForegroundNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "alarm_notification_channel";
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Alarm Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, AlarmActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Health Alert")
                .setContentText("Abnormal heart rate or stress detected. Tap to view details.")
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(1, notification);
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "RESET_ALARM_TRIGGERED".equals(intent.getAction())) {
            isAlarmTriggered = false;
            Log.d(TAG, "Alarm triggered flag reset.");
        }
        return super.onStartCommand(intent, flags, startId);
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
                                                        Toast.makeText(this, "Gagal Menyimpan data peringatan", Toast.LENGTH_SHORT).show();
                                                    });

                                        })
                                        .addOnFailureListener(e -> {
                                            Log.e(TAG, "Failed to fetch location", e);
                                            Toast.makeText(this, "Gagal mendapatkan data lokasi", Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(this, "Gagal mendapatkan data pengguna", Toast.LENGTH_SHORT).show();
                    });
        }

        private String formatTimestamp(long timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd-MM-yyyy, HH:mm:ss", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }


        private void sendNotificationsToConnectedAccounts(int heartRate, int stressLevel) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user == null) {
                Log.w(TAG, "sendNotificationsToConnectedAccounts: User not authenticated.");
                return;
            }

            String currentUserId = user.getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            Log.d(TAG, "sendNotificationsToConnectedAccounts: Fetching username for current user ID: " + currentUserId);

            // 1. Get the username associated with the current user's UID
            db.collection("users")
                    .whereEqualTo("UID", currentUserId)
                    .get()
                    .addOnSuccessListener(userQuerySnapshot -> {
                        if (userQuerySnapshot != null && !userQuerySnapshot.isEmpty()) {
                            String currentUsername = userQuerySnapshot.getDocuments().get(0).getString("username");

                            Log.d(TAG, "sendNotificationsToConnectedAccounts: Found current username: " + currentUsername);

                            if (currentUsername != null) {
                                Log.d(TAG, "sendNotificationsToConnectedAccounts: Current username: " + currentUsername);

                                // 2. Now use the username to construct the correct path
                                DocumentReference userConnectionDocument = db.collection("connectionRequests").document(currentUsername);
                                CollectionReference requestsSubcollection = userConnectionDocument.collection("requests");

                                Log.d(TAG, "sendNotificationsToConnectedAccounts: Looking in collection: connectionRequests");
                                Log.d(TAG, "sendNotificationsToConnectedAccounts: Looking in document: " + userConnectionDocument.getPath());
                                Log.d(TAG, "sendNotificationsToConnectedAccounts: Looking in subcollection: " + requestsSubcollection.getPath());

                                requestsSubcollection
                                        .whereEqualTo("status", "accepted")
                                        .get()
                                        .addOnSuccessListener(querySnapshot -> {
                                            // ... (Rest of the code remains the same: handling querySnapshot)
                                            if (querySnapshot != null) {
                                                Log.d(TAG, "sendNotificationsToConnectedAccounts: Query returned " + querySnapshot.size() + " documents.");
                                                if(querySnapshot.isEmpty()){
                                                    Log.d(TAG, "sendNotificationsToConnectedAccounts: Query returned empty documents");
                                                }
                                                for (QueryDocumentSnapshot document : querySnapshot) {
                                                    Log.d(TAG, "sendNotificationsToConnectedAccounts: Processing document: " + document.getId());
                                                    Log.d(TAG, "sendNotificationsToConnectedAccounts: Document data: " + document.getData()); // Log document data
                                                    String requesterUsername = document.getString("requesterUsername");
                                                    if (requesterUsername != null) {
                                                        Log.d(TAG, "sendNotificationsToConnectedAccounts: Found connected requester: " + requesterUsername);
                                                        fetchAndSendNotification(requesterUsername, heartRate, stressLevel);
                                                    } else {
                                                        Log.w(TAG, "sendNotificationsToConnectedAccounts: Document " + document.getId() + " is missing requesterUsername field.");
                                                    }
                                                }
                                            } else {
                                                Log.w(TAG, "sendNotificationsToConnectedAccounts: QuerySnapshot is null.");
                                            }
                                        })
                                        .addOnFailureListener(e -> Log.w(TAG, "sendNotificationsToConnectedAccounts: Failed to fetch connection requests: " + e.getMessage()));
                            } else {
                                Log.w(TAG, "sendNotificationsToConnectedAccounts: Current username not found for UID: " + currentUserId);
                            }
                        } else {
                            Log.w(TAG, "sendNotificationsToConnectedAccounts: User document not found for UID: " + currentUserId);
                        }
                    })
                    .addOnFailureListener(e -> Log.w(TAG, "sendNotificationsToConnectedAccounts: Error fetching user document: " + e.getMessage()));
        }

        private void fetchAndSendNotification(String username, int heartRate, int stressLevel) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            Log.d(TAG, "fetchAndSendNotification: Fetching user data for requester username: " + username);

            db.collection("users")
                    .document(username) // Use the requester's username as the document ID
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Log.d(TAG, "fetchAndSendNotification: User document found for requester: " + username);
                            String token = documentSnapshot.getString("fcmToken");
                            if (token != null) {
                                Log.d(TAG, "fetchAndSendNotification: Found FCM token for requester: " + username);
                                String notificationBody = constructNotificationBody(heartRate, stressLevel);
                                // Pass the context (this) as the first argument
                                Log.d(TAG, "fetchAndSendNotification: Constructing notification body: " + notificationBody);
                                sendFCMNotification(this, token, "Health Alert", notificationBody);
                            } else {
                                Log.w(TAG, "fetchAndSendNotification: fcmToken is missing for requester: " + username);
                            }
                        } else {
                            Log.w(TAG, "fetchAndSendNotification: User document does not exist for requester: " + username);
                        }
                    })
                    .addOnFailureListener(e -> Log.w(TAG, "fetchAndSendNotification: Failed to fetch user document for requester: " + username, e));
        }

        private String constructNotificationBody(int heartRate, int stressLevel) {
            StringBuilder body = new StringBuilder();
            if (heartRate != -1) {
                body.append("Abnormal heart rate: ").append(heartRate);
            }
            if (stressLevel != -1) {
                if (body.length() > 0) body.append(" | ");
                body.append("High stress level: ").append(stressLevel);
            }
            Log.d(TAG, "constructNotificationBody: Constructed notification body: " + body.toString());
            return body.toString();
        }

        private void sendFCMNotification(Context context, String token, String title, String body) {
            Log.d(TAG, "sendFCMNotification: Preparing to send notification. Title: " + title + ", Body: " + body + ", Token: " + token);

            String FCM_API_URL = "https://fcm.googleapis.com/v1/projects/smartschiz-6a1d3/messages:send";

            JSONObject payload = new JSONObject();
            try {
                JSONObject message = new JSONObject();
                JSONObject notification = new JSONObject();

                notification.put("title", title);
                notification.put("body", body);

                message.put("notification", notification);
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
        handler = new Handler(Looper.getMainLooper());
        stressFetchRunnable = new Runnable() {
            @Override
            public void run() {
                fetchAndMonitorStress();
                handler.postDelayed(this, ONE_MINUTE); // Schedule next run
            }
        };
        handler.post(stressFetchRunnable); // Start the periodic task
    }
    private void fetchAndMonitorStress() {
        List<GBDevice> devices = GBApplication.app().getDeviceManager().getDevices();
        StressSample latestSample = StressDataUtils.getLatestStressSample(devices, this);

        if (latestSample != null) {
            currentStressLevel = latestSample.getStress();
            Log.d(TAG, "Latest stress value fetched: " + currentStressLevel);

            // Broadcast the latest stress value
            Intent intent = new Intent(DashboardStressData.ACTION_STRESS_DATA_UPDATED);
            intent.putExtra(DashboardStressData.EXTRA_STRESS_VALUE, currentStressLevel);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            // Check for abnormalities
            checkAndHandleAbnormalReadings(System.currentTimeMillis());
        } else {
            Log.d(TAG, "No stress data available to monitor");
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Receiver unregistered");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
    }
}