package nodomain.freeyourgadget.gadgetbridge.activities;

import static android.content.ContentValues.TAG;

import android.content.Intent;
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

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.adapter.ConnectedAccountsAdapter;
import nodomain.freeyourgadget.gadgetbridge.model.ConnectedUser;

public class    ConnectedAccountActivity extends AppCompatActivity {

    private RecyclerView connectedAccountsRecyclerView;
    private ConnectedAccountsAdapter adapter;
    private List<ConnectedUser> connectedUsers = new ArrayList<>();
    private String currentUsername; // Cache the current username for reuse

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connected_accounts);

        connectedAccountsRecyclerView = findViewById(R.id.connectedAccountsRecyclerView);
        connectedAccountsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ConnectedAccountsAdapter(connectedUsers, this::onDeclineConnection);
        connectedAccountsRecyclerView.setAdapter(adapter);

        fetchCurrentUsername();
        Button viewPendingRequestButton = findViewById(R.id.viewPendingRequestButton);

        viewPendingRequestButton.setOnClickListener(v -> {

            Log.d(TAG, "Navigating to PendingRequestActivity");

            Toast.makeText(this, "Membuka Permintaan Koneksi", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(ConnectedAccountActivity.this, PendingRequestActivity.class);

            startActivity(intent);

        });

        Button showDeclinedButton = findViewById(R.id.show_declined_button);

        showDeclinedButton.setOnClickListener(v -> {

            Log.d(TAG, "Navigating to DeclinedAccountsActivity");

            Toast.makeText(this, "Membuka daftar akun ditolak", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(ConnectedAccountActivity.this, DeclinedAccountsActivity.class);

            startActivity(intent);

        });
    }

    private void fetchCurrentUsername() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        String currentUserEmail = auth.getCurrentUser() != null ? auth.getCurrentUser().getEmail() : null;

        if (currentUserEmail == null) {
            Log.w("ConnectedAccounts", "Current user email is null");
            Toast.makeText(this, "Email Pengguna tidak ditemukan!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Query the "users" collection to find the username by email
        db.collection("users")
                .whereEqualTo("email", currentUserEmail)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        currentUsername = task.getResult().getDocuments().get(0).getId(); // Cache the username
                        fetchConnectedAccountsForUser(currentUsername);
                    } else {
                        Log.w("ConnectedAccounts", "No user found for email: " + currentUserEmail);
                        Toast.makeText(this, "Tidak ada akun yang terhubung dengan email tersebut", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ConnectedAccounts", "Error fetching username", e);
                    Toast.makeText(this, "Gagal mendapatkan username.", Toast.LENGTH_SHORT).show();
                });
    }

    private void fetchConnectedAccountsForUser(String username) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Query the connectionRequests subcollection for the logged-in user's username
        db.collection("connectionRequests").document(username)
                .collection("requests")
                .whereEqualTo("status", "accepted") // Filter only accepted connections
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        connectedUsers.clear(); // Clear the current list before adding new data

                        for (QueryDocumentSnapshot document : task.getResult()) {
                            // Extract requester information
                            String documentId = document.getId();
                            String requesterUsername = document.getString("requesterUsername");
                            String requesterEmail = document.getString("requesterEmail");

                            if (documentId != null && requesterUsername != null && requesterEmail != null) {
                                // Create a ConnectedUser object and add it to the list
                                ConnectedUser connectedUser = new ConnectedUser(documentId, requesterUsername, requesterEmail);
                                connectedUsers.add(connectedUser);
                            }
                        }
                        adapter.notifyDataSetChanged(); // Notify adapter about data changes
                    } else {
                        Log.w("ConnectedAccounts", "No accepted connections found or error occurred", task.getException());
                    }
                })
                .addOnFailureListener(e -> Log.e("ConnectedAccounts", "Error fetching connected accounts", e));
    }

    private void onDeclineConnection(String documentId) {
        if (currentUsername == null) {
            Log.w("ConnectedAccounts", "Current username is not available.");
            Toast.makeText(this, "Username gagal ditemukan", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Access the specific document using the document ID under the current username
        db.collection("connectionRequests")
                .document(currentUsername)
                .collection("requests")
                .document(documentId)
                .update("status", "declined")
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Permintaan berhasil dihapus", Toast.LENGTH_SHORT).show();
                    fetchConnectedAccountsForUser(currentUsername); // Refresh the list after updating
                })
                .addOnFailureListener(e -> {
                    Log.w("ConnectedAccounts", "Error declining connection", e);
                    Toast.makeText(this, "Gagal menghapus permintaan", Toast.LENGTH_SHORT).show();
                });
    }
}
