package nodomain.freeyourgadget.gadgetbridge.activities;

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

import nodomain.freeyourgadget.gadgetbridge.R;

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

        fetchPendingRequests();
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
        db.collection("connectionRequests")
                .document(loggedInUsername)
                .collection("requests")
                .document(requestId)
                .update("status", accepted ? "accepted" : "declined")
                .addOnSuccessListener(aVoid -> {
                    if (!accepted) {
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
                    fetchPendingRequests();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update request status.", e));
    }
}
