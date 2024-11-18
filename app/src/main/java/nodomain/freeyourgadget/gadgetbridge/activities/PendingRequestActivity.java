package nodomain.freeyourgadget.gadgetbridge.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;

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

        fetchPendingRequests();

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
    }

    private void fetchPendingRequests() {
        String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();
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

    private void fetchRequestsForUsername(String username) {
        db.collection("connectionRequests")
                .whereEqualTo("targetUser", username)  // Matches the current authenticated user's username
                .whereEqualTo("status", "pending")    // Filter by pending status
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        QuerySnapshot querySnapshot = task.getResult();
                        pendingRequests.clear(); // Clear old data

                        if (!querySnapshot.isEmpty()) {
                            for (QueryDocumentSnapshot document : querySnapshot) {
                                String requesterUsername = document.getString("requester");
                                String requestId = document.getId(); // Store document ID
                                pendingRequests.add("Request from: " + requesterUsername + " (ID: " + requestId + ")");
                                Log.d(TAG, "Pending request from: " + requesterUsername + ", ID: " + requestId);
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
        db.collection("connectionRequests").document(requestId)
                .update("status", status)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(PendingRequestActivity.this, "Request " + status, Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Request " + requestId + " updated to " + status);
                    fetchPendingRequests(); // Refresh the list after updating status
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update request", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "Error updating request status", e);
                });
    }
}
