package ru.example.tmi.source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import ru.example.tmi.DataTooLongException;
import ru.example.tmi.InvalidMarkerException;
import ru.example.tmi.Ipv4Address;
import ru.example.tmi.TruncatedPacketException;

class SourcePacketReaderTest {

    /**
     * Пакет, собранный вручную побайтово, чтобы тест не зависел от
     * {@link SourcePacketWriter}.
     */
    private static final byte[] SAMPLE_PACKET = bytes(
            0xA7, 0xBE, 0x1C, 0x55,                         // Marker 0x551CBEA7, LE
            0xAC, 0x11, 0x72, 0x7A,                         // SrcIPv4Address 172.17.114.122
            0xAC, 0x11, 0x72, 0x01,                         // DstIPv4Address 172.17.114.1
            0x50, 0xC3,                                     // SrcPort 50000
            0x9F, 0x13,                                     // DstPort 5023
            0x03, 0x00, 0x00, 0x00,                         // DataLength 3
            0x07, 0x00, 0x00, 0x80,                         // Counter 0x80000007
            0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01, // Timestamp 0x0102030405060708
            0x00, 0x00, 0x00, 0x00,                         // Flags 0
            0x0A, 0x0B, 0x0C,                               // Data
            0xEF, 0xBE, 0xAD, 0xDE);                        // Checksum 0xDEADBEEF

    @Test
    void readsAllFieldsOfPacket() throws IOException {
        SourcePacketReader reader = reader(SAMPLE_PACKET);

        SourcePacket packet = reader.read();

        assertEquals(Ipv4Address.of(172, 17, 114, 122), packet.getSourceAddress());
        assertEquals(Ipv4Address.of(172, 17, 114, 1), packet.getDestinationAddress());
        assertEquals(50000, packet.getSourcePort());
        assertEquals(5023, packet.getDestinationPort());
        assertEquals(0x8000_0007L, packet.getCounter());
        assertEquals(0x0102_0304_0506_0708L, packet.getTimestampTicks());
        assertEquals(0, packet.getFlags());
        assertFalse(packet.isCompressed());
        assertArrayEquals(bytes(0x0A, 0x0B, 0x0C), packet.getData());
        assertEquals(0xDEADBEEF, packet.getChecksum());
        assertEquals(0, reader.getLastPacketOffset());
        assertNull(reader.read());
    }

    @Test
    void readsUnsignedMaximums() throws IOException {
        byte[] packet = SAMPLE_PACKET.clone();
        Arrays.fill(packet, 12, 16, (byte) 0xFF); // SrcPort, DstPort
        Arrays.fill(packet, 20, 24, (byte) 0xFF); // Counter

        SourcePacket result = reader(packet).read();

        assertEquals(65535, result.getSourcePort());
        assertEquals(65535, result.getDestinationPort());
        assertEquals(0xFFFF_FFFFL, result.getCounter());
    }

    @Test
    void readsCompressionFlag() throws IOException {
        byte[] packet = SAMPLE_PACKET.clone();
        packet[32] = 0x01;

        assertTrue(reader(packet).read().isCompressed());
    }

    @Test
    void readsSeveralPacketsInSequence() throws IOException {
        SourcePacket first = samplePacket(0, new byte[] {1, 2, 3});
        SourcePacket second = samplePacket(1, new byte[0]);
        SourcePacket third = samplePacket(2, new byte[1000]);
        byte[] file = write(first, second, third);

        SourcePacketReader reader = reader(file);

        assertEquals(0, reader.read().getCounter());
        assertEquals(1, reader.read().getCounter());
        assertEquals(36 + 3 + 4, reader.getLastPacketOffset());
        SourcePacket readThird = reader.read();
        assertEquals(2, readThird.getCounter());
        assertEquals(1000, readThird.getDataLength());
        assertNull(reader.read());
        assertNull(reader.read());
        assertEquals(file.length, reader.getPosition());
    }

    @Test
    void returnsNullForEmptyInput() throws IOException {
        assertNull(reader(new byte[0]).read());
    }

