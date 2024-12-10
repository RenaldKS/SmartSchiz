package nodomain.freeyourgadget.gadgetbridge.adapter;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.model.ConnectedUser;

public class ConnectedAccountsAdapter extends RecyclerView.Adapter<ConnectedAccountsAdapter.ViewHolder> {
    private List<ConnectedUser> connectedUsers;
    private OnConnectionDeleteListener deleteListener;

    public interface OnConnectionDeleteListener {
        void onDeleteConnection(String userId);
    }

    public ConnectedAccountsAdapter(List<ConnectedUser> connectedUsers, OnConnectionDeleteListener deleteListener) {
        this.connectedUsers = connectedUsers;
        this.deleteListener = deleteListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.connected_account_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConnectedUser user = connectedUsers.get(position);
        holder.userIdView.setText(user.getUsername());
        holder.emailView.setText("Email: " + user.getEmail());

        holder.deleteButton.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDeleteConnection(user.getDocumentId());
            }
        });
    }

    @Override
    public int getItemCount() {
        return connectedUsers.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView userIdView;
        TextView emailView;
        Button deleteButton;

        public ViewHolder(View itemView) {
            super(itemView);
            userIdView = itemView.findViewById(R.id.username_view);
            emailView = itemView.findViewById(R.id.email_view);
            deleteButton = itemView.findViewById(R.id.delete_button);
        }
    }
}
