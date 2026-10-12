package ru.example.tmi.source;

import java.util.Objects;

import ru.example.tmi.Ipv4Address;

/**
 * Пакет исходного архива ТМИ. Неизменяемый: массив данных копируется
 * при создании и при выдаче.
 *
 * <p>Беззнаковые поля исходного формата хранятся в более широких типах:
 * порты в {@code int} (0..65535), счетчик в {@code long} (0..2<sup>32</sup>-1).
 * Флаги хранятся как есть, все 32 бита.
 */
public final class SourcePacket {

    private final Ipv4Address sourceAddress;
    private final Ipv4Address destinationAddress;
    private final int sourcePort;
    private final int destinationPort;
    private final long counter;
    private final long timestampTicks;
    private final int flags;
    private final byte[] data;
    private final int checksum;

    private SourcePacket(Builder builder) {
        this.sourceAddress = Objects.requireNonNull(builder.sourceAddress, "sourceAddress");
        this.destinationAddress = Objects.requireNonNull(builder.destinationAddress, "destinationAddress");
        this.sourcePort = checkPort(builder.sourcePort, "sourcePort");
        this.destinationPort = checkPort(builder.destinationPort, "destinationPort");
        if (builder.counter < 0 || builder.counter > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("counter вне диапазона UInt32: " + builder.counter);
        }
        this.counter = builder.counter;
        this.timestampTicks = builder.timestampTicks;
        this.flags = builder.flags;
        this.data = Objects.requireNonNull(builder.data, "data").clone();
        this.checksum = builder.checksum;
    }

    private static int checkPort(int port, String name) {
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException(name + " вне диапазона 0..65535: " + port);
        }
        return port;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Ipv4Address getSourceAddress() {
        return sourceAddress;
    }

    public Ipv4Address getDestinationAddress() {
        return destinationAddress;
    }

    public int getSourcePort() {
        return sourcePort;
    }

    public int getDestinationPort() {
        return destinationPort;
    }

    public long getCounter() {
        return counter;
    }

    /**
     * Время приема как .NET {@code DateTime.Ticks} в ДМВ.
     */
    public long getTimestampTicks() {
        return timestampTicks;
    }

    public int getFlags() {
        return flags;
    }

    /**
     * Признак того, что Data сжата zlib (бит 0 поля Flags).
     */
    public boolean isCompressed() {
        return (flags & SourcePacketFormat.FLAG_ZLIB) != 0;
    }

    /**
     * Длина поля Data в том виде, в котором оно хранится в файле.
     */
    public int getDataLength() {
        return data.length;
    }

    /**
     * Копия поля Data в том виде, в котором оно хранится в файле
     * (сжатое, если установлен флаг zlib).
     */
    public byte[] getData() {
        return data.clone();
    }

    public int getChecksum() {
        return checksum;
    }

    @Override
    public String toString() {
        return "SourcePacket{" + sourceAddress + ":" + sourcePort
                + " -> " + destinationAddress + ":" + destinationPort
                + ", counter=" + counter
                + ", ticks=" + timestampTicks
                + ", flags=0x" + Integer.toHexString(flags)
                + ", dataLength=" + data.length + "}";
    }

    /**
     * Построитель {@link SourcePacket}.
     */
    public static final class Builder {

        private Ipv4Address sourceAddress;
        private Ipv4Address destinationAddress;
        private int sourcePort;
        private int destinationPort;
        private long counter;
        private long timestampTicks;
        private int flags;
        private byte[] data = new byte[0];
        private int checksum;

        private Builder() {
        }

        public Builder sourceAddress(Ipv4Address sourceAddress) {
            this.sourceAddress = sourceAddress;
            return this;
        }

        public Builder destinationAddress(Ipv4Address destinationAddress) {
            this.destinationAddress = destinationAddress;
            return this;
        }

        public Builder sourcePort(int sourcePort) {
            this.sourcePort = sourcePort;
            return this;
        }

        public Builder destinationPort(int destinationPort) {
            this.destinationPort = destinationPort;
            return this;
        }

        public Builder counter(long counter) {
            this.counter = counter;
            return this;
        }

        public Builder timestampTicks(long timestampTicks) {
            this.timestampTicks = timestampTicks;
            return this;
        }

        public Builder flags(int flags) {
            this.flags = flags;
            return this;
        }

        /**
         * Данные пакета; массив копируется при вызове {@link #build()}.
         */
        public Builder data(byte[] data) {
            this.data = data;
            return this;
        }

        public Builder checksum(int checksum) {
            this.checksum = checksum;
            return this;
        }

        public SourcePacket build() {
            return new SourcePacket(this);
        }
    }
}
