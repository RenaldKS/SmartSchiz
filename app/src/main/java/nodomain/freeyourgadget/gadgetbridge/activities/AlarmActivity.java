package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.service.AlarmMonitoringService;

public class AlarmActivity extends AppCompatActivity {
    private Vibrator vibrator;
    private Ringtone ringtone;
    private boolean isRingtonePlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔥 Ensure AlarmActivity pops up over other apps & lock screen
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_FULLSCREEN |
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        setContentView(R.layout.activity_alarm);
        TextView alarmText = findViewById(R.id.alarm_text);

        // Display emergency instructions
        String fullText = "Saat Mengalami Halusinasi atau Panik:\n\n" +
                getString(R.string.relaksasi_tips) + "\n\n" +
                getString(R.string.alihkan_perhatian) + "\n\n" +
                getString(R.string.atasi_halusinasi);
        alarmText.setText(fullText);

        // 🔥 Start continuous alarm (vibration + ringtone)
        startAlarm();

        Button stopAlarmButton = findViewById(R.id.stop_vibration_button);
        stopAlarmButton.setOnClickListener(v -> stopAlarm());
    }

    // 🔥 Keeps vibrating & playing sound until stopped
    private void startAlarm() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (audioManager != null) {
            int ringerMode = audioManager.getRingerMode();

            if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                // Play ringtone only once if not already playing
                if (!isRingtonePlaying) {
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
                        isRingtonePlaying = true; // Set flag to prevent overlay
                    }
                }
            }

            // 🔥 Keep vibrating indefinitely, regardless of sound settings
            if (vibrator == null) {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                long[] vibrationPattern = {0, 1000, 1000}; // Vibrate every second
                vibrator.vibrate(vibrationPattern, 0); // Repeat indefinitely
            }
        }
    }

    // 🔥 Stop the alarm when the user presses "Stop"
    private void stopAlarm() {
        if (ringtone != null) {
            ringtone.stop();
            isRingtonePlaying = false; // Reset flag
        }

        if (vibrator != null) {
            vibrator.cancel();
        }

        finish();
    }
}
