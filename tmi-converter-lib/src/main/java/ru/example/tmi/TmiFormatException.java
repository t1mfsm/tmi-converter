package ru.example.tmi;

import java.io.IOException;

/**
 * Ошибка формата входного файла. Содержит смещение в файле начала пакета,
 * к которому относится ошибка.
 */
public class TmiFormatException extends IOException {

    private static final long serialVersionUID = 1L;

    private final long offset;

    public TmiFormatException(long offset, String message) {
        this(offset, message, null);
    }

    public TmiFormatException(long offset, String message, Throwable cause) {
        super(message + " (пакет по смещению " + offset + ", 0x" + Long.toHexString(offset) + ")", cause);
        this.offset = offset;
    }

    /**
     * Смещение начала пакета в файле в байтах.
     */
    public long getOffset() {
        return offset;
    }
}
