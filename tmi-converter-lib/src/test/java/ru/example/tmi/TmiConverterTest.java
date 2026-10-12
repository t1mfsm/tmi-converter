package ru.example.tmi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.example.tmi.source.SourcePacket;
import ru.example.tmi.source.SourcePacketWriter;
import ru.example.tmi.sspp.SsppInfoPacket;
import ru.example.tmi.sspp.SsppProperty;
import ru.example.tmi.sspp.SsppWriter;

class TmiConverterTest {

    private static final byte[] FILE_HEADER = {
            'S', 'S', 'P', 'P', (byte) 0x80, 0, 0, 0, 4, 0, 1, 0, 2};

    /** 2020-08-17 11:28:27.291 ДМВ в ticks. */
    private static final long SAMPLE_TICKS = 637_332_605_072_910_000L;

    private final TmiConverter converter = new TmiConverter();

    @Test
    void emptyInputProducesOnlyFileHeader() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConversionResult result = converter.convert(new ByteArrayInputStream(new byte[0]), out);

        assertArrayEquals(FILE_HEADER, out.toByteArray());
        assertEquals(0, result.getPacketCount());
    }

    @Test
    void sampleTicksMatchExpectedMoscowTime() {
        assertEquals(OffsetDateTime.of(LocalDateTime.of(2020, 8, 17, 11, 28, 27, 291_000_000),
                ZoneOffset.ofHours(3)).toInstant(), DotNetTicks.mskTicksToInstant(SAMPLE_TICKS));
    }

    @Test
    void convertsPacketFieldsByMeaning() throws IOException {
        byte[] data = randomBytes(1400, 1);
        SourcePacket packet = packet(data).build();

        byte[] actual = convert(packet);

        SsppInfoPacket expected = SsppInfoPacket.builder()
                .sourceType("UDP")
                .sourceNumber(5023)
                .receiveTime(OffsetDateTime.parse("2020-08-17T11:28:27.291+03:00").toInstant())
                .data(data)
                .addProperty(SsppProperty.sourceIp(Ipv4Address.parse("172.17.114.122")))
                .addProperty(SsppProperty.destinationPort(5023))
                .addProperty(SsppProperty.sourcePort(50000))
                .build();
        assertArrayEquals(concat(FILE_HEADER, writeInfo(expected)), actual);
    }

    @Test
    void convertsSeveralPacketsInOrder() throws IOException {
        SourcePacket first = packet(new byte[] {1}).destinationPort(5023).build();
        SourcePacket second = packet(new byte[] {2, 2}).destinationPort(5024).build();
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        ConversionResult result = converter.convert(new ByteArrayInputStream(write(first, second)), out);

        byte[] expected = concat(FILE_HEADER, concat(
                writeInfo(TmiConverter.toInfoPacket(first, 0)),
                writeInfo(TmiConverter.toInfoPacket(second, 0))));
        assertArrayEquals(expected, out.toByteArray());
        assertEquals(2, result.getPacketCount());
        assertEquals(0, result.getCompressedPacketCount());
    }

    @Test
    void decompressesZlibData() throws IOException {
        byte[] original = repeatingBytes(5000);
        byte[] compressed = zlib(original);
        assertTrue(compressed.length < original.length);

        SsppInfoPacket converted = TmiConverter.toInfoPacket(packet(compressed).flags(1).build(), 0);

        assertArrayEquals(original, converted.getData());
    }

    @Test
    void ignoresUnknownFlagBits() throws IOException {
        byte[] original = repeatingBytes(100);

        SsppInfoPacket compressed = TmiConverter.toInfoPacket(
                packet(zlib(original)).flags(0xF000_0001).build(), 0);
        SsppInfoPacket notCompressed = TmiConverter.toInfoPacket(
                packet(original).flags(0x0000_0006).build(), 0);

        assertArrayEquals(original, compressed.getData());
        assertArrayEquals(original, notCompressed.getData());
    }

    @Test
    void countsCompressedPackets() throws IOException {
        byte[] input = write(packet(zlib(new byte[10])).flags(1).build(), packet(new byte[10]).build());

        ConversionResult result = converter.convert(new ByteArrayInputStream(input), new ByteArrayOutputStream());

        assertEquals(2, result.getPacketCount());
        assertEquals(1, result.getCompressedPacketCount());
        assertEquals(input.length, result.getBytesRead());
    }

    @Test
    void acceptsDataOfMaximumLength() throws IOException {
        assertEquals(65535, TmiConverter.toInfoPacket(packet(new byte[65535]).build(), 0).getDataLength());
        assertEquals(65535, TmiConverter.toInfoPacket(
                packet(zlib(new byte[65535])).flags(1).build(), 0).getDataLength());
    }

    @Test
    void rejectsUncompressedDataLongerThanUShort() throws IOException {
        byte[] input = concat(write(packet(new byte[10]).build()), write(packet(new byte[65536]).build()));

        DataTooLongException e = assertThrows(DataTooLongException.class,
                () -> converter.convert(new ByteArrayInputStream(input), new ByteArrayOutputStream()));

        assertEquals(36 + 10 + 4, e.getOffset());
        assertTrue(e.getMessage().contains("65536"), e.getMessage());
    }

    @Test
    void rejectsDecompressedDataLongerThanUShort() throws IOException {
        byte[] compressed = zlib(new byte[65536]);

        assertThrows(DataTooLongException.class,
                () -> TmiConverter.toInfoPacket(packet(compressed).flags(1).build(), 0));
    }

    @Test
    void stopsDecompressingHugeData() throws IOException {
        // Около 10 МБ нулей сжимаются в несколько килобайт; разжимать их целиком не нужно.
        byte[] compressed = zlib(new byte[10 * 1024 * 1024]);

        assertThrows(DataTooLongException.class,
                () -> TmiConverter.toInfoPacket(packet(compressed).flags(1).build(), 0));
    }

    @Test
    void rejectsCorruptedZlibData() {
        byte[] garbage = {0x12, 0x34, 0x56, 0x78, (byte) 0x9A};

        DecompressionException e = assertThrows(DecompressionException.class,
                () -> TmiConverter.toInfoPacket(packet(garbage).flags(1).build(), 77));

        assertEquals(77, e.getOffset());
    }

    @Test
    void rejectsTruncatedZlibData() throws IOException {
        byte[] compressed = zlib(randomBytes(1000, 2));
        byte[] truncated = Arrays.copyOf(compressed, compressed.length / 2);

        assertThrows(DecompressionException.class,
                () -> TmiConverter.toInfoPacket(packet(truncated).flags(1).build(), 0));
    }

    @Test
    void ignoresChecksum() throws IOException {
        byte[] data = randomBytes(200, 3);

        byte[] withZero = convert(packet(data).checksum(0).build());
        byte[] withGarbage = convert(packet(data).checksum(0xDEADBEEF).build());

        assertArrayEquals(withZero, withGarbage);
        // Checksum не попадает в результат: FILE_HEADER + INFO и ничего больше.
        assertEquals(FILE_HEADER.length + writeInfo(TmiConverter.toInfoPacket(packet(data).build(), 0)).length,
                withGarbage.length);
    }

    @Test
    void propagatesFormatErrors() throws IOException {
        byte[] input = write(packet(new byte[5]).build());
        input[0] = 0;

        assertThrows(InvalidMarkerException.class,
                () -> converter.convert(new ByteArrayInputStream(input), new ByteArrayOutputStream()));
        assertThrows(TruncatedPacketException.class, () -> converter.convert(
                new ByteArrayInputStream(Arrays.copyOf(write(packet(new byte[5]).build()), 40)),
                new ByteArrayOutputStream()));
    }

    @Test
    void convertsFiles(@TempDir Path dir) throws IOException {
        Path input = dir.resolve("in.bin");
        Path output = dir.resolve("out.sspp");
        Files.write(input, write(packet(new byte[] {1, 2, 3}).build()));

        ConversionResult result = converter.convert(input, output);

        assertEquals(1, result.getPacketCount());
        assertArrayEquals(convert(packet(new byte[] {1, 2, 3}).build()), Files.readAllBytes(output));
    }

    @Test
    void deletesPartialOutputFileOnError(@TempDir Path dir) throws IOException {
        Path input = dir.resolve("in.bin");
        Path output = dir.resolve("out.sspp");
        byte[] content = write(packet(new byte[] {1}).build(), packet(new byte[] {2}).build());
        Files.write(input, Arrays.copyOf(content, content.length - 1));

        assertThrows(TruncatedPacketException.class, () -> converter.convert(input, output));

        assertFalse(Files.exists(output));
    }

    private static SourcePacket.Builder packet(byte[] data) {
        return SourcePacket.builder()
                .sourceAddress(Ipv4Address.parse("172.17.114.122"))
                .destinationAddress(Ipv4Address.parse("172.17.114.1"))
                .sourcePort(50000)
                .destinationPort(5023)
                .counter(1)
                .timestampTicks(SAMPLE_TICKS)
                .data(data)
                .checksum(0x11223344);
    }

    private byte[] convert(SourcePacket... packets) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        converter.convert(new ByteArrayInputStream(write(packets)), out);
        return out.toByteArray();
    }

    private static byte[] write(SourcePacket... packets) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SourcePacketWriter writer = new SourcePacketWriter(out);
        for (SourcePacket packet : packets) {
            writer.write(packet);
        }
        return out.toByteArray();
    }

    private static byte[] writeInfo(SsppInfoPacket packet) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SsppWriter writer = new SsppWriter(out);
        writer.writeInfo(packet);
        writer.flush();
        return out.toByteArray();
    }

    private static byte[] zlib(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(out, new Deflater(Deflater.BEST_COMPRESSION))) {
            deflater.write(data);
        }
        return out.toByteArray();
    }

    private static byte[] randomBytes(int length, long seed) {
        byte[] result = new byte[length];
        new Random(seed).nextBytes(result);
        return result;
    }

    private static byte[] repeatingBytes(int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++) {
            result[i] = (byte) (i % 10);
        }
        return result;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
