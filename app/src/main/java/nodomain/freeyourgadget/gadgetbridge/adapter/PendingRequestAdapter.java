package nodomain.freeyourgadget.gadgetbridge.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class PendingRequestAdapter extends RecyclerView.Adapter<PendingRequestAdapter.ViewHolder> {

    private List<String> pendingRequests;

    public PendingRequestAdapter(List<String> pendingRequests) {
        this.pendingRequests = pendingRequests;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String request = pendingRequests.get(position);
        holder.requestTextView.setText(request);
    }

    @Override
    public int getItemCount() {
        return pendingRequests.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView requestTextView;

        public ViewHolder(View itemView) {
            super(itemView);
            requestTextView = itemView.findViewById(android.R.id.text1);
        }
    }
}
