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
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import android.media.RingtoneManager;
import android.media.Ringtone;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;

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
import nodomain.freeyourgadget.gadgetbridge.activities.charts.AbstractChartsActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.data.DashboardStressData;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;
import nodomain.freeyourgadget.gadgetbridge.model.RecordedDataTypes;
import nodomain.freeyourgadget.gadgetbridge.model.StressSample;
import nodomain.freeyourgadget.gadgetbridge.util.FCMAccessTokenProvider;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
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
    private boolean isAlarmTriggered = false;
    private FusedLocationProviderClient fusedLocationClient;
    private int currentHeartRate = -1; // Store the latest heart rate
    private int currentStressLevel = -1; // Store the latest stress level
    private long heartRateAbnormalStartTime = -1;
    private long stressLevelAbnormalStartTime = -1;
    private static final long ONE_MINUTE = 60 * 1000; // 1 minute in milliseconds
    private static final long FIVE_MINUTE = 5 * 60 * 1000;
    private Handler handler; // Handler for periodic tasks
    private Runnable stressFetchRunnable;
    private long lastAlarmTriggeredTime = -1;
    private static final long COOLDOWN_PERIOD = 10 * 60 * 1000; // 5-minute cooldow

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
                        isHeartRateTestInProgress = false;
                    } else {
                        Log.d(TAG, "Unknown heartrate sample type received ");
                        isHeartRateTestInProgress = false;
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
        boolean heartRateAbnormal = currentHeartRate != -1 && (currentHeartRate < 40 || currentHeartRate > 130 );
        boolean stressLevelAbnormal = currentStressLevel != -1 && currentStressLevel > 70;

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
                long elapsedSeconds = elapsedTime / 1000;  // Convert milliseconds to seconds
                Log.d(TAG, "Heart rate still abnormal. Elapsed time: " + elapsedSeconds + " s. Remaining: " + (THREE_MINUTES - elapsedSeconds) + " s");
            }
        } else {
            if (heartRateAbnormalStartTime != -1) {
                Log.d(TAG, "Heart rate returned to normal.");
            }
            heartRateAbnormalStartTime = -1; //
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
                    long elapsedSeconds = elapsedTime / 1000;  // Convert milliseconds to seconds
                    Log.d(TAG, "Stress level still high. Elapsed time: " + elapsedSeconds + " s. Remaining: " + (THREE_MINUTES - elapsedSeconds) + " s");
            }
        } else {
            if (stressLevelAbnormalStartTime != -1) {
                Log.d(TAG, "Stress level returned to normal.");
            }
            stressLevelAbnormalStartTime = -1; // Reset if stress level becomes normal
        }
    }


    private void triggerAlarm(long timestamp, int heartRate, int stressLevel) {
        // 1. Check Cooldown FIRST (and isAlarmTriggered flag)
        if (isAlarmTriggered) {
            Log.d(TAG, "Alarm is already triggered. Skipping (cooldown active).");
            return;
        }
        if (lastAlarmTriggeredTime != -1 && (timestamp - lastAlarmTriggeredTime) < COOLDOWN_PERIOD) {
            Log.d(TAG, "Cooldown period active. Alarm not triggered.");
            return;
        }

        // 2. Set isAlarmTriggered BEFORE other actions
        isAlarmTriggered = true;  // Set the flag *before* doing anything else.
        lastAlarmTriggeredTime = timestamp; // Update last triggered time

        Log.d(TAG, "Triggering alarm...");

        // 3. Save Data and Send Notifications (These should be fast operations)
        saveAlarmTriggeredDataToFirestore(timestamp, heartRate, stressLevel);
        sendNotificationsToConnectedAccounts(heartRate, stressLevel);

        // 4. Show Notification (This can be shown while the activity starts)
        showAlarmNotification(); // Pass heartRate and stressLevel

        // 5. Start Alarm Activity (Crucial for immediate attention)
        Intent alarmIntent = new Intent(this, AlarmActivity.class);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_NO_USER_ACTION |
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(alarmIntent);


        // 6. Reset isAlarmTriggered (using the main looper's handler)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isAlarmTriggered = false;
            Log.d(TAG, "isAlarmTriggered reset after cooldown.");
        }, COOLDOWN_PERIOD);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if ("STOP_SERVICE".equals(intent.getAction())) {
                stopSelf();
                return START_NOT_STICKY; // Ensures service does NOT restart
            }
            if ("RESET_ALARM_TRIGGERED".equals(intent.getAction())) {
                isAlarmTriggered = false;
                Log.d(TAG, "Alarm triggered flag reset.");
            }
        }
        createForegroundNotification();
        return START_STICKY; // Keeps service running unless explicitly stopped
    }

    private void createForegroundNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "alarm_monitoring_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Alarm Monitoring",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Monitoring for abnormal heart rate and stress.");
            channel.enableVibration(true);
            channel.enableLights(true);
            channel.setVibrationPattern(new long[]{0, 1000, 1000});
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
        }

        // Intent to launch the AlarmActivity when notification is clicked
        Intent intent = new Intent(this, AlarmActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Monitoring Active")
                .setContentText("Monitoring for abnormal heart rate and stress.")
                .setSmallIcon(R.drawable.ic_heart)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .setOngoing(true) // Prevent user from swiping it away
                .build();

        startForeground(1, notification);
    }


    private void showAlarmNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "alarm_notification_channel"; // Keep a separate channel

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Alarm Alerts",
                    NotificationManager.IMPORTANCE_HIGH  // CRITICAL: High Importance
            );
            channel.setDescription("Emergency alarm triggered due to abnormal heart rate.");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); // Show on lock screen
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 1000, 500, 1000}); // Stronger pattern
            // ALARM SOUND
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmSound == null) {
                alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION); // Fallback
            }
            channel.setSound(alarmSound, null); // Set the sound on the channel
            notificationManager.createNotificationChannel(channel);
        }

        // 🔥 Intent to force AlarmActivity to open (FULL SCREEN INTENT)
        Intent fullScreenIntent = new Intent(this, AlarmActivity.class);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 🔥 Build a full-screen notification
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_heart) // Use an appropriate icon
                .setContentTitle("🚨 ALARM TRIGGERED!") // Clear and attention-grabbing title
                .setContentText("Abnormal heart rate detected! Tap to open.") // Informative text
                .setPriority(NotificationCompat.PRIORITY_MAX) // CRITICAL: Max priority
                .setCategory(NotificationCompat.CATEGORY_ALARM) // CRITICAL: Alarm category
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
                .setOngoing(false) // IMPORTANT: Not ongoing (alarms are dismissible)
                .setFullScreenIntent(fullScreenPendingIntent, true) // CRITICAL: Full screen intent
                .setDefaults(Notification.DEFAULT_ALL) // Use default sound, vibration, and lights (for compatibility)
                .setVibrate(new long[]{0, 500, 1000, 500, 1000}) // Stronger vibration pattern
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)) // CRITICAL: Alarm sound
                .setAutoCancel(false); // Do NOT auto-cancel (user must take action)


        Notification notification = notificationBuilder.build();
        notificationManager.notify(1001, notification); // Use a unique notification ID
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
                                String notificationBody = constructNotificationBody(username, heartRate, stressLevel);
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

        private String constructNotificationBody(String username,int heartRate, int stressLevel) {
            StringBuilder body = new StringBuilder();
            body.append("Peringatan terjadi dari salah satu akun terhubung |");
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
            Log.d(TAG, "sendFCMNotification: Preparing to send notification. Title: " + title + ", Body: " + body );

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
        Log.d(TAG, "Broadcast receiver registered for ACTION_REALTIME_SAMPLES");
        Log.d(TAG, "Monitoring service started, Broadcast receiver registered");
        handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                initiateHeartRateTest(); // Trigger heart rate test
                handler.postDelayed(this, ONE_MINUTE); // Repeat every minute
            }
        });
        stressFetchRunnable = new Runnable() {
            @Override
            public void run() {
                fetchAndMonitorStress();
                handler.postDelayed(this, FIVE_MINUTE); // Schedule next run
            }
        };
        handler.post(stressFetchRunnable); // Start the periodic task
    }
    private boolean isHeartRateTestInProgress = false; // To track if a test is ongoing

    private void initiateHeartRateTest() {
        if (isHeartRateTestInProgress) {
            Log.d(TAG, "Heart rate test already in progress, skipping...");
            return;
        }

        isHeartRateTestInProgress = true;
        Log.d(TAG, "Initiating heart rate test...");
        GBApplication.deviceService().onHeartRateTest(); // Trigger the test
    }
    private boolean isStressTestInProgress =false;



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

        // Stop foreground service
        stopForeground(true);

        // Stop handler from executing further heart rate checks
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            Log.d(TAG, "Handler tasks stopped.");
        }

        // Stop stress monitoring
        if (stressFetchRunnable != null) {
            handler.removeCallbacks(stressFetchRunnable);
            Log.d(TAG, "Stress fetch task stopped.");
        }

        Log.d(TAG, "Monitoring is Stopping");
    }

}