package nodomain.freeyourgadget.gadgetbridge.service;

import nodomain.freeyourgadget.gadgetbridge.model.StressSample;

public interface StressDataFetcher {
    StressSample getLatestStressSample();
}
