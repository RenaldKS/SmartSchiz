package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.service.AlarmMonitoringService;

public class AlarmActivity extends AppCompatActivity {
    private static final String TAG = "AlarmActivity";
    TextView alarmText;
    private Vibrator vibrator;
    private Ringtone ringtone;
    private boolean isAlarmTriggered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);
        alarmText = findViewById(R.id.alarm_text);

        // Get strings from resources
        String relaxationTechniques = getString(R.string.relaksasi_tips);
        String distractionActivities = getString(R.string.alihkan_perhatian);
        String copingWithHallucinations = getString(R.string.atasi_halusinasi);

        // Combine and format the text
        String fullText = "Saat Mengalami Halusinasi atau Panik:\n" + relaxationTechniques + "\n" + distractionActivities + "\n" + copingWithHallucinations;

        alarmText.setText(fullText);
        // Initialize vibrator
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Start alarm (ringtone and/or vibration)
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
        // Get AudioManager to check ringer mode
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (audioManager != null) {
            int ringerMode = audioManager.getRingerMode();

            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                // Play ringtone
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) {
                    alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                }
                ringtone = RingtoneManager.getRingtone(getApplicationContext(), alarmUri);
                if (ringtone != null) {
                    ringtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build());
                    ringtone.play();
                }
            } else if (ringerMode == AudioManager.RINGER_MODE_VIBRATE || ringerMode == AudioManager.RINGER_MODE_SILENT) {
                // Vibrate only
                if (vibrator != null && vibrator.hasVibrator()) {
                    long[] vibrationPattern = {0, 500, 1000}; // Vibrate for 500ms, pause for 1000ms
                    vibrator.vibrate(vibrationPattern, 0); // Repeat the pattern (0 = repeat indefinitely)
                }
            }

            isAlarmTriggered = true;
        }
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
        // Notify the service to reset alarm triggered flag
        Intent intent = new Intent(this, AlarmMonitoringService.class);
        intent.setAction("RESET_ALARM_TRIGGERED");
        startService(intent);
    }
}
