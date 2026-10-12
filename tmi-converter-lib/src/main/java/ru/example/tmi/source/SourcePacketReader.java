package ru.example.tmi.source;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import ru.example.tmi.DataTooLongException;
import ru.example.tmi.InvalidMarkerException;
import ru.example.tmi.Ipv4Address;
import ru.example.tmi.TruncatedPacketException;

/**
 * Последовательное чтение пакетов исходного архива из потока.
 *
 * <p>Пакеты читаются по одному, в памяти держится только текущий пакет.
 * Поле Checksum читается, но не проверяется. Поток рекомендуется передавать
 * буферизованным.
 */
public final class SourcePacketReader implements Closeable {

    /**
     * Ограничение на DataLength по умолчанию. Защищает от попытки выделить
     * гигантский массив при поврежденном поле длины и с запасом покрывает
     * максимальный размер UDP-пакета.
     */
    public static final int DEFAULT_MAX_DATA_LENGTH = 1024 * 1024;

    private final InputStream in;
    private final int maxDataLength;
    private final byte[] header = new byte[SourcePacketFormat.HEADER_SIZE];
    private final byte[] checksum = new byte[SourcePacketFormat.CHECKSUM_SIZE];

    private long position;
    private long lastPacketOffset = -1;

    public SourcePacketReader(InputStream in) {
        this(in, DEFAULT_MAX_DATA_LENGTH);
    }

    /**
     * @param in            входной поток
     * @param maxDataLength максимально допустимое значение DataLength
     */
    public SourcePacketReader(InputStream in, int maxDataLength) {
        this.in = Objects.requireNonNull(in, "in");
        if (maxDataLength < 0) {
            throw new IllegalArgumentException("maxDataLength < 0: " + maxDataLength);
        }
        this.maxDataLength = maxDataLength;
    }

    /**
     * Читает очередной пакет.
     *
     * @return пакет или {@code null}, если поток закончился ровно на границе пакетов
     * @throws InvalidMarkerException   если маркер пакета неверен
     * @throws TruncatedPacketException если поток закончился посреди пакета
     * @throws DataTooLongException     если DataLength больше допустимого
     * @throws IOException              при ошибке чтения
     */
    public SourcePacket read() throws IOException {
        long packetOffset = position;

        int headerRead = readFully(header);
        if (headerRead == 0) {
            return null;
        }
        lastPacketOffset = packetOffset;
        if (headerRead < header.length) {
            throw new TruncatedPacketException(packetOffset, "заголовок", header.length, headerRead);
        }

        ByteBuffer buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        int marker = buffer.getInt(SourcePacketFormat.MARKER_OFFSET);
        if (marker != SourcePacketFormat.MARKER) {
            throw new InvalidMarkerException(packetOffset, SourcePacketFormat.MARKER, marker);
        }

        long dataLength = Integer.toUnsignedLong(buffer.getInt(SourcePacketFormat.DATA_LENGTH_OFFSET));
        if (dataLength > maxDataLength) {
            throw new DataTooLongException(packetOffset, "DataLength = " + dataLength
                    + " превышает максимально допустимое значение " + maxDataLength);
        }
        byte[] data = new byte[(int) dataLength];
        int dataRead = readFully(data);
        if (dataRead < data.length) {
            throw new TruncatedPacketException(packetOffset, "Data", data.length, dataRead);
        }

        int checksumRead = readFully(checksum);
        if (checksumRead < checksum.length) {
            throw new TruncatedPacketException(packetOffset, "Checksum", checksum.length, checksumRead);
        }

        return SourcePacket.builder()
                .sourceAddress(Ipv4Address.fromBytes(header, SourcePacketFormat.SRC_ADDRESS_OFFSET))
                .destinationAddress(Ipv4Address.fromBytes(header, SourcePacketFormat.DST_ADDRESS_OFFSET))
                .sourcePort(Short.toUnsignedInt(buffer.getShort(SourcePacketFormat.SRC_PORT_OFFSET)))
                .destinationPort(Short.toUnsignedInt(buffer.getShort(SourcePacketFormat.DST_PORT_OFFSET)))
                .counter(Integer.toUnsignedLong(buffer.getInt(SourcePacketFormat.COUNTER_OFFSET)))
                .timestampTicks(buffer.getLong(SourcePacketFormat.TIMESTAMP_OFFSET))
                .flags(buffer.getInt(SourcePacketFormat.FLAGS_OFFSET))
                .data(data)
                .checksum(ByteBuffer.wrap(checksum).order(ByteOrder.LITTLE_ENDIAN).getInt())
                .build();
    }

    /**
     * Смещение в потоке начала последнего прочитанного (или начатого) пакета,
     * либо -1, если пакетов еще не было.
     */
    public long getLastPacketOffset() {
        return lastPacketOffset;
    }

    /**
     * Количество байтов, прочитанных из потока.
     */
    public long getPosition() {
        return position;
    }

    private int readFully(byte[] target) throws IOException {
        int total = 0;
        while (total < target.length) {
            int count = in.read(target, total, target.length - total);
            if (count < 0) {
                break;
            }
            total += count;
        }
        position += total;
        return total;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
