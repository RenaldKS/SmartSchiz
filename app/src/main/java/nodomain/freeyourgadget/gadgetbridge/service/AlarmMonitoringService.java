package nodomain.freeyourgadget.gadgetbridge.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import nodomain.freeyourgadget.gadgetbridge.activities.AlarmActivity;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService;

public class AlarmMonitoringService extends Service {
    private static final String TAG = "AlarmMonitoringService";
    private static final long THREE_MINUTE_IN_MILLISECONDS = 60 * 1000 * 3;
    private long firstAbnormalTimestamp = -1;
    private static boolean isAlarmTriggered = false;
    private static final String PREFS_NAME = "AlarmPreferences";
    private static final String PREF_ALARM_TRIGGERED = "isAlarmTriggered";

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DeviceService.ACTION_REALTIME_SAMPLES.equals(intent.getAction())) {
                Log.d(TAG, "Received ACTION_REALTIME_SAMPLES intent");
                ActivitySample sample = (ActivitySample) intent.getSerializableExtra(DeviceService.EXTRA_REALTIME_SAMPLE);
                if (sample != null) {
                    int heartRate = sample.getHeartRate();
                    Log.d(TAG, "Heart rate received: " + heartRate);
                    long currentTime = System.currentTimeMillis();

                    if (heartRate < 60 || heartRate > 80) {
                        Log.d(TAG, "Abnormal heart rate detected: " + heartRate);
                        if (firstAbnormalTimestamp == -1) {
                            firstAbnormalTimestamp = currentTime;
                        } else if (currentTime - firstAbnormalTimestamp >= THREE_MINUTE_IN_MILLISECONDS) {
                            if (!isAlarmActive()) {
                                Log.d(TAG, "Abnormal heart rate sustained for 3 minutes. Triggering alarm.");
                                triggerAlarm();
                            } else {
                                Log.d(TAG, "Alarm already triggered. Skipping...");
                            }
                        }
                    } else {
                        // Reset the timestamp if heart rate is back to normal
                        firstAbnormalTimestamp = -1;
                        resetAlarmState();
                        Log.d(TAG, "Heart rate back to normal: " + heartRate);
                    }
                } else {
                    Log.d(TAG, "ActivitySample is null");
                }
            }
        }
    };

    // Check if the alarm is active using the flag or SharedPreferences
    private boolean isAlarmActive() {
        if (!isAlarmTriggered) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            isAlarmTriggered = prefs.getBoolean(PREF_ALARM_TRIGGERED, false);
        }
        return isAlarmTriggered;
    }

    // Set the alarm trigger state, updating both the static flag and SharedPreferences
    private void setAlarmTriggered(boolean triggered) {
        isAlarmTriggered = triggered;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(PREF_ALARM_TRIGGERED, triggered);
        editor.apply();  // Save changes
    }

    // Reset the alarm state when heart rate returns to normal
    private void resetAlarmState() {
        if (isAlarmTriggered) {
            setAlarmTriggered(false);
            Log.d(TAG, "Alarm state reset as heart rate returned to normal.");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't need to bind the service for now
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
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

    private void triggerAlarm() {
        // Launch the AlarmActivity when alarm is triggered
        Intent alarmIntent = new Intent(this, AlarmActivity.class);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(alarmIntent);

        // Set the alarm as triggered
        setAlarmTriggered(true);
        Log.d(TAG, "AlarmActivity started from service, alarm state set to triggered.");
    }
}
