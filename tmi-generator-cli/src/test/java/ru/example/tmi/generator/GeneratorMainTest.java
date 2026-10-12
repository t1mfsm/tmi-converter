package ru.example.tmi.generator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratorMainTest {

    @TempDir
    Path dir;

    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    @Test
    void parsesAllOptions() {
        GeneratorMain.Options options = GeneratorMain.Options.parse(
                new String[] {"--packets", "5", "out.bin", "--seed", "-3"});

        assertEquals("out.bin", options.output.toString());
        assertEquals(5, options.packets);
        assertEquals(-3, options.seed);
    }

    @Test
    void usesDefaultPacketCount() {
        assertEquals(GeneratorMain.DEFAULT_PACKETS, GeneratorMain.Options.parse(new String[] {"out.bin"}).packets);
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> GeneratorMain.Options.parse(new String[0]));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratorMain.Options.parse(new String[] {"out.bin", "--packets"}));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratorMain.Options.parse(new String[] {"out.bin", "--packets", "x"}));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratorMain.Options.parse(new String[] {"out.bin", "--packets", "-1"}));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratorMain.Options.parse(new String[] {"out.bin", "--unknown"}));
        assertThrows(IllegalArgumentException.class,
                () -> GeneratorMain.Options.parse(new String[] {"a.bin", "b.bin"}));
    }

    @Test
    void generatesSameFileForSameSeed() throws IOException {
        Path first = dir.resolve("first.bin");
        Path second = dir.resolve("second.bin");

        assertEquals(GeneratorMain.EXIT_OK, run(first.toString(), "--packets", "15", "--seed", "42"));
        assertEquals(GeneratorMain.EXIT_OK, run(second.toString(), "--packets", "15", "--seed", "42"));

        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    }

    @Test
    void reportsUsageError() {
        assertEquals(GeneratorMain.EXIT_USAGE, run("--packets", "abc"));
        assertTrue(new String(err.toByteArray(), StandardCharsets.UTF_8).contains("Использование"));
    }

    private int run(String... args) {
        try (PrintStream out = new PrintStream(new ByteArrayOutputStream(), true, "UTF-8");
             PrintStream errStream = new PrintStream(err, true, "UTF-8")) {
            return GeneratorMain.run(args, out, errStream);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
