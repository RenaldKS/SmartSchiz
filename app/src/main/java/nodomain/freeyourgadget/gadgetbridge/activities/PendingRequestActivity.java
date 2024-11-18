package nodomain.freeyourgadget.gadgetbridge.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.adapter.PendingRequestAdapter;

public class PendingRequestActivity extends AppCompatActivity {

    private static final String TAG = "PendingRequestActivity";
    private RecyclerView recyclerView;
    private PendingRequestAdapter adapter;
    private ArrayList<String> pendingRequests = new ArrayList<>();
    private FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pending_request);

        recyclerView = findViewById(R.id.recyclerViewPendingRequests);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PendingRequestAdapter(pendingRequests);
        recyclerView.setAdapter(adapter);

        // Button to accept a request
        Button acceptButton = findViewById(R.id.buttonAccept);
        acceptButton.setOnClickListener(view -> {
            if (!pendingRequests.isEmpty()) {
                String requestId = pendingRequests.get(0); // Assumes the first request in the list
                updateRequestStatus(requestId, "accepted");
            } else {
                Toast.makeText(this, "No requests to accept", Toast.LENGTH_SHORT).show();
            }
        });

        fetchPendingRequests();
    }

    private void fetchPendingRequests() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String currentUserEmail = user.getEmail();
            if (currentUserEmail == null) {
                Log.w(TAG, "Current user's email is null");
                return;
            }

            Log.d(TAG, "Fetching pending requests for user email: " + currentUserEmail);

            // Find the username corresponding to the current user's email
            db.collection("users")
                    .whereEqualTo("email", currentUserEmail)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && !task.getResult().isEmpty()) {
                            String currentUsername = task.getResult().getDocuments().get(0).getId();
                            fetchRequestsForUsername(currentUsername);
                        } else {
                            Log.w(TAG, "No user found for email: " + currentUserEmail);
                            Toast.makeText(this, "No user found for the current account", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void fetchRequestsForUsername(String username) {
        db.collection("connectionRequests")
                .whereEqualTo("targetUsername", username)  // Use the target's username
                .whereEqualTo("status", "pending") // Filter by pending status
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        pendingRequests.clear(); // Clear old data

                        if (!querySnapshot.isEmpty()) {
                            for (QueryDocumentSnapshot document : querySnapshot) {
                                String requesterUsername = document.getString("requesterUsername");
                                String requesterEmail = document.getString("requesterEmail");
                                pendingRequests.add("Requester Username: " + requesterUsername + "\nRequester Email: " + requesterEmail);
                                Log.d(TAG, "Pending request from: " + requesterUsername + ", Email: " + requesterEmail);
                            }
                            adapter.notifyDataSetChanged();
                        } else {
                            Toast.makeText(this, "No pending requests", Toast.LENGTH_SHORT).show();
                            Log.d(TAG, "No pending requests found");
                        }
                    } else {
                        Log.w(TAG, "Error fetching requests", task.getException());
                    }
                });
    }

    private void updateRequestStatus(String requestId, String status) {
        Log.d(TAG, "Updating request " + requestId + " status to " + status);
        db.collection("connectionRequests")
                .document(requestId) // Assuming requestId corresponds to the document ID
                .update("status", status)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(PendingRequestActivity.this, "Request " + status, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Request " + requestId + " updated to " + status);

                    // After accepting the request, create the connection
                    if (status.equals("accepted")) {
                        createConnection(requestId);
                    }

                    fetchPendingRequests(); // Refresh the list after updating the request status
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update request", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "Error updating request status", e);
                });
    }

    private void createConnection(String requestId) {
        db.collection("connectionRequests")
                .document(requestId) // Assuming requestId corresponds to the document ID
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String requesterUsername = documentSnapshot.getString("requesterUsername");
                        String targetUserUsername = documentSnapshot.getString("targetUsername");

                        // Create the connection in the target user's document
                        sendConnectionRequest(requesterUsername, targetUserUsername);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error fetching request document", e);
                });
    }

    private void sendConnectionRequest(String requesterUsername, String targetUserUsername) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> request = new HashMap<>();
        request.put("requesterUsername", requesterUsername);
        request.put("requesterEmail", "requester_email@example.com");  // Assuming the email is available
        request.put("targetUsername", targetUserUsername);
        request.put("status", "pending");
        request.put("timestamp", FieldValue.serverTimestamp());

        db.collection("connectionRequests")
                .document(requesterUsername)  // Use requester username as document ID
                .collection("request")
                .add(request)
                .addOnSuccessListener(documentReference -> {
                    Log.d(TAG, "Connection request sent successfully");
                    Toast.makeText(this, "Request sent successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to send connection request", e);
                    Toast.makeText(this, "Failed to send request", Toast.LENGTH_SHORT).show();
                });
    }
}
