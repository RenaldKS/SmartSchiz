/*  Copyright (C) 2019-2024 Andreas Shimokawa, Arjan Schrijver, Damien Gaignon,
    Jean-François Greffier

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.miscale;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.IntentListener;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfoProfile;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class MiCompositionScaleDeviceSupport extends AbstractBTLEDeviceSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MiCompositionScaleDeviceSupport.class);
    private static final String UNIT_KG = "kg";
    private static final String UNIT_LBS = "lb";
    private static final String UNIT_JIN = "jīn";
    private final DeviceInfoProfile<MiCompositionScaleDeviceSupport> deviceInfoProfile;
    private final GBDeviceEventVersionInfo versionCmd = new GBDeviceEventVersionInfo();
    private final IntentListener mListener = new IntentListener() {
        @Override
        public void notify(Intent intent) {
            String s = intent.getAction();
            if (s.equals(DeviceInfoProfile.ACTION_DEVICE_INFO)) {
                handleDeviceInfo((nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo) intent.getParcelableExtra(DeviceInfoProfile.EXTRA_DEVICE_INFO));
            }
        }
    };

    public MiCompositionScaleDeviceSupport() {
        super(LOG);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ACCESS);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ATTRIBUTE);
        addSupportedService(GattService.UUID_SERVICE_DEVICE_INFORMATION);
        addSupportedService(GattService.UUID_SERVICE_BODY_COMPOSITION);
        addSupportedService(UUID.fromString("00001530-0000-3512-2118-0009af100700"));

        deviceInfoProfile = new DeviceInfoProfile<>(this);
        deviceInfoProfile.addListener(mListener);
        addSupportedProfile(deviceInfoProfile);
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZING, getContext()));

        LOG.debug("Requesting Device Info!");
        deviceInfoProfile.requestDeviceInfo(builder);
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZED, getContext()));

        // Weight and body composition
        builder.setCallback(this);
        builder.notify(getCharacteristic(GattCharacteristic.UUID_CHARACTERISTIC_BODY_COMPOSITION_MEASUREMENT), true);

        return builder;
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);

        UUID characteristicUUID = characteristic.getUuid();
        if (characteristicUUID.equals(GattCharacteristic.UUID_CHARACTERISTIC_BODY_COMPOSITION_MEASUREMENT)) {
            final byte[] data = characteristic.getValue();

            boolean stabilized = testBit(data[1], 5) && !testBit(data[1], 7);
            boolean isLbs = testBit(data[1], 0);
            boolean isJin = testBit(data[1], 4);
            boolean isKg = !(isLbs && isJin);
            String unit = "";
            if (isKg) {
                unit = UNIT_KG;
            } else if (isLbs) {
                unit = UNIT_LBS;
            } else if (isJin) {
                unit = UNIT_JIN;
            }

            if (stabilized) {
                int year = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 2);
                int month = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 4);
                int day = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 5);
                int hour = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 6);
                int minute = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 7);
                int second = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 8);
                Calendar c = GregorianCalendar.getInstance();
                c.set(year, month - 1, day, hour, minute, second);
                Date date = c.getTime();
                float weight = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 11) / (isKg ? 200.0f : 100.0f);
                handleWeightInfo(date, weight, unit);
            }

            return true;
        }

        return false;
    }

    private boolean testBit(byte value, int offset) {
        return ((value >> offset) & 1) == 1;
    }

    private void handleDeviceInfo(nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo info) {
        LOG.warn("Device info: " + info);
        versionCmd.hwVersion = info.getHardwareRevision();
        versionCmd.fwVersion = info.getSoftwareRevision();
        handleGBDeviceEvent(versionCmd);
    }

    private void handleWeightInfo(Date date, float weight, String unit) {
        // TODO
        LOG.warn("Weight info: " + weight + unit);
        GB.toast(weight + unit, Toast.LENGTH_SHORT, GB.INFO);
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }

    @Override
    public boolean getImplicitCallbackModify() {
        return true;
    }

    @Override
    public boolean getSendWriteRequestResponse() {
        return false;
    }
}
