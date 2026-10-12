package ru.example.tmi.sspp;

import java.util.Arrays;
import java.util.Objects;

import ru.example.tmi.Ipv4Address;

/**
 * Дополнительное свойство пакета INFO: код и значение (длина значения
 * пишется отдельным байтом). Неизменяемое.
 */
public final class SsppProperty {

    private final int code;
    private final byte[] value;

    /**
     * @param code  код свойства (0..255)
     * @param value значение свойства (не длиннее 255 байтов)
     */
    public SsppProperty(int code, byte[] value) {
        if (code < 0 || code > SsppFormat.MAX_BYTE) {
            throw new IllegalArgumentException("Код свойства вне диапазона 0..255: " + code);
        }
        Objects.requireNonNull(value, "value");
        if (value.length > SsppFormat.MAX_BYTE) {
            throw new IllegalArgumentException("Значение свойства длиннее 255 байтов: " + value.length);
        }
        this.code = code;
        this.value = value.clone();
    }

    /**
     * Свойство SOURCE_IP: адрес без собственного байта длины.
     */
    public static SsppProperty sourceIp(Ipv4Address address) {
        return new SsppProperty(SsppFormat.PROPERTY_SOURCE_IP, address.toBytes());
    }

    /**
     * Свойство DESTINATION_PORT ({@code UShort}).
     */
    public static SsppProperty destinationPort(int port) {
        return new SsppProperty(SsppFormat.PROPERTY_DESTINATION_PORT, ushort(port));
    }

    /**
     * Свойство SOURCE_PORT ({@code UShort}).
     */
    public static SsppProperty sourcePort(int port) {
        return new SsppProperty(SsppFormat.PROPERTY_SOURCE_PORT, ushort(port));
    }

    private static byte[] ushort(int value) {
        if (value < 0 || value > SsppFormat.MAX_USHORT) {
            throw new IllegalArgumentException("Значение вне диапазона UShort: " + value);
        }
        return new byte[] {(byte) (value >>> 8), (byte) value};
    }

    public int getCode() {
        return code;
    }

    public byte[] getValue() {
        return value.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SsppProperty)) {
            return false;
        }
        SsppProperty other = (SsppProperty) o;
        return code == other.code && Arrays.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return 31 * code + Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        StringBuilder hex = new StringBuilder();
        for (byte b : value) {
            hex.append(String.format("%02x", b));
        }
        return "SsppProperty{code=" + code + ", value=" + hex + "}";
    }
}
