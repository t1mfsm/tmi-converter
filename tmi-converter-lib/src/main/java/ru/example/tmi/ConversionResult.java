package ru.example.tmi;

/**
 * Статистика выполненной конвертации.
 */
public final class ConversionResult {

    private final long packetCount;
    private final long compressedPacketCount;
    private final long bytesRead;

    public ConversionResult(long packetCount, long compressedPacketCount, long bytesRead) {
        this.packetCount = packetCount;
        this.compressedPacketCount = compressedPacketCount;
        this.bytesRead = bytesRead;
    }

    /**
     * Количество сконвертированных пакетов (равно количеству пакетов INFO в результате).
     */
    public long getPacketCount() {
        return packetCount;
    }

    /**
     * Сколько из них было сжато zlib во входном файле.
     */
    public long getCompressedPacketCount() {
        return compressedPacketCount;
    }

    /**
     * Количество прочитанных байтов входного файла.
     */
    public long getBytesRead() {
        return bytesRead;
    }

    @Override
    public String toString() {
        return "ConversionResult{packets=" + packetCount
                + ", compressed=" + compressedPacketCount
                + ", bytesRead=" + bytesRead + "}";
    }
}
