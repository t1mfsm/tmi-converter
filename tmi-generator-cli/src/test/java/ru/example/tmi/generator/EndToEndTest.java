package ru.example.tmi.generator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.example.tmi.ConversionResult;
import ru.example.tmi.TmiConverter;
import ru.example.tmi.source.SourcePacket;
import ru.example.tmi.source.SourcePacketReader;

/**
 * Сквозной тест: генерация входного файла, конвертация и независимый разбор
 * результата. Разбор формата ССПП написан здесь заново по документу и не
 * использует классы библиотеки, чтобы ошибка в библиотеке не «совпала сама
 * с собой».
 */
class EndToEndTest {

    private static final int PACKETS = 100;

    @TempDir
    Path dir;

    @Test
    void generatedFileIsConvertedCorrectly() throws IOException {
        Path input = dir.resolve("input.bin");
        Path output = dir.resolve("output.sspp");
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(input))) {
            new SourceFileGenerator(20260101L).generate(out, PACKETS);
        }

        ConversionResult result = new TmiConverter().convert(input, output);

        List<SourcePacket> source = readSource(input);
        List<InfoPacket> converted = readSspp(output);
        assertEquals(PACKETS, source.size());
        assertEquals(PACKETS, result.getPacketCount());
        assertEquals(PACKETS, converted.size());

        for (int i = 0; i < PACKETS; i++) {
            SourcePacket expected = source.get(i);
            InfoPacket actual = converted.get(i);
            String context = "пакет " + i;

            assertEquals("UDP", actual.sourceType, context);
            assertEquals(expected.getDestinationPort(), actual.sourceNumber, context);
            assertEquals(mskTicksToInstant(expected.getTimestampTicks()), actual.receiveTime, context);
            assertArrayEquals(expected.getData(), actual.data, context);

            assertEquals(3, actual.properties.size(), context);
            assertProperty(actual.properties.get(0), 0, expected.getSourceAddress().toBytes(), context);
            assertProperty(actual.properties.get(1), 1, ushort(expected.getDestinationPort()), context);
            assertProperty(actual.properties.get(2), 2, ushort(expected.getSourcePort()), context);
        }
    }

    /** Независимое от {@code DotNetTicks} преобразование: через календарную арифметику java.time. */
    private static Instant mskTicksToInstant(long ticks) {
        return LocalDateTime.of(1, 1, 1, 0, 0)
                .plusSeconds(ticks / 10_000_000L)
                .plusNanos(ticks % 10_000_000L * 100)
                .toInstant(ZoneOffset.ofHours(3));
    }

    private static void assertProperty(Property property, int code, byte[] value, String context) {
        assertEquals(code, property.code, context);
        assertArrayEquals(value, property.value, context);
    }

    private static byte[] ushort(int value) {
        return new byte[] {(byte) (value >> 8), (byte) value};
    }

    private static List<SourcePacket> readSource(Path file) throws IOException {
        List<SourcePacket> packets = new ArrayList<>();
        try (SourcePacketReader reader = new SourcePacketReader(new BufferedInputStream(Files.newInputStream(file)))) {
            for (SourcePacket packet; (packet = reader.read()) != null; ) {
                packets.add(packet);
            }
        }
        return packets;
    }

    /**
     * Простой парсер файла ССПП: проверяет FILE_HEADER в начале, допускает
     * далее только пакеты INFO и требует, чтобы файл заканчивался ровно
     * на границе пакета (обрыв посреди пакета дает EOFException).
     */
    private static List<InfoPacket> readSspp(Path file) throws IOException {
        List<InfoPacket> packets = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            assertEquals(0x80, readPacketStart(in), "первым должен быть FILE_HEADER");
            assertEquals(4, in.readInt(), "длина тела FILE_HEADER");
            assertEquals(1, in.readUnsignedShort(), "major");
            assertEquals(2, in.readUnsignedShort(), "minor");

            for (int type; (type = readPacketStartOrEnd(in)) >= 0; ) {
                if (type != 0x00) {
                    fail("Неожиданный тип пакета: " + type);
                }
                packets.add(readInfoBody(in));
            }
        }
        return packets;
    }

    private static int readPacketStartOrEnd(DataInputStream in) throws IOException {
        in.mark(1);
        if (in.read() < 0) {
            return -1;
        }
        in.reset();
        return readPacketStart(in);
    }

    private static int readPacketStart(DataInputStream in) throws IOException {
        byte[] signature = new byte[4];
        in.readFully(signature);
        assertEquals("SSPP", new String(signature, StandardCharsets.US_ASCII));
        return in.readUnsignedByte();
    }

    private static InfoPacket readInfoBody(DataInputStream in) throws IOException {
        InfoPacket packet = new InfoPacket();
        byte[] type = new byte[in.readUnsignedByte()];
        in.readFully(type);
        packet.sourceType = new String(type, StandardCharsets.UTF_8);
        packet.sourceNumber = in.readUnsignedShort();
        long seconds = in.readLong();
        int nanos = in.readInt();
        packet.receiveTime = Instant.ofEpochSecond(seconds, nanos);
        packet.data = new byte[in.readUnsignedShort()];
        in.readFully(packet.data);
        int propertyCount = in.readUnsignedByte();
        for (int i = 0; i < propertyCount; i++) {
            Property property = new Property();
            property.code = in.readUnsignedByte();
            property.value = new byte[in.readUnsignedByte()];
            in.readFully(property.value);
            packet.properties.add(property);
        }
        return packet;
    }

    private static final class InfoPacket {
        String sourceType;
        int sourceNumber;
        Instant receiveTime;
        byte[] data;
        final List<Property> properties = new ArrayList<>();
    }

    private static final class Property {
        int code;
        byte[] value;
    }
}
