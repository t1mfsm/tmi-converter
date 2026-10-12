package ru.example.tmi;

import java.time.Instant;

/**
 * Преобразование значений .NET {@code DateTime.Ticks}, записанных в декретном
 * московском времени (ДМВ), в {@link Instant} и обратно.
 *
 * <p>Ticks: количество 100-наносекундных интервалов, прошедших с полуночи
 * 1 января 0001 года. В исходном архиве время записано в ДМВ, то есть
 * со смещением UTC+3 без перехода на летнее время.
 */
public final class DotNetTicks {

    /** Количество ticks в одной секунде. */
    public static final long TICKS_PER_SECOND = 10_000_000L;

    /** Количество наносекунд в одном tick. */
    public static final int NANOS_PER_TICK = 100;

    /** Значение ticks, соответствующее 1970-01-01T00:00:00. */
    public static final long UNIX_EPOCH_TICKS = 621_355_968_000_000_000L;

    /** Смещение ДМВ относительно UTC (3 часа) в ticks. */
    public static final long MSK_OFFSET_TICKS = 3L * 3600 * TICKS_PER_SECOND;

    private DotNetTicks() {
    }

    /**
     * Переводит ticks в ДМВ в момент времени UTC.
     *
     * @param mskTicks значение {@code DateTime.Ticks} в ДМВ
     * @return соответствующий момент времени
     */
    public static Instant mskTicksToInstant(long mskTicks) {
        long unixTicks = mskTicks - UNIX_EPOCH_TICKS - MSK_OFFSET_TICKS;
        long seconds = Math.floorDiv(unixTicks, TICKS_PER_SECOND);
        int nanos = (int) Math.floorMod(unixTicks, TICKS_PER_SECOND) * NANOS_PER_TICK;
        return Instant.ofEpochSecond(seconds, nanos);
    }

    /**
     * Переводит момент времени в ticks в ДМВ. Доли меньше 100 нс отбрасываются
     * (округление вниз).
     *
     * @param instant момент времени
     * @return значение {@code DateTime.Ticks} в ДМВ
     * @throws ArithmeticException если момент не представим в ticks
     */
    public static long instantToMskTicks(Instant instant) {
        long secondsTicks = Math.multiplyExact(instant.getEpochSecond(), TICKS_PER_SECOND);
        long unixTicks = Math.addExact(secondsTicks, instant.getNano() / NANOS_PER_TICK);
        return Math.addExact(unixTicks, UNIX_EPOCH_TICKS + MSK_OFFSET_TICKS);
    }
}
