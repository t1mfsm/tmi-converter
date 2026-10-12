package ru.example.tmi.source;

/**
 * Константы исходного формата архива ТМИ (все целые поля Little Endian).
 *
 * <pre>
 * Смещение  Байт  Поле
 *  0        4     Marker = 0x551CBEA7
 *  4        4     SrcIPv4Address
 *  8        4     DstIPv4Address
 * 12        2     SrcPort
 * 14        2     DstPort
 * 16        4     DataLength = n
 * 20        4     Counter
 * 24        8     Timestamp (.NET DateTime.Ticks в ДМВ)
 * 32        4     Flags
 * 36        n     Data
 * 36 + n    4     Checksum
 * </pre>
 */
public final class SourcePacketFormat {

    /** Маркер начала пакета. */
    public static final int MARKER = 0x551CBEA7;

    /** Размер заголовка пакета (все поля до Data). */
    public static final int HEADER_SIZE = 36;

    /** Размер поля Checksum. */
    public static final int CHECKSUM_SIZE = 4;

    /** Флаг: Data сжата zlib. */
    public static final int FLAG_ZLIB = 1;

    static final int MARKER_OFFSET = 0;
    static final int SRC_ADDRESS_OFFSET = 4;
    static final int DST_ADDRESS_OFFSET = 8;
    static final int SRC_PORT_OFFSET = 12;
    static final int DST_PORT_OFFSET = 14;
    static final int DATA_LENGTH_OFFSET = 16;
    static final int COUNTER_OFFSET = 20;
    static final int TIMESTAMP_OFFSET = 24;
    static final int FLAGS_OFFSET = 32;

    private SourcePacketFormat() {
    }
}
