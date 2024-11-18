package nodomain.freeyourgadget.gadgetbridge.adapter;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.model.ConnectedUser;

public class ConnectedAccountsAdapter extends RecyclerView.Adapter<ConnectedAccountsAdapter.ViewHolder> {
    private List<ConnectedUser> connectedUsers;

    public ConnectedAccountsAdapter(List<ConnectedUser> connectedUsers) {
        this.connectedUsers = connectedUsers;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConnectedUser user = connectedUsers.get(position);
        holder.userIdView.setText("User ID: " + user.getUserId());
        holder.emailView.setText("Email: " + user.getEmail());
    }

    @Override
    public int getItemCount() {
        return connectedUsers.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView userIdView;
        TextView emailView;

        public ViewHolder(View itemView) {
            super(itemView);
            userIdView = itemView.findViewById(android.R.id.text1);
            emailView = itemView.findViewById(android.R.id.text2);
        }
    }
}