    @Test
    void rejectsInvalidMarker() {
        byte[] packet = SAMPLE_PACKET.clone();
        packet[0] = 0x00;

        InvalidMarkerException e = assertThrows(InvalidMarkerException.class, () -> reader(packet).read());

        assertEquals(0, e.getOffset());
        assertEquals(0x551CBE00, e.getActualMarker());
        assertTrue(e.getMessage().contains("0x551CBEA7"), e.getMessage());
    }

    @Test
    void reportsOffsetOfPacketWithInvalidMarker() throws IOException {
        byte[] corrupted = SAMPLE_PACKET.clone();
        corrupted[3] = 0x00;
        byte[] file = concat(SAMPLE_PACKET, corrupted);
        SourcePacketReader reader = reader(file);
        reader.read();

        InvalidMarkerException e = assertThrows(InvalidMarkerException.class, reader::read);

        assertEquals(SAMPLE_PACKET.length, e.getOffset());
    }

    static int[] everyTruncationPoint() {
        int[] points = new int[SAMPLE_PACKET.length - 1];
        for (int i = 0; i < points.length; i++) {
            points[i] = i + 1;
        }
        return points;
    }

    @ParameterizedTest(name = "файл обрезан после {0} байт")
    @MethodSource("everyTruncationPoint")
    void rejectsPacketTruncatedAtAnyPoint(int length) {
        byte[] truncated = Arrays.copyOf(SAMPLE_PACKET, length);

        TruncatedPacketException e = assertThrows(TruncatedPacketException.class,
                () -> reader(truncated).read());

        String expectedPart = length < 36 ? "заголовок" : length < 39 ? "Data" : "Checksum";
        assertEquals(expectedPart, e.getPart());
        assertEquals(0, e.getOffset());
    }

    @Test
    void reportsOffsetOfTruncatedSecondPacket() throws IOException {
        byte[] file = concat(SAMPLE_PACKET, Arrays.copyOf(SAMPLE_PACKET, 37));
        SourcePacketReader reader = reader(file);
        reader.read();

        TruncatedPacketException e = assertThrows(TruncatedPacketException.class, reader::read);

        assertEquals(SAMPLE_PACKET.length, e.getOffset());
        assertEquals("Data", e.getPart());
        assertTrue(e.getMessage().contains("ожидалось 3 байт, прочитано 1"), e.getMessage());
    }

    @Test
    void rejectsDataLengthAboveLimitWithoutAllocating() {
        byte[] packet = SAMPLE_PACKET.clone();
        Arrays.fill(packet, 16, 20, (byte) 0xFF); // DataLength = 4294967295

        DataTooLongException e = assertThrows(DataTooLongException.class, () -> reader(packet).read());

        assertTrue(e.getMessage().contains("4294967295"), e.getMessage());
    }

    @Test
    void respectsCustomDataLengthLimit() throws IOException {
        assertThrows(DataTooLongException.class,
                () -> new SourcePacketReader(new ByteArrayInputStream(SAMPLE_PACKET), 2).read());
        assertEquals(3, new SourcePacketReader(new ByteArrayInputStream(SAMPLE_PACKET), 3).read()
                .getDataLength());
    }

    @Test
    void readsFromStreamReturningOneByteAtATime() throws IOException {
        ByteArrayInputStream slow = new ByteArrayInputStream(SAMPLE_PACKET) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                return super.read(b, off, Math.min(len, 1));
            }
        };

        SourcePacket packet = new SourcePacketReader(slow).read();

        assertEquals(0xDEADBEEF, packet.getChecksum());
    }

    static SourcePacket samplePacket(long counter, byte[] data) {
        return SourcePacket.builder()
                .sourceAddress(Ipv4Address.of(172, 17, 1, 2))
                .destinationAddress(Ipv4Address.of(172, 17, 114, 1))
                .sourcePort(40000)
                .destinationPort(5023)
                .counter(counter)
                .timestampTicks(637_332_785_072_910_000L)
                .data(data)
                .checksum(0x12345678)
                .build();
    }

    private static byte[] write(SourcePacket... packets) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SourcePacketWriter writer = new SourcePacketWriter(out);
        for (SourcePacket packet : packets) {
            writer.write(packet);
        }
        writer.flush();
        return out.toByteArray();
    }

    private static SourcePacketReader reader(byte[] content) {
        return new SourcePacketReader(new ByteArrayInputStream(content));
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
