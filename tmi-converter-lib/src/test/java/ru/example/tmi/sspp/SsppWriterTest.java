package ru.example.tmi.sspp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import ru.example.tmi.Ipv4Address;

class SsppWriterTest {

    /**
     * Начало пакета из примера в п. 3.1 документа ССПП (до содержимого данных):
     * <pre>
     * 53 53 50 50 00 03 55 44  50 13 9f 00 00 00 00 5f
     * 3a 3f ab 11 58 4e c0 05  78
     * </pre>
     */
    private static final byte[] EXAMPLE_HEAD = bytes(
            0x53, 0x53, 0x50, 0x50, // SSPP
            0x00,                   // INFO
            0x03, 0x55, 0x44, 0x50, // "UDP"
            0x13, 0x9f,             // номер источника 5023
            0x00, 0x00, 0x00, 0x00, 0x5f, 0x3a, 0x3f, 0xab, // секунды
            0x11, 0x58, 0x4e, 0xc0, // наносекунды
            0x05, 0x78);            // длина данных 1400

    /**
     * Конец пакета из примера (после содержимого данных): два свойства,
     * SOURCE_IP 172.17.114.122 и DESTINATION_PORT 5023.
     */
    private static final byte[] EXAMPLE_TAIL = bytes(
            0x02,
            0x00, 0x04, 0xac, 0x11, 0x72, 0x7a,
            0x01, 0x02, 0x13, 0x9f);

    private static final Instant EXAMPLE_TIME = Instant.ofEpochSecond(0x5f3a3fabL, 0x11584ec0);

    @Test
    void writesFileHeaderWithFormatVersion12() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SsppWriter writer = new SsppWriter(out);

        writer.writeFileHeader();
        writer.flush();

        assertArrayEquals(bytes(
                0x53, 0x53, 0x50, 0x50,
                0x80,
                0x00, 0x00, 0x00, 0x04,
                0x00, 0x01, 0x00, 0x02), out.toByteArray());
    }

    @Test
    void writesInfoPacketMatchingDocumentExample() throws IOException {
        byte[] data = exampleData();
        SsppInfoPacket packet = SsppInfoPacket.builder()
                .sourceType(SsppFormat.SOURCE_TYPE_UDP)
                .sourceNumber(5023)
                .receiveTime(EXAMPLE_TIME)
                .data(data)
                .addProperty(SsppProperty.sourceIp(Ipv4Address.parse("172.17.114.122")))
                .addProperty(SsppProperty.destinationPort(5023))
                .build();

        byte[] actual = write(packet);

        assertEquals(EXAMPLE_HEAD.length + data.length + EXAMPLE_TAIL.length, actual.length);
        assertArrayEquals(EXAMPLE_HEAD, Arrays.copyOfRange(actual, 0, EXAMPLE_HEAD.length));
        assertArrayEquals(data, Arrays.copyOfRange(actual, EXAMPLE_HEAD.length, EXAMPLE_HEAD.length + data.length));
        assertArrayEquals(EXAMPLE_TAIL, Arrays.copyOfRange(actual, actual.length - EXAMPLE_TAIL.length, actual.length));
        // В дампе документа последний байт данных находится по смещению 0x590.
        assertEquals(0x03, actual[0x590]);
    }

    @Test
    void writesSourcePortProperty() throws IOException {
        SsppInfoPacket packet = SsppInfoPacket.builder()
                .sourceType(SsppFormat.SOURCE_TYPE_UDP)
                .sourceNumber(5023)
                .receiveTime(EXAMPLE_TIME)
                .data(bytes(0xAA))
                .addProperty(SsppProperty.sourceIp(Ipv4Address.parse("172.17.114.122")))
                .addProperty(SsppProperty.destinationPort(5023))
                .addProperty(SsppProperty.sourcePort(0xC350))
                .build();

        byte[] actual = write(packet);

        assertArrayEquals(bytes(
                0x00, 0x01, 0xAA,
                0x03,
                0x00, 0x04, 0xac, 0x11, 0x72, 0x7a,
                0x01, 0x02, 0x13, 0x9f,
                0x02, 0x02, 0xc3, 0x50),
                Arrays.copyOfRange(actual, EXAMPLE_HEAD.length - 2, actual.length));
    }

    @Test
    void writesPacketWithoutDataAndProperties() throws IOException {
        SsppInfoPacket packet = SsppInfoPacket.builder()
                .sourceType("KMS")
                .sourceNumber(0)
                .receiveTime(Instant.ofEpochSecond(-1, 900_000_000))
                .build();

        assertArrayEquals(bytes(
                0x53, 0x53, 0x50, 0x50, 0x00,
                0x03, 'K', 'M', 'S',
                0x00, 0x00,
                0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
                0x35, 0xa4, 0xe9, 0x00,
                0x00, 0x00,
                0x00), write(packet));
    }

    @Test
    void acceptsMaximumDataLength() throws IOException {
        SsppInfoPacket packet = SsppInfoPacket.builder()
                .sourceType(SsppFormat.SOURCE_TYPE_UDP)
                .receiveTime(EXAMPLE_TIME)
                .data(new byte[65535])
                .build();

        byte[] actual = write(packet);

        assertEquals((byte) 0xff, actual[EXAMPLE_HEAD.length - 2]);
        assertEquals((byte) 0xff, actual[EXAMPLE_HEAD.length - 1]);
    }

    @Test
    void rejectsValuesThatDoNotFitIntoFormat() {
        SsppInfoPacket.Builder builder = SsppInfoPacket.builder()
                .sourceType(SsppFormat.SOURCE_TYPE_UDP)
                .receiveTime(EXAMPLE_TIME);

        assertThrows(IllegalArgumentException.class, () -> builder.data(new byte[65536]).build());
        assertThrows(IllegalArgumentException.class, () -> builder.data(new byte[0]).sourceNumber(65536).build());
        assertThrows(IllegalArgumentException.class, () -> SsppProperty.destinationPort(-1));
        assertThrows(IllegalArgumentException.class, () -> new SsppProperty(256, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new SsppProperty(0, new byte[256]));
    }

    /** Данные длиной 0x578, начинающиеся и заканчивающиеся как в примере документа. */
    private static byte[] exampleData() {
        byte[] data = new byte[0x578];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i * 31 + 7);
        }
        byte[] start = bytes(0x61, 0x29, 0x00, 0x00, 0x20, 0x00, 0x00);
        System.arraycopy(start, 0, data, 0, start.length);
        data[data.length - 1] = 0x03;
        return data;
    }

    private static byte[] write(SsppInfoPacket packet) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SsppWriter writer = new SsppWriter(out);
        writer.writeInfo(packet);
        writer.flush();
        return out.toByteArray();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
