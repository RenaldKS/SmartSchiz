package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import nodomain.freeyourgadget.gadgetbridge.R;

public class AlarmActivity extends AppCompatActivity {
    private static final String TAG = "AlarmActivity";

    private Vibrator vibrator;
    private static Ringtone ringtone;
    private static boolean isAlarmTriggered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        // Initialize vibrator
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Start alarm (ringtone and vibration)
        startAlarm();

        Button stopAlarmButton = findViewById(R.id.stop_vibration_button);
        stopAlarmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAlarm();
            }
        });
    }

    private void startAlarm() {
        // Start ringtone
        if (ringtone == null) {
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            }
            ringtone = RingtoneManager.getRingtone(getApplicationContext(), alarmUri);
            ringtone.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
        }

        if (!ringtone.isPlaying()) {
            ringtone.play();
        }

        // Start vibration
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] vibrationPattern = {0, 500, 1000}; // Vibrate for 500ms, pause for 1000ms
            vibrator.vibrate(vibrationPattern, 0); // Repeat the pattern (0 = repeat indefinitely)
        }

        isAlarmTriggered = true;
    }

    private void stopAlarm() {
        // Stop ringtone
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }

        // Stop vibration
        if (vibrator != null) {
            vibrator.cancel();
        }

        isAlarmTriggered = false;
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Ensure the alarm is stopped when the activity is destroyed
        stopAlarm();
    }
}
