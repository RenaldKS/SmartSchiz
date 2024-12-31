package nodomain.freeyourgadget.gadgetbridge.impl;

import java.io.Serializable;

import nodomain.freeyourgadget.gadgetbridge.model.StressSample;

public class StressSampleImpl implements StressSample, Serializable {
    private static final long serialVersionUID = 1L;

    private final Type type;
    private final int stress;

    public StressSampleImpl(Type type, int stress) {
        this.type = type;
        this.stress = stress;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public int getStress() {
        return stress;
    }

    @Override
    public String toString() {
        return "StressSampleImpl{" +
                "type=" + type +
                ", stress=" + stress +
                '}';
    }

    @Override
    public long getTimestamp() {
        return 0;
    }
}
