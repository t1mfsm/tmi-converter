package ru.example.tmi;

/**
 * Данные пакета, помеченные как сжатые zlib, не удалось разжать.
 */
public class DecompressionException extends TmiFormatException {

    private static final long serialVersionUID = 1L;

    public DecompressionException(long offset, String message) {
        super(offset, message);
    }

    public DecompressionException(long offset, String message, Throwable cause) {
        super(offset, message, cause);
    }
}
