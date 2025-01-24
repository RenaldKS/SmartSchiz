package nodomain.freeyourgadget.gadgetbridge.util;

import android.content.Context;
import android.util.Log;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.StressSample;

import java.util.List;

public class StressDataUtils {

    private static final String TAG = "StressDataUtils";

    /**
     * Fetches the latest stress sample from the connected devices.
     *
     * @param devices List of connected devices.
     * @param context The application context.
     * @return The latest StressSample, or null if no valid sample is available.
     */
    public static StressSample getLatestStressSample(List<GBDevice> devices, Context context) {
        StressSample latestSample = null;

        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            for (GBDevice device : devices) {
                if (device.getDeviceCoordinator().supportsStressMeasurement()) {
                    Log.d(TAG, "Checking device for stress measurement: " + device.getName());

                    // Fetch the latest stress sample
                    StressSample sample = device.getDeviceCoordinator()
                            .getStressSampleProvider(device, dbHandler.getDaoSession())
                            .getLatestSample();

                    if (sample != null) {
                        Log.d(TAG, "Latest stress value from device " + device.getName() + ": " + sample.getStress());
                        latestSample = sample;
                        break; // Use the first available device's sample
                    } else {
                        Log.d(TAG, "No stress sample available from device " + device.getName());
                    }
                } else {
                    Log.d(TAG, "Device does not support stress measurement: " + device.getName());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error fetching latest stress sample", e);
        }

        if (latestSample != null) {
            Log.d(TAG, "Returning latest stress sample with value: " + latestSample.getStress());
        } else {
            Log.d(TAG, "No valid stress sample found across devices");
        }

        return latestSample;
    }
}
