package ru.example.tmi.source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static ru.example.tmi.source.SourcePacketReaderTest.bytes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import ru.example.tmi.Ipv4Address;

class SourcePacketWriterTest {

    @Test
    void writesLittleEndianLayout() throws IOException {
        SourcePacket packet = SourcePacket.builder()
                .sourceAddress(Ipv4Address.of(172, 17, 114, 122))
                .destinationAddress(Ipv4Address.of(172, 17, 114, 1))
                .sourcePort(50000)
                .destinationPort(5023)
                .counter(0x8000_0007L)
                .timestampTicks(0x0102_0304_0506_0708L)
                .flags(1)
                .data(bytes(0x0A, 0x0B, 0x0C))
                .checksum(0xDEADBEEF)
                .build();

        byte[] expected = bytes(
                0xA7, 0xBE, 0x1C, 0x55,
                0xAC, 0x11, 0x72, 0x7A,
                0xAC, 0x11, 0x72, 0x01,
                0x50, 0xC3,
                0x9F, 0x13,
                0x03, 0x00, 0x00, 0x00,
                0x07, 0x00, 0x00, 0x80,
                0x08, 0x07, 0x06, 0x05, 0x04, 0x03, 0x02, 0x01,
                0x01, 0x00, 0x00, 0x00,
                0x0A, 0x0B, 0x0C,
                0xEF, 0xBE, 0xAD, 0xDE);
        assertArrayEquals(expected, write(packet));
    }

    @Test
    void readerRestoresWrittenPacket() throws IOException {
        byte[] data = new byte[300];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 7);
        }
        SourcePacket original = SourcePacketReaderTest.samplePacket(42, data);

        SourcePacket restored = new SourcePacketReader(new ByteArrayInputStream(write(original))).read();

        assertEquals(original.getSourceAddress(), restored.getSourceAddress());
        assertEquals(original.getDestinationAddress(), restored.getDestinationAddress());
        assertEquals(original.getSourcePort(), restored.getSourcePort());
        assertEquals(original.getDestinationPort(), restored.getDestinationPort());
        assertEquals(original.getCounter(), restored.getCounter());
        assertEquals(original.getTimestampTicks(), restored.getTimestampTicks());
        assertEquals(original.getFlags(), restored.getFlags());
        assertArrayEquals(original.getData(), restored.getData());
        assertEquals(original.getChecksum(), restored.getChecksum());
    }

    private static byte[] write(SourcePacket packet) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new SourcePacketWriter(out).write(packet);
        return out.toByteArray();
    }
}
