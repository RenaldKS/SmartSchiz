/*  Copyright (C) 2023-2024 Frank Ertl

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.withingssteelhr.communication;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;

import nodomain.freeyourgadget.gadgetbridge.service.btle.BtLEServerAction;

public class WithingsServerAction extends BtLEServerAction
{
    private BluetoothGattCharacteristic characteristic;

    public WithingsServerAction(BluetoothDevice device, BluetoothGattCharacteristic characteristic) {
        super(device);
        this.characteristic = characteristic;
    }

    @Override
    public boolean expectsResult() {
        return false;
    }

    @Override
    public boolean run(BluetoothGattServer server) {
        return server.notifyCharacteristicChanged(getDevice(), characteristic, false);
    }
}
