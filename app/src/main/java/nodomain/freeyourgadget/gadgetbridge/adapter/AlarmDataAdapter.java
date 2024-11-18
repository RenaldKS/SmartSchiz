package nodomain.freeyourgadget.gadgetbridge.adapter;


import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.R;

public class AlarmDataAdapter extends RecyclerView.Adapter<AlarmDataAdapter.AlarmDataViewHolder> {

    private List<Map<String, Object>> alarmDataList;

    public AlarmDataAdapter(List<Map<String, Object>> alarmDataList) {
        this.alarmDataList = alarmDataList;
    }

    @NonNull
    @Override
    public AlarmDataViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alarm_data, parent, false);
        return new AlarmDataViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlarmDataViewHolder holder, int position) {
        Map<String, Object> alarmData = alarmDataList.get(position);

        holder.timestampTextView.setText((String) alarmData.get("timestamp"));
        holder.heartRateTextView.setText("Heart Rate: " + alarmData.get("heartRate"));
        holder.stressLevelTextView.setText("Stress Level: " + alarmData.get("stressLevel"));

        String locationLink = (String) alarmData.get("locationLink");
        if (locationLink != null) {
            holder.locationTextView.setText("Location: View on Map");
            holder.locationTextView.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(locationLink));
                v.getContext().startActivity(intent);
            });
        } else {
            holder.locationTextView.setText("Location: Not available");
            holder.locationTextView.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() {
        return alarmDataList.size();
    }

    static class AlarmDataViewHolder extends RecyclerView.ViewHolder {
        TextView timestampTextView;
        TextView heartRateTextView;
        TextView stressLevelTextView;
        TextView locationTextView;

        public AlarmDataViewHolder(@NonNull View itemView) {
            super(itemView);
            timestampTextView = itemView.findViewById(R.id.timestampTextView);
            heartRateTextView = itemView.findViewById(R.id.heartRateTextView);
            stressLevelTextView = itemView.findViewById(R.id.stressLevelTextView);
            locationTextView = itemView.findViewById(R.id.locationTextView);
        }
    }
}

