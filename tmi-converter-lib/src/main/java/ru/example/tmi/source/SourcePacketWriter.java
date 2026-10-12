package ru.example.tmi.source;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Запись пакетов в исходном формате архива. Используется генератором
 * тестовых файлов и тестами. Все поля, включая Checksum, пишутся как есть.
 */
public final class SourcePacketWriter implements Closeable, Flushable {

    private final OutputStream out;
    private final ByteBuffer header = ByteBuffer.allocate(SourcePacketFormat.HEADER_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN);
    private final ByteBuffer checksum = ByteBuffer.allocate(SourcePacketFormat.CHECKSUM_SIZE)
            .order(ByteOrder.LITTLE_ENDIAN);

    public SourcePacketWriter(OutputStream out) {
        this.out = Objects.requireNonNull(out, "out");
    }

    public void write(SourcePacket packet) throws IOException {
        header.clear();
        header.putInt(SourcePacketFormat.MARKER)
                .put(packet.getSourceAddress().toBytes())
                .put(packet.getDestinationAddress().toBytes())
                .putShort((short) packet.getSourcePort())
                .putShort((short) packet.getDestinationPort())
                .putInt(packet.getDataLength())
                .putInt((int) packet.getCounter())
                .putLong(packet.getTimestampTicks())
                .putInt(packet.getFlags());
        out.write(header.array());

        out.write(packet.getData());

        checksum.clear();
        checksum.putInt(packet.getChecksum());
        out.write(checksum.array());
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
