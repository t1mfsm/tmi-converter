package ru.example.tmi.generator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.Adler32;

import org.junit.jupiter.api.Test;

import ru.example.tmi.source.SourcePacket;
import ru.example.tmi.source.SourcePacketReader;

class SourceFileGeneratorTest {

    @Test
    void sameSeedGivesSameFile() throws IOException {
        assertArrayEquals(generate(42, 50), generate(42, 50));
    }

    @Test
    void differentSeedsGiveDifferentFiles() throws IOException {
        assertFalse(Arrays.equals(generate(1, 10), generate(2, 10)));
    }

    @Test
    void zeroPacketsGiveEmptyFile() throws IOException {
        assertEquals(0, generate(1, 0).length);
    }

    @Test
    void generatesPlausibleValues() throws IOException {
        List<SourcePacket> packets = read(generate(7, 200));

        assertEquals(200, packets.size());
        for (int i = 0; i < packets.size(); i++) {
            SourcePacket packet = packets.get(i);
            assertEquals(i, packet.getCounter());
            assertEquals(0, packet.getFlags());
            assertTrue(packet.getSourceAddress().toString().startsWith("172.17."), packet.toString());
            assertEquals(SourceFileGenerator.DESTINATION_ADDRESS, packet.getDestinationAddress());
            assertTrue(packet.getSourcePort() >= SourceFileGenerator.MIN_EPHEMERAL_PORT, packet.toString());
            assertTrue(Arrays.stream(SourceFileGenerator.DESTINATION_PORTS)
                    .anyMatch(port -> port == packet.getDestinationPort()), packet.toString());
            assertTrue(packet.getDataLength() >= SourceFileGenerator.MIN_DATA_LENGTH
                    && packet.getDataLength() <= SourceFileGenerator.MAX_DATA_LENGTH, packet.toString());
            assertEquals(adler32(packet.getData()), packet.getChecksum());

            if (i > 0) {
                long step = packet.getTimestampTicks() - packets.get(i - 1).getTimestampTicks();
                assertTrue(step >= SourceFileGenerator.MIN_STEP_TICKS
                        && step < SourceFileGenerator.MAX_STEP_TICKS, "шаг " + step);
            }
        }
    }

    @Test
    void sourceKeepsSameAddressAndPortsAcrossPackets() throws IOException {
        List<SourcePacket> packets = read(generate(11, 100));

        for (SourcePacket a : packets) {
            for (SourcePacket b : packets) {
                if (a.getDestinationPort() == b.getDestinationPort()) {
                    assertEquals(a.getSourceAddress(), b.getSourceAddress());
                    assertEquals(a.getSourcePort(), b.getSourcePort());
                }
            }
        }
    }

    private static byte[] generate(long seed, int packets) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new SourceFileGenerator(seed).generate(out, packets);
        return out.toByteArray();
    }

    private static List<SourcePacket> read(byte[] file) throws IOException {
        SourcePacketReader reader = new SourcePacketReader(new ByteArrayInputStream(file));
        List<SourcePacket> packets = new ArrayList<>();
        for (SourcePacket packet; (packet = reader.read()) != null; ) {
            packets.add(packet);
        }
        assertNull(reader.read());
        return packets;
    }

    private static int adler32(byte[] data) {
        Adler32 adler = new Adler32();
        adler.update(data, 0, data.length);
        return (int) adler.getValue();
    }
}
