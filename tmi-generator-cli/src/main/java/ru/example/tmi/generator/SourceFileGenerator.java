package ru.example.tmi.generator;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.zip.Adler32;

import ru.example.tmi.DotNetTicks;
import ru.example.tmi.Ipv4Address;
import ru.example.tmi.source.SourcePacket;
import ru.example.tmi.source.SourcePacketWriter;

/**
 * Генератор файлов в исходном формате архива ТМИ для тестирования конвертера.
 *
 * <p>Значения полей правдоподобны: несколько источников (НИПов) с адресами
 * 172.17.x.x и эфемерными портами шлют пакеты на сервер 172.17.114.1 на порты
 * из типичного набора; счетчик сквозной с нуля; время монотонно растет
 * с шагом от 1 до 500 мс. Data заполняется случайными байтами, Flags = 0
 * (сжатие не используется), Checksum = Adler-32 от Data.
 *
 * <p>Генерация полностью определяется зерном случайных чисел.
 */
public final class SourceFileGenerator {

    /** Адрес сервера, принимающего пакеты. */
    static final Ipv4Address DESTINATION_ADDRESS = Ipv4Address.of(172, 17, 114, 1);

    /** Типичные порты получателя (номера источников ТМИ). */
    static final int[] DESTINATION_PORTS = {5023, 5024, 5025, 5031, 5040, 5101};

    static final int MIN_SOURCES = 1;
    static final int MAX_SOURCES = 3;

    static final int MIN_EPHEMERAL_PORT = 49152;
    static final int MAX_EPHEMERAL_PORT = 65535;

    static final int MIN_DATA_LENGTH = 100;
    static final int MAX_DATA_LENGTH = 1400;

    /** Минимальный шаг времени между пакетами: 1 мс. */
    static final long MIN_STEP_TICKS = DotNetTicks.TICKS_PER_SECOND / 1000;

    /** Максимальный шаг времени между пакетами: 500 мс. */
    static final long MAX_STEP_TICKS = DotNetTicks.TICKS_PER_SECOND / 2;

    /** Базовый момент начала записи; к нему добавляется случайное смещение в пределах суток. */
    static final Instant BASE_START_TIME = OffsetDateTime.parse("2021-07-19T10:00:00+03:00").toInstant();

    private static final long DAY_TICKS = 24L * 3600 * DotNetTicks.TICKS_PER_SECOND;

    private final Random random;

    public SourceFileGenerator(long seed) {
        this.random = new Random(seed);
    }

    /**
     * Генерирует пакеты и пишет их в поток. Поток не закрывается.
     *
     * @param out         выходной поток
     * @param packetCount количество пакетов
     */
    public void generate(OutputStream out, int packetCount) throws IOException {
        if (packetCount < 0) {
            throw new IllegalArgumentException("Количество пакетов не может быть отрицательным: " + packetCount);
        }
        List<Source> sources = createSources();
        long ticks = DotNetTicks.instantToMskTicks(BASE_START_TIME) + nextLong(0, DAY_TICKS);

        SourcePacketWriter writer = new SourcePacketWriter(out);
        for (int counter = 0; counter < packetCount; counter++) {
            if (counter > 0) {
                ticks += nextLong(MIN_STEP_TICKS, MAX_STEP_TICKS);
            }
            Source source = sources.get(random.nextInt(sources.size()));
            byte[] data = new byte[nextInt(MIN_DATA_LENGTH, MAX_DATA_LENGTH)];
            random.nextBytes(data);

            writer.write(SourcePacket.builder()
                    .sourceAddress(source.address)
                    .destinationAddress(DESTINATION_ADDRESS)
                    .sourcePort(source.port)
                    .destinationPort(source.destinationPort)
                    .counter(counter)
                    .timestampTicks(ticks)
                    .flags(0)
                    .data(data)
                    .checksum(adler32(data))
                    .build());
        }
        writer.flush();
    }

    private List<Source> createSources() {
        List<Integer> ports = new ArrayList<>();
        for (int port : DESTINATION_PORTS) {
            ports.add(port);
        }
        Collections.shuffle(ports, random);

        int count = nextInt(MIN_SOURCES, MAX_SOURCES);
        List<Source> sources = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Ipv4Address address = Ipv4Address.of(172, 17, nextInt(1, 254), nextInt(1, 254));
            int port = nextInt(MIN_EPHEMERAL_PORT, MAX_EPHEMERAL_PORT);
            sources.add(new Source(address, port, ports.get(i)));
        }
        return sources;
    }

    /** Случайное целое из диапазона [min, max] включительно. */
    private int nextInt(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    /** Случайное длинное целое из диапазона [min, max) (диапазон меньше 2^62). */
    private long nextLong(long min, long max) {
        return min + Math.floorMod(random.nextLong(), max - min);
    }

    private static int adler32(byte[] data) {
        Adler32 checksum = new Adler32();
        checksum.update(data, 0, data.length);
        return (int) checksum.getValue();
    }

    /** Источник ТМИ: адрес и порт отправителя и порт получателя. */
    private static final class Source {

        final Ipv4Address address;
        final int port;
        final int destinationPort;

        Source(Ipv4Address address, int port, int destinationPort) {
            this.address = address;
            this.port = port;
            this.destinationPort = destinationPort;
        }
    }
}
