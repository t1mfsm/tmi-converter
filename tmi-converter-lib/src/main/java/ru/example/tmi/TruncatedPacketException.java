package ru.example.tmi;

/**
 * Файл закончился посреди пакета.
 */
public class TruncatedPacketException extends TmiFormatException {

    private static final long serialVersionUID = 1L;

    private final String part;

    /**
     * @param offset   смещение начала пакета
     * @param part     часть пакета, в которой оборвался файл (заголовок, Data, Checksum)
     * @param expected ожидаемое количество байтов этой части
     * @param actual   фактически прочитанное количество байтов
     */
    public TruncatedPacketException(long offset, String part, long expected, long actual) {
        super(offset, String.format("Файл обрезан: неполное поле %s, ожидалось %d байт, прочитано %d",
                part, expected, actual));
        this.part = part;
    }

    /**
     * Часть пакета, в которой оборвался файл.
     */
    public String getPart() {
        return part;
    }
}
