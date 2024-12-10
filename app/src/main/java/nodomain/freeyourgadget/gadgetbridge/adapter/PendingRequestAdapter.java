package nodomain.freeyourgadget.gadgetbridge.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.R;

public class PendingRequestAdapter extends RecyclerView.Adapter<PendingRequestAdapter.ViewHolder> {

    public interface OnRequestActionListener {
        void onAccept(String documentId);
        void onDecline(String documentId);
    }

    private final List<Map<String, Object>> pendingRequests;
    private final OnRequestActionListener actionListener;

    public PendingRequestAdapter(List<Map<String, Object>> pendingRequests, OnRequestActionListener actionListener) {
        this.pendingRequests = pendingRequests;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pending_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> request = pendingRequests.get(position);

        String requesterUsername = (String) request.get("requesterUsername");
        String requesterEmail = (String) request.get("requesterEmail");
        String status = (String) request.get("status");
        String documentId = (String) request.get("documentId");

        holder.requesterUsername.setText("Requester: " + requesterUsername);
        holder.requesterEmail.setText("Email: " + requesterEmail);
        holder.status.setText("Status: " + status);

        holder.acceptButton.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onAccept(documentId);
            }
        });

        holder.declineButton.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onDecline(documentId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return pendingRequests.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView requesterUsername, requesterEmail, status;
        Button acceptButton, declineButton;

        public ViewHolder(View itemView) {
            super(itemView);
            requesterUsername = itemView.findViewById(R.id.requesterUsername);
            requesterEmail = itemView.findViewById(R.id.requesterEmail);
            status = itemView.findViewById(R.id.status);
            acceptButton = itemView.findViewById(R.id.buttonAccept);
            declineButton = itemView.findViewById(R.id.buttonDecline);
        }
    }
}
