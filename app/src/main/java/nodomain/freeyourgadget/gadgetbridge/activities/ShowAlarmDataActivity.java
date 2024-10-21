package nodomain.freeyourgadget.gadgetbridge.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.R;

public class ShowAlarmDataActivity extends AppCompatActivity {
    private static final String TAG = "ShowAlarmDataActivity";
    private ListView alarmDataListView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_alarm_data);

        alarmDataListView = findViewById(R.id.alarmDataListView);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            fetchAlarmData(user.getUid());
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();  // Close the activity if the user is not authenticated
        }
    }

    private void fetchAlarmData(String userId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("alarmData")
                .whereEqualTo("userId", userId)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        if (!querySnapshot.isEmpty()) {
                            List<String> alarmDataList = new ArrayList<>();
                            for (QueryDocumentSnapshot document : querySnapshot) {
                                // Retrieve the fields
                                String timestampString = document.getString("timestamp");
                                Long heartRateLong = document.getLong("heartRate");
                                Long stressLevelLong = document.getLong("stressLevel");

                                // Check for null values before accessing intValue()
                                if (heartRateLong != null && stressLevelLong != null) {
                                    int heartRate = heartRateLong.intValue();
                                    int stressLevel = stressLevelLong.intValue();

                                    // Use the retrieved timestamp directly since it's a string
                                    String formattedAlarmData = "Timestamp: " + timestampString +
                                            "\nHeart Rate: " + heartRate +
                                            "\nStress Level: " + stressLevel;
                                    alarmDataList.add(formattedAlarmData);
                                } else {
                                    Log.w(TAG, "Heart rate or stress level is null for document: " + document.getId());
                                }
                            }
                            displayAlarmData(alarmDataList);
                        } else {
                            Toast.makeText(this, "No alarm data found", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.w(TAG, "Error getting documents: ", task.getException());
                        Toast.makeText(this, "Error fetching data", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void displayAlarmData(List<String> alarmDataList) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, alarmDataList);
        alarmDataListView.setAdapter(adapter);
    }
}
