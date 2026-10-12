package ru.example.tmi;

/**
 * Длина данных пакета превышает допустимую.
 */
public class DataTooLongException extends TmiFormatException {

    private static final long serialVersionUID = 1L;

    public DataTooLongException(long offset, String message) {
        super(offset, message);
    }
}
