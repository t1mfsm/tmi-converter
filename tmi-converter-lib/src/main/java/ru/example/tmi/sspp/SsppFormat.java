package ru.example.tmi.sspp;

/**
 * Константы формата транспортно-архивных пакетов ССПП ТМИ
 * (п. 3 документа «Протокол и структура архива ССПП ТМИ»).
 * Все целые поля записываются в Big Endian.
 */
public final class SsppFormat {

    /** Сигнатура в начале каждого пакета: ASCII {@code SSPP}. */
    static final byte[] SIGNATURE = {'S', 'S', 'P', 'P'};

    /** Тип пакета INFO (пакет с ТМИ). */
    public static final int PACKET_TYPE_INFO = 0x00;

    /** Тип пакета FILE_HEADER (заголовок файла архива). */
    public static final int PACKET_TYPE_FILE_HEADER = 0x80;

    /** Основной номер версии формата архива (последняя версия в истории изменений). */
    public static final int FORMAT_VERSION_MAJOR = 1;

    /** Вторичный номер версии формата архива. */
    public static final int FORMAT_VERSION_MINOR = 2;

    /** Тип источника для пакетов, принятых по UDP. */
    public static final String SOURCE_TYPE_UDP = "UDP";

    /** Код свойства SOURCE_IP: IP-адрес отправителя. */
    public static final int PROPERTY_SOURCE_IP = 0;

    /** Код свойства DESTINATION_PORT: порт получателя. */
    public static final int PROPERTY_DESTINATION_PORT = 1;

    /** Код свойства SOURCE_PORT: порт отправителя. */
    public static final int PROPERTY_SOURCE_PORT = 2;

    /** Максимальное значение типа UShort; ограничивает длину поля «Данные». */
    public static final int MAX_USHORT = 0xFFFF;

    /** Максимальное значение типа Byte (беззнакового). */
    public static final int MAX_BYTE = 0xFF;

    private SsppFormat() {
    }

    /**
     * Копия сигнатуры пакета ({@code SSPP}).
     */
    public static byte[] signature() {
        return SIGNATURE.clone();
    }
}
