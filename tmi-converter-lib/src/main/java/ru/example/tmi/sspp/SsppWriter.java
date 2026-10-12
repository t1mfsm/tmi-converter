package ru.example.tmi.sspp;

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Запись пакетов ССПП ТМИ типов FILE_HEADER и INFO. Все целые поля пишутся
 * в Big Endian ({@link DataOutputStream} использует именно этот порядок).
 */
public final class SsppWriter implements Closeable, Flushable {

    /** Размер тела FILE_HEADER: поле Version (два UShort). */
    private static final int FILE_HEADER_BODY_LENGTH = 4;

    private final DataOutputStream out;

    public SsppWriter(OutputStream out) {
        this.out = new DataOutputStream(out);
    }

    /**
     * Пишет FILE_HEADER с текущей версией формата
     * ({@value SsppFormat#FORMAT_VERSION_MAJOR}.{@value SsppFormat#FORMAT_VERSION_MINOR}).
     */
    public void writeFileHeader() throws IOException {
        writeFileHeader(SsppFormat.FORMAT_VERSION_MAJOR, SsppFormat.FORMAT_VERSION_MINOR);
    }

    /**
     * Пишет FILE_HEADER. Тело пакета имеет вид {@code ByteArray}:
     * длина ({@code Int}), затем {@code Version} (major и minor типа {@code UShort}).
     */
    public void writeFileHeader(int major, int minor) throws IOException {
        checkUShort(major, "major");
        checkUShort(minor, "minor");
        writePacketStart(SsppFormat.PACKET_TYPE_FILE_HEADER);
        out.writeInt(FILE_HEADER_BODY_LENGTH);
        out.writeShort(major);
        out.writeShort(minor);
    }

    /**
     * Пишет пакет INFO. Тело пакета имеет особую структуру (п. 3.1) и
     * в отличие от остальных типов не оборачивается в {@code ByteArray}.
     */
    public void writeInfo(SsppInfoPacket packet) throws IOException {
        writePacketStart(SsppFormat.PACKET_TYPE_INFO);

        byte[] sourceType = packet.getSourceType().getBytes(StandardCharsets.UTF_8);
        out.writeByte(sourceType.length);
        out.write(sourceType);

        out.writeShort(packet.getSourceNumber());

        Instant time = packet.getReceiveTime();
        out.writeLong(time.getEpochSecond());
        out.writeInt(time.getNano());

        out.writeShort(packet.getDataLength());
        out.write(packet.getData());

        out.writeByte(packet.getProperties().size());
        for (SsppProperty property : packet.getProperties()) {
            byte[] value = property.getValue();
            out.writeByte(property.getCode());
            out.writeByte(value.length);
            out.write(value);
        }
    }

    private void writePacketStart(int type) throws IOException {
        out.write(SsppFormat.SIGNATURE);
        out.writeByte(type);
    }

    private static void checkUShort(int value, String name) {
        if (value < 0 || value > SsppFormat.MAX_USHORT) {
            throw new IllegalArgumentException(name + " вне диапазона UShort: " + value);
        }
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
