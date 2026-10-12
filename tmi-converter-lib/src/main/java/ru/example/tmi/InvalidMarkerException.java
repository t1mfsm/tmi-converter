package ru.example.tmi;

/**
 * Пакет исходного файла начинается не с ожидаемого маркера.
 */
public class InvalidMarkerException extends TmiFormatException {

    private static final long serialVersionUID = 1L;

    private final int actualMarker;

    public InvalidMarkerException(long offset, int expectedMarker, int actualMarker) {
        super(offset, String.format("Неверный маркер пакета: ожидался 0x%08X, прочитан 0x%08X",
                expectedMarker, actualMarker));
        this.actualMarker = actualMarker;
    }

    public int getActualMarker() {
        return actualMarker;
    }
}
