package ru.example.tmi.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ru.example.tmi.Ipv4Address;
import ru.example.tmi.source.SourcePacket;
import ru.example.tmi.source.SourcePacketWriter;

class ConverterMainTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @TempDir
    Path dir;

    @Test
    void convertsFileAndPrintsStatistics() throws IOException {
        Path input = writeInput(3);
        Path output = dir.resolve("out.sspp");

        int code = run(input.toString(), output.toString());

        assertEquals(ConverterMain.EXIT_OK, code);
        assertTrue(Files.size(output) > 0);
        assertTrue(text(out).contains("Сконвертировано пакетов: 3"), text(out));
        assertEquals("", text(err));
    }

    @Test
    void reportsUsageErrorForWrongArguments() {
        int code = run("only-one-argument");

        assertEquals(ConverterMain.EXIT_USAGE, code);
        assertTrue(text(err).contains("Использование"), text(err));
    }

    @Test
    void printsHelp() {
        assertEquals(ConverterMain.EXIT_OK, run("--help"));
        assertTrue(text(out).contains("Использование"));
    }

    @Test
    void reportsMissingInputFile() {
        int code = run(dir.resolve("missing.bin").toString(), dir.resolve("out.sspp").toString());

        assertEquals(ConverterMain.EXIT_ERROR, code);
        assertTrue(text(err).contains("входной файл не найден"), text(err));
    }

    @Test
    void reportsFormatErrorWithoutStackTrace() throws IOException {
        Path input = dir.resolve("broken.bin");
        Files.write(input, new byte[] {1, 2, 3, 4, 5});
        Path output = dir.resolve("out.sspp");

        int code = run(input.toString(), output.toString());

        assertEquals(ConverterMain.EXIT_ERROR, code);
        String message = text(err);
        assertTrue(message.startsWith("Ошибка формата входного файла: Файл обрезан"), message);
        assertFalse(message.contains("\tat "), message);
        assertFalse(Files.exists(output));
    }

    @Test
    void refusesToOverwriteInput() throws IOException {
        Path input = writeInput(1);

        assertEquals(ConverterMain.EXIT_USAGE, run(input.toString(), input.toString()));
        assertEquals(36 + 10 + 4, Files.size(input));
    }

    private Path writeInput(int packets) throws IOException {
        Path input = dir.resolve("in.bin");
        try (SourcePacketWriter writer = new SourcePacketWriter(Files.newOutputStream(input))) {
            for (int i = 0; i < packets; i++) {
                writer.write(SourcePacket.builder()
                        .sourceAddress(Ipv4Address.parse("172.17.1.1"))
                        .destinationAddress(Ipv4Address.parse("172.17.114.1"))
                        .sourcePort(50000)
                        .destinationPort(5023)
                        .counter(i)
                        .timestampTicks(637_332_605_072_910_000L + i)
                        .data(new byte[10])
                        .build());
            }
        }
        return input;
    }

    private int run(String... args) {
        try (PrintStream outStream = new PrintStream(out, true, "UTF-8");
             PrintStream errStream = new PrintStream(err, true, "UTF-8")) {
            return ConverterMain.run(args, outStream, errStream);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(ByteArrayOutputStream stream) {
        return new String(stream.toByteArray(), StandardCharsets.UTF_8);
    }
}
