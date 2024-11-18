package nodomain.freeyourgadget.gadgetbridge.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.adapter.ConnectedAccountsAdapter;
import nodomain.freeyourgadget.gadgetbridge.model.ConnectedUser;

public class ConnectedAccountActivity extends AppCompatActivity {

    private RecyclerView connectedAccountsRecyclerView;
    private ConnectedAccountsAdapter adapter;
    private List<ConnectedUser> connectedUsers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connected_accounts);

        connectedAccountsRecyclerView = findViewById(R.id.connectedAccountsRecyclerView);
        connectedAccountsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ConnectedAccountsAdapter(connectedUsers);
        connectedAccountsRecyclerView.setAdapter(adapter);

        fetchConnectedAccounts();
    }

    private void fetchConnectedAccounts() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String currentUserEmail = FirebaseAuth.getInstance().getCurrentUser().getEmail();

        if (currentUserEmail == null) {
            Log.w("ConnectedAccounts", "Current user email is null");
            Toast.makeText(this, "No user email found.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Find the username for the current user based on their email
        db.collection("users")
                .whereEqualTo("email", currentUserEmail)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && !task.getResult().isEmpty()) {
                        String currentUsername = task.getResult().getDocuments().get(0).getId();
                        fetchConnectedAccountsForUser(currentUsername);
                    } else {
                        Log.w("ConnectedAccounts", "No user found for email: " + currentUserEmail);
                        Toast.makeText(this, "User not found for email.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void fetchConnectedAccountsForUser(String username) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(username)
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        List<String> connectedAccountUsernames = (List<String>) task.getResult().get("connectedAccounts");

                        if (connectedAccountUsernames != null && !connectedAccountUsernames.isEmpty()) {
                            for (String connectedUsername : connectedAccountUsernames) {
                                fetchConnectedUserDetails(connectedUsername);
                            }
                        } else {
                            Toast.makeText(this, "No connected accounts found.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.w("ConnectedAccounts", "Error fetching connected accounts", task.getException());
                    }
                });
    }

    private void fetchConnectedUserDetails(String username) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(username)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String email = documentSnapshot.getString("email");
                        ConnectedUser connectedUser = new ConnectedUser(username, email);
                        connectedUsers.add(connectedUser);
                        adapter.notifyDataSetChanged();
                    }
                })
                .addOnFailureListener(e -> Log.w("ConnectedAccounts", "Error fetching user details", e));
    }
}
