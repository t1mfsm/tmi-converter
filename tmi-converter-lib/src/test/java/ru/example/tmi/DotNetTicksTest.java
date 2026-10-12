package ru.example.tmi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

class DotNetTicksTest {

    private static final ZoneOffset MSK = ZoneOffset.ofHours(3);

    /** Ticks для заданного локального времени ДМВ, посчитанные независимо от тестируемого кода. */
    private static long mskTicks(LocalDateTime mskLocal) {
        LocalDateTime dotNetEpoch = LocalDateTime.of(1, 1, 1, 0, 0);
        long seconds = ChronoUnit.SECONDS.between(dotNetEpoch, mskLocal);
        return seconds * 10_000_000L + mskLocal.getNano() / 100;
    }

    @Test
    void unixEpochConstantMatchesJavaTime() {
        long seconds = ChronoUnit.SECONDS.between(
                LocalDateTime.of(1, 1, 1, 0, 0), LocalDateTime.of(1970, 1, 1, 0, 0));
        assertEquals(seconds * DotNetTicks.TICKS_PER_SECOND, DotNetTicks.UNIX_EPOCH_TICKS);
    }

    @Test
    void mskMidnightOfUnixEpochIsThreeHoursBeforeEpoch() {
        Instant instant = DotNetTicks.mskTicksToInstant(DotNetTicks.UNIX_EPOCH_TICKS);
        assertEquals(Instant.parse("1969-12-31T21:00:00Z"), instant);
    }

    @Test
    void convertsTimeFromSsppDocumentExample() {
        // Пример из документа ССПП: 0x5f3a3fab секунд и 0x11584ec0 наносекунд,
        // что соответствует 2020-08-17 11:28:27.291 ДМВ.
        long ticks = mskTicks(LocalDateTime.of(2020, 8, 17, 11, 28, 27, 291_000_000));

        Instant instant = DotNetTicks.mskTicksToInstant(ticks);

        assertEquals(0x5f3a3fabL, instant.getEpochSecond());
        assertEquals(0x11584ec0, instant.getNano());
    }

    @Test
    void keepsFractionalPartWithTickPrecision() {
        LocalDateTime local = LocalDateTime.of(2021, 7, 19, 23, 59, 59, 123_456_700);

        Instant instant = DotNetTicks.mskTicksToInstant(mskTicks(local));

        assertEquals(OffsetDateTime.of(local, MSK).toInstant(), instant);
        assertEquals(123_456_700, instant.getNano());
    }

    @Test
    void handlesTimeBeforeUnixEpochWithPositiveNanos() {
        // 1969-12-31 23:59:59.9 UTC: секунды должны быть -1, наносекунды 900 000 000,
        // а не 0 и -100 000 000, как получилось бы при обычном делении.
        LocalDateTime local = LocalDateTime.of(1970, 1, 1, 2, 59, 59, 900_000_000);

        Instant instant = DotNetTicks.mskTicksToInstant(mskTicks(local));

        assertEquals(-1L, instant.getEpochSecond());
        assertEquals(900_000_000, instant.getNano());
        assertEquals(Instant.parse("1969-12-31T23:59:59.900Z"), instant);
    }

    @Test
    void handlesFarPast() {
        LocalDateTime local = LocalDateTime.of(1900, 3, 1, 12, 0, 0, 500);

        Instant instant = DotNetTicks.mskTicksToInstant(mskTicks(local));

        assertEquals(OffsetDateTime.of(local, MSK).toInstant(), instant);
    }

    @Test
    void convertsMinimalTicks() {
        assertEquals(Instant.parse("0000-12-31T21:00:00Z"), DotNetTicks.mskTicksToInstant(0L));
    }

    @Test
    void roundTripsThroughInstant() {
        Instant[] samples = {
                Instant.parse("2020-08-17T08:28:27.291Z"),
                Instant.parse("1969-12-31T23:59:59.999999900Z"),
                Instant.parse("1970-01-01T00:00:00Z"),
                Instant.parse("2038-01-19T03:14:08.0000001Z"),
        };
        for (Instant sample : samples) {
            long ticks = DotNetTicks.instantToMskTicks(sample);
            assertEquals(sample, DotNetTicks.mskTicksToInstant(ticks), sample.toString());
        }
    }

    @Test
    void instantToTicksDropsSubTickNanos() {
        Instant instant = Instant.parse("2020-01-01T00:00:00Z").plusNanos(199);
        long ticks = DotNetTicks.instantToMskTicks(instant);
        assertEquals(Instant.parse("2020-01-01T00:00:00Z").plusNanos(100),
                DotNetTicks.mskTicksToInstant(ticks));
    }
}
