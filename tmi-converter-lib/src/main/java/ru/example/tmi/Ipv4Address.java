package ru.example.tmi;

import java.util.Arrays;

/**
 * Адрес IPv4.
 *
 * <p><b>Допущение о порядке байтов.</b> И в исходном формате (несмотря на то,
 * что остальные его поля записаны в Little Endian), и в формате ССПП адрес
 * {@code a.b.c.d} хранится как четыре байта подряд в «сетевом» порядке:
 * {@code a} в байте 0, {@code d} в байте 3. Поэтому байты адреса читаются
 * и пишутся как есть, без разворота. Это единственное место, где зафиксировано
 * представление адреса; если допущение окажется неверным, исправлять нужно
 * здесь.
 */
public final class Ipv4Address {

    /** Размер адреса в байтах. */
    public static final int SIZE = 4;

    private final byte[] octets;

    private Ipv4Address(byte[] octets) {
        this.octets = octets;
    }

    /**
     * Создает адрес из четырех байтов в сетевом порядке.
     *
     * @param source массив с адресом
     * @param offset смещение первого байта адреса
     * @return адрес
     */
    public static Ipv4Address fromBytes(byte[] source, int offset) {
        if (offset < 0 || offset + SIZE > source.length) {
            throw new IllegalArgumentException("Недостаточно байтов для IPv4-адреса по смещению " + offset);
        }
        return new Ipv4Address(Arrays.copyOfRange(source, offset, offset + SIZE));
    }

    /**
     * Создает адрес {@code a.b.c.d}.
     *
     * @throws IllegalArgumentException если какой-либо октет вне диапазона 0..255
     */
    public static Ipv4Address of(int a, int b, int c, int d) {
        int[] values = {a, b, c, d};
        byte[] octets = new byte[SIZE];
        for (int i = 0; i < SIZE; i++) {
            if (values[i] < 0 || values[i] > 255) {
                throw new IllegalArgumentException("Октет IPv4-адреса вне диапазона 0..255: " + values[i]);
            }
            octets[i] = (byte) values[i];
        }
        return new Ipv4Address(octets);
    }

    /**
     * Разбирает адрес в записи {@code a.b.c.d}.
     *
     * @throws IllegalArgumentException если строка не является адресом IPv4
     */
    public static Ipv4Address parse(String text) {
        String[] parts = text.split("\\.", -1);
        if (parts.length != SIZE) {
            throw new IllegalArgumentException("Некорректный IPv4-адрес: " + text);
        }
        try {
            return of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Некорректный IPv4-адрес: " + text, e);
        }
    }

    /**
     * Возвращает копию байтов адреса в сетевом порядке.
     */
    public byte[] toBytes() {
        return octets.clone();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof Ipv4Address && Arrays.equals(octets, ((Ipv4Address) o).octets);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(octets);
    }

    @Override
    public String toString() {
        return (octets[0] & 0xFF) + "." + (octets[1] & 0xFF) + "." + (octets[2] & 0xFF) + "." + (octets[3] & 0xFF);
    }
}
