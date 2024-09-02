/*  Copyright (C) 2024 Martin.JM

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
package nodomain.freeyourgadget.gadgetbridge.devices.huawei.packets;

import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiPacket;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiTLV;

/**
 * File downloading for "older" devices/implementations
 * Newer ones might be using service id 0x2c
 * Which one is used is reported by the band in 0x01 0x31
 */
public class FileDownloadService0A {
    public static final int id = 0x0a;

    /*
    Type of files that can be downloaded through here:
    - debug files
    - sleep files
    - rrisqi file
     */

    public static class FileDownloadInit {
        public static final int id = 0x01;

        public static class DebugFilesRequest extends HuaweiPacket {
            public DebugFilesRequest(ParamsProvider paramsProvider) {
                super(paramsProvider);

                this.serviceId = FileDownloadService0A.id;
                this.commandId = id;

                this.tlv = new HuaweiTLV(); // Empty TLV

                this.complete = true;
            }
        }

        public static class SleepFilesRequest extends HuaweiPacket {
            public SleepFilesRequest(ParamsProvider paramsProvider, int startTime, int endTime) {
                super(paramsProvider);

                this.serviceId = FileDownloadService0A.id;
                this.commandId = id;

                this.tlv = new HuaweiTLV()
                        .put(0x02, (byte) 0x01)
                        .put(0x83, new HuaweiTLV()
                                .put(0x04, startTime)
                                .put(0x05, endTime)
                        );

                this.complete = true;
            }
        }

        public static class Response extends HuaweiPacket {
            public String[] fileNames;

            public Response(ParamsProvider paramsProvider) {
                super(paramsProvider);
            }

            @Override
            public void parseTlv() throws ParseException {
                String possibleNames = this.tlv.getString(0x01);
                fileNames = possibleNames.split(";");
            }
        }
    }

    public static class FileParameters {
        public static final int id = 0x02;

        public static class Request extends HuaweiPacket {
            public Request(ParamsProvider paramsProvider) {
                super(paramsProvider);

                this.serviceId = FileDownloadService0A.id;
                this.commandId = id;

                this.tlv = new HuaweiTLV().put(0x06, (byte) 1);

                this.complete = true;
            }
        }

        public static class Response extends HuaweiPacket {
            public String version;
            public boolean unknown;
            public short packetSize;
            public short maxBlockSize;
            public short timeout;

            public Response(ParamsProvider paramsProvider) {
                super(paramsProvider);
            }

            @Override
            public void parseTlv() throws ParseException {
                // TODO: below could be different for AW70?

                this.version = this.tlv.getString(0x01);
                this.unknown = this.tlv.getBoolean(0x02);
                this.packetSize = this.tlv.getShort(0x03);
                this.maxBlockSize = this.tlv.getShort(0x04);
                this.timeout = this.tlv.getShort(0x05);
            }
        }
    }

    public static class FileInfo {
        public static final int id = 0x03;

        public static class Request extends HuaweiPacket {
            public Request(ParamsProvider paramsProvider, String fileName) {
                super(paramsProvider);

                this.serviceId = FileDownloadService0A.id;
                this.commandId = id;

                this.tlv = new HuaweiTLV().put(0x01, fileName);

                this.complete = true;
            }
        }

        public static class Response extends HuaweiPacket {
            public int fileLength;
            public byte transferType = -1;
            public int fileCreateTime = -1;
            public byte unknown = -1;

            public Response(ParamsProvider paramsProvider) {
                super(paramsProvider);
            }

            @Override
            public void parseTlv() throws ParseException {
                this.fileLength = this.tlv.getInteger(0x02);
                if (this.tlv.contains(0x04))
                    this.transferType = this.tlv.getByte(0x04);
                if (this.tlv.contains(0x05))
                    this.fileCreateTime = this.tlv.getInteger(0x05);
                if (this.tlv.contains(0x06))
                    this.unknown = this.tlv.getByte(0x06);
            }
        }
    }

    public static class RequestBlock {
        public static final int id = 0x04;

        public static class Request extends HuaweiPacket {
            public Request(ParamsProvider paramsProvider, String fileName, int offset, int size) {
                super(paramsProvider);

                this.serviceId = FileDownloadService0A.id;
                this.commandId = id;

                this.tlv = new HuaweiTLV()
                        .put(0x01, fileName)
                        .put(0x02, offset) // TODO: not for AW70
                        .put(0x03, size); // TODO: not for AW70

                this.complete = true;
            }
        }

        public static class Response extends HuaweiPacket {
            public boolean isOk;
            public String filename;
            public int offset;

            public Response(ParamsProvider paramsProvider) {
                super(paramsProvider);
            }

            public void parseTlv() throws ParseException {
                isOk = this.tlv.getInteger(0x7f) == 0x000186A0;
                if (isOk) {
                    if (this.tlv.contains(0x01))
                        filename = this.tlv.getString(0x01);
                    offset = this.tlv.getInteger(0x02);
                }
            }
        }
    }

    public static class BlockResponse extends HuaweiPacket {
        public static final int id = 0x05;

        public byte number;
        public byte[] data;

        public BlockResponse(ParamsProvider paramsProvider) {
            super(paramsProvider);
        }

        @Override
        public void parseTlv() throws ParseException {
            // Note that this packet does not contain TLV data
            this.number = this.payload[2];
            this.data = new byte[this.payload.length - 3];
            System.arraycopy(this.payload, 3, this.data, 0, this.payload.length - 3);
        }
    }

    public static class FileDownloadCompleteRequest extends HuaweiPacket {
        public static final int id = 0x06;

        public FileDownloadCompleteRequest(ParamsProvider paramsProvider) {
            super(paramsProvider);

            this.serviceId = FileDownloadService0A.id;
            this.commandId = id;

            this.tlv = new HuaweiTLV().put(0x06, true);

            this.complete = true;
        }
    }
}
