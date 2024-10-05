package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.Context;
import android.media.Ringtone;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.Button;
import nodomain.freeyourgadget.gadgetbridge.R;


public class AlarmActivity extends AppCompatActivity {
    private static final String TAG = "AlarmActivity";

    private Context context;
    private Vibrator vibrator;
    private static Ringtone ringtone;
    private static boolean isAlarmTriggered = false;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);

        Button stopAlarmButton = findViewById(R.id.stop_vibration_button);
        stopAlarmButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAlarm();
            }
        });
    }

    private void stopAlarm() {
        if (ringtone != null && ringtone.isPlaying()) {
            ringtone.stop();
        }
        isAlarmTriggered = false;
        finish();
    }
}