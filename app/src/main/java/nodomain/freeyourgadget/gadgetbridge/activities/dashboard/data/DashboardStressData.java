package nodomain.freeyourgadget.gadgetbridge.activities.dashboard.data;

import android.util.Log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.DashboardFragment;
import nodomain.freeyourgadget.gadgetbridge.activities.charts.StressChartFragment;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.StressSample;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractTimeSampleProvider;
import android.content.Context;
import android.content.Intent;

public class DashboardStressData implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardStressData.class);
    public static final String ACTION_STRESS_DATA_UPDATED = "nodomain.freeyourgadget.gadgetbridge.STRESS_DATA_UPDATED";
    public static final String EXTRA_STRESS_VALUE = "extra_stress_value";

    public int value; // Average stress value
    public int latestStressValue; // New field for the latest stress value
    public int[] ranges;
    public int[] totalTime;

    // Method to broadcast stress value


    public static DashboardStressData compute(final DashboardFragment.DashboardData dashboardData, Context context) {
        final List<GBDevice> devices = GBApplication.app().getDeviceManager().getDevices();

        GBDevice stressDevice = null;
        double averageStress = -1;
        StressSample latestSample = null;

        final int[] totalTime = new int[StressChartFragment.StressType.values().length];

        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            for (GBDevice dev : devices) {
                Log.d("DashboardStressData", "Checking device: " + dev.getName());
                if ((dashboardData.showAllDevices || (dashboardData.showDeviceList != null && dashboardData.showDeviceList.contains(dev.getAddress()))) && dev.getDeviceCoordinator().supportsStressMeasurement()) {
                    Log.d("DashboardStressData", "Device supports stress measurement: " + dev.getName());
                    final List<? extends StressSample> samples = dev.getDeviceCoordinator()
                            .getStressSampleProvider(dev, dbHandler.getDaoSession())
                            .getAllSamples(dashboardData.timeFrom * 1000L, dashboardData.timeTo * 1000L);

                    Log.d("DashboardStressData", "Fetched " + samples.size() + " stress samples.");

                    for (StressSample sample : samples) {
                        Log.d("DashboardStressData", "Stress sample value: " + sample.getStress());
                    }

                    if (!samples.isEmpty()) {
                        stressDevice = dev;
                        final int[] stressRanges = dev.getDeviceCoordinator().getStressRanges();
                        averageStress = samples.stream()
                                .mapToInt(StressSample::getStress)
                                .peek(stress -> {
                                    final StressChartFragment.StressType stressType = StressChartFragment.StressType.fromStress(stress, stressRanges);
                                    if (stressType != StressChartFragment.StressType.UNKNOWN) {
                                        totalTime[stressType.ordinal() - 1] += 60;
                                    }
                                })
                                .average()
                                .orElse(0);

                        Log.d("DashboardStressData", "Average stress value: " + averageStress);
                    }

                    latestSample = dev.getDeviceCoordinator()
                            .getStressSampleProvider(dev, dbHandler.getDaoSession())
                            .getLatestSample();

                    if (latestSample != null) {
                        Log.d("DashboardStressData", "Latest stress value: " + latestSample.getStress());
                    }
                } else {
                    Log.d("DashboardStressData", "Device does not support stress measurement: " + dev.getName());
                }
            }
        } catch (final Exception e) {
            LOG.error("Could not compute stress", e);
        }

        if (stressDevice != null) {
            final DashboardStressData stressData = new DashboardStressData();
            stressData.value = (int) Math.round(averageStress);
            stressData.ranges = stressDevice.getDeviceCoordinator().getStressRanges();
            stressData.totalTime = totalTime;

            if (latestSample != null) {
                stressData.latestStressValue = latestSample.getStress();
                Log.d("DashboardStressData", "About to broadcast stress value: " + stressData.latestStressValue);

            }

            Log.d("DashboardStressData", "Computed stress data: Average: " + stressData.value + ", Latest: " + stressData.latestStressValue);
            return stressData;
        } else {
            Log.d("DashboardStressData", "No stress data available.");
        }

        return null;
    }
}
