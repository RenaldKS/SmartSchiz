package nodomain.freeyourgadget.gadgetbridge.activities.dashboard;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class DashboardRefreshWorker extends Worker {

    public static final String ACTION_REFRESH_DASHBOARD = "nodomain.freeyourgadget.gadgetbridge.ACTION_REFRESH_DASHBOARD";

    public DashboardRefreshWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        android.util.Log.d("DashboardRefreshWorker", "Worker created");
    }

    @NonNull
    @Override
    public Result doWork() {
        // Log when the work starts
        android.util.Log.d("DashboardRefreshWorker", "Work started.");

        // Send a broadcast to notify that the dashboard needs to be refreshed
        Intent intent = new Intent(ACTION_REFRESH_DASHBOARD);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

        // Log for debugging
        android.util.Log.d("DashboardRefreshWorker", "Broadcast sent to refresh dashboard");

        // Returning success
        Result result = Result.success();
        android.util.Log.d("DashboardRefreshWorker", "Work finished with result: " + result);

        return result;
    }
}
