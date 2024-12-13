package nodomain.freeyourgadget.gadgetbridge.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.adapter.ConnectedAccountsAdapter;
import nodomain.freeyourgadget.gadgetbridge.model.ConnectedUser;

public class DeclinedAccountsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ConnectedAccountsAdapter adapter;
    private List<ConnectedUser> declinedUsers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_declined_accounts);

        recyclerView = findViewById(R.id.recycler_view_declined);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ConnectedAccountsAdapter(declinedUsers, this::deleteDeclinedConnection);
        recyclerView.setAdapter(adapter);

        fetchDeclinedAccounts();

    }

    private void fetchDeclinedAccounts() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        String currentUserEmail = auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : null;

        if (currentUserEmail == null) {
            Log.w("DeclinedAccounts", "Current user email is null");
            Toast.makeText(this, "No user email found.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Find the username of the logged-in user
        db.collection("users")
                .whereEqualTo("email", currentUserEmail)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        String currentUsername = task.getResult().getDocuments().get(0).getId();
                        fetchDeclinedAccountsForUser(currentUsername);
                    } else {
                        Log.w("DeclinedAccounts", "No user found for email: " + currentUserEmail);
                        Toast.makeText(this, "User not found for email.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchDeclinedAccountsForUser(String username) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Query the connectionRequests subcollection for the logged-in user's username
        db.collection("connectionRequests").document(username)
                .collection("requests")
                .whereEqualTo("status", "declined") // Filter only declined connections
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        declinedUsers.clear(); // Clear the current list before adding new data

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // Extract requester information
                            String documentId = document.getId();
                            String requesterUsername = document.getString("requesterUsername");
                            String requesterEmail = document.getString("requesterEmail");

                            if (documentId != null && requesterUsername != null && requesterEmail != null) {
                                // Create a ConnectedUser object and add it to the list
                                ConnectedUser connectedUser = new ConnectedUser(documentId, requesterUsername, requesterEmail);
                                declinedUsers.add(connectedUser);
                            }
                        }
                        adapter.notifyDataSetChanged(); // Notify adapter about data changes
                    } else {
                        Log.w("DeclinedAccounts", "No declined connections found or error occurred", task.getException());
                    }
                })
                .addOnFailureListener(e -> Log.e("DeclinedAccounts", "Error fetching declined accounts", e));
    }

    private void deleteDeclinedConnection(String documentId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        String currentUserEmail = auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : null;

        if (currentUserEmail == null) {
            Log.w("DeclinedAccounts", "Current user email is null");
            return;
        }

        db.collection("users")
                .whereEqualTo("email", currentUserEmail)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        String currentUsername = task.getResult().getDocuments().get(0).getId();

                        db.collection("connectionRequests")
                                .document(currentUsername)
                                .collection("requests")
                                .document(documentId)
                                .delete()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(this, "Declined connection deleted successfully", Toast.LENGTH_SHORT).show();
                                    fetchDeclinedAccounts(); // Refresh the list
                                })
                                .addOnFailureListener(e -> {
                                    Log.w("DeclinedAccounts", "Error deleting declined connection", e);
                                    Toast.makeText(this, "Error deleting declined connection", Toast.LENGTH_SHORT).show();
                                });
                    } else {
                        Log.w("DeclinedAccounts", "Error fetching current user's username");
                    }
                });
    }
}
