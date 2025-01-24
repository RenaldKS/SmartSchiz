package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Executors;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.util.FCMAccessTokenProvider;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class PendingRequestActivity extends AppCompatActivity {
    private static final String TAG = "PendingRequestActivity";

    private LinearLayout pendingRequestsLayout;
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_requests);

        pendingRequestsLayout = findViewById(R.id.pending_requests_layout);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        handleIntentFromNotification();

        fetchPendingRequests();
    }


    private void handleIntentFromNotification() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            String clickAction = intent.getExtras().getString("click_action");
            if ("OPEN_PENDING_REQUESTS".equals(clickAction)) {
                // Notification was clicked, do nothing as fetchPendingRequests() will handle it
                Log.d(TAG, "Notification clicked! Opening pending requests.");
            }
        }
    }

    private void fetchPendingRequests() {
        String userEmail = auth.getCurrentUser().getEmail();

        if (userEmail != null) {
            db.collection("users")
                    .whereEqualTo("email", userEmail)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            String loggedInUsername = task.getResult().getDocuments().get(0).getString("username");

                            db.collection("connectionRequests")
                                    .document(loggedInUsername)
                                    .collection("requests")
                                    .whereEqualTo("status", "pending")
                                    .get()
                                    .addOnCompleteListener(requestTask -> {
                                        if (requestTask.isSuccessful()) {
                                            pendingRequestsLayout.removeAllViews();

                                            for (QueryDocumentSnapshot document : requestTask.getResult()) {
                                                String requesterUsername = document.getString("requesterUsername");
                                                String requesterEmail = document.getString("requesterEmail");

                                                View requestView = getLayoutInflater().inflate(R.layout.request_item_layout, pendingRequestsLayout, false);

                                                TextView usernameTextView = requestView.findViewById(R.id.requester_username);
                                                TextView emailTextView = requestView.findViewById(R.id.requester_email);

                                                usernameTextView.setText(requesterUsername);
                                                emailTextView.setText(requesterEmail);

                                                Button acceptButton = requestView.findViewById(R.id.accept_button);
                                                Button declineButton = requestView.findViewById(R.id.decline_button);

                                                acceptButton.setOnClickListener(v -> handleRequestAction(document.getId(), true, loggedInUsername));
                                                declineButton.setOnClickListener(v -> handleRequestAction(document.getId(), false, loggedInUsername));

                                                pendingRequestsLayout.addView(requestView);
                                            }
                                        } else {
                                            Log.e(TAG, "Failed to fetch requests: ", requestTask.getException());
                                        }
                                    });
                        } else {
                            Log.e(TAG, "Failed to fetch user document: ", task.getException());
                        }
                    });
        } else {
            Log.e(TAG, "No logged-in user email found.");
        }
    }

    private void handleRequestAction(String requestId, boolean accepted, String loggedInUsername) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Update the request status in Firestore
        db.collection("connectionRequests")
                .document(loggedInUsername)
                .collection("requests")
                .document(requestId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String requesterFCMToken = documentSnapshot.getString("requesterFCMToken");
                        String requesterUsername = documentSnapshot.getString("requesterUsername");

                        // Update the status field
                        db.collection("connectionRequests")
                                .document(loggedInUsername)
                                .collection("requests")
                                .document(requestId)
                                .update("status", accepted ? "accepted" : "declined")
                                .addOnSuccessListener(aVoid -> {
                                    if (!accepted) {
                                        // Delete the request if declined
                                        db.collection("connectionRequests")
                                                .document(loggedInUsername)
                                                .collection("requests")
                                                .document(requestId)
                                                .delete()
                                                .addOnSuccessListener(unused -> Log.d(TAG, "Request declined and deleted."))
                                                .addOnFailureListener(e -> Log.e(TAG, "Failed to delete declined request.", e));
                                    } else {
                                        Log.d(TAG, "Request accepted.");
                                    }

                                    // Send notification to the requester
                                    if (requesterFCMToken != null && !requesterFCMToken.isEmpty()) {
                                        String notificationTitle = "Pemberitahuan soal permintaan anda";
                                        String notificationMessage = "Permintaan Hubungkan Akun anda " +
                                                (accepted ? "Diterima" : "Ditolak") +
                                                " by " + loggedInUsername + ".";
                                        sendFCMNotification(this, requesterFCMToken, notificationTitle, notificationMessage);
                                        Log.d(TAG, "Notification sent to requester: " + requesterUsername);
                                    } else {
                                        Log.w(TAG, "Requester FCM token is null or empty. Cannot send notification.");
                                    }

                                    fetchPendingRequests();
                                })
                                .addOnFailureListener(e -> Log.e(TAG, "Failed to update request status.", e));
                    } else {
                        Log.w(TAG, "Request document not found. Cannot update status or send notification.");
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to fetch request document.", e));
    }
    private void sendFCMNotification(Context context, String token, String title, String body) {
        Log.d(TAG, "sendFCMNotification: Preparing to send notification. Title: " + title + ", Body: " + body);

        String FCM_API_URL = "https://fcm.googleapis.com/v1/projects/smartschiz-6a1d3/messages:send";

        JSONObject payload = new JSONObject();
        try {
            JSONObject message = new JSONObject();

            // Notification object (optional, for displaying a system notification)
            JSONObject notification = new JSONObject();
            notification.put("title", title);
            notification.put("body", body);
            message.put("notification", notification);

            // Data object (for sending data to the app and triggering the intent)
            JSONObject data = new JSONObject();
            data.put("click_action", "OPEN_PENDING_REQUESTS"); // This will be sent as an extra
            message.put("data", data);

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
                            Log.d(TAG, "sendFCMNotification: Notification sent successfully: " + response.body().string());
                        } else {
                            Log.e(TAG, "sendFCMNotification: Failed. Code: " + response.code() + ", Body: " + response.body().string());
                        }
                    }
                });
            } catch (IOException e) {
                Log.e(TAG, "sendFCMNotification: Failed to retrieve access token", e);
            }
        });
    }
}
