package nodomain.freeyourgadget.gadgetbridge.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import nodomain.freeyourgadget.gadgetbridge.activities.AlarmActivity;

public class FCMReciever extends BroadcastReceiver {
    private static final String TAG = "FCMReciever";
    public static final String FCM_MESSAGE_RECEIVED = "com.google.firebase.messaging.MESSAGE_RECEIVED";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive: Intent received");
        if (intent.getAction() != null && intent.getAction().equals(FCM_MESSAGE_RECEIVED)) {
            if (intent.getExtras() != null) {
                String clickAction = intent.getExtras().getString("click_action");
                String googleMapsUrl = intent.getExtras().getString("googleMapsUrl");

                if (clickAction != null && clickAction.equals("OPEN_MAP") && googleMapsUrl != null) {
                    try {
                        Uri gmmIntentUri = Uri.parse(googleMapsUrl);
                        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                        mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(mapIntent);
                    } catch (Exception e) {
                        Log.e(TAG, "onReceive: Error opening map", e);
                        // Handle the exception, e.g., show a Toast message
                    }

                } else if (clickAction != null && clickAction.equals("OPEN_PENDING_REQUESTS")) {
                    Intent i = new Intent(context, AlarmActivity.class);
                    i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    context.startActivity(i);
                }
            } else {
                Log.d(TAG, "onReceive: Intent extras are null");
            }
        }
    }
}