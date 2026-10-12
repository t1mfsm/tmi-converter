package ru.example.tmi;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import ru.example.tmi.source.SourcePacket;
import ru.example.tmi.source.SourcePacketFormat;
import ru.example.tmi.source.SourcePacketReader;
import ru.example.tmi.sspp.SsppFormat;
import ru.example.tmi.sspp.SsppInfoPacket;
import ru.example.tmi.sspp.SsppProperty;
import ru.example.tmi.sspp.SsppWriter;

/**
 * Конвертер архива исходной ТМИ в формат архива ССПП ТМИ.
 *
 * <p>Результат начинается с пакета FILE_HEADER, за которым следует по одному
 * пакету INFO на каждый пакет исходного файла. Пакеты обрабатываются потоково,
 * по одному; файл целиком в память не загружается.
 *
 * <p>Соответствие полей:
 * <ul>
 * <li>DstPort: номер источника и свойство DESTINATION_PORT;</li>
 * <li>SrcIPv4Address: свойство SOURCE_IP;</li>
 * <li>SrcPort: свойство SOURCE_PORT;</li>
 * <li>Timestamp: время приема (ticks в ДМВ переводятся в UTC);</li>
 * <li>Data: данные (разжимаются, если установлен флаг zlib);</li>
 * <li>DstIPv4Address, Counter, Checksum: в целевом формате мест для них нет,
 * отбрасываются. Checksum не проверяется.</li>
 * </ul>
 */
public final class TmiConverter {

    /**
     * Биты Flags, которые понимает конвертер. Остальные биты сознательно
     * игнорируются: исходный формат определяет только бит сжатия, и неизвестный
     * бит не мешает корректно перенести пакет.
     */
    public static final int KNOWN_FLAGS = SourcePacketFormat.FLAG_ZLIB;

    private static final int BUFFER_SIZE = 64 * 1024;

    /**
     * Конвертирует поток. Потоки не закрываются; выходной поток сбрасывается
     * ({@code flush}) по окончании. Для производительности потоки следует
     * передавать буферизованными.
     *
     * @return статистика конвертации
     * @throws TmiFormatException при ошибке формата входных данных
     * @throws IOException        при ошибке ввода-вывода
     */
    public ConversionResult convert(InputStream in, OutputStream out) throws IOException {
        SourcePacketReader reader = new SourcePacketReader(in);
        SsppWriter writer = new SsppWriter(out);

        writer.writeFileHeader();
        long packetCount = 0;
        long compressedCount = 0;
        for (SourcePacket packet; (packet = reader.read()) != null; ) {
            writer.writeInfo(toInfoPacket(packet, reader.getLastPacketOffset()));
            packetCount++;
            if (packet.isCompressed()) {
                compressedCount++;
            }
        }
        writer.flush();
        return new ConversionResult(packetCount, compressedCount, reader.getPosition());
    }

    /**
     * Конвертирует файл. При ошибке частично записанный выходной файл удаляется.
     *
     * @see #convert(InputStream, OutputStream)
     */
    public ConversionResult convert(Path input, Path output) throws IOException {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(input), BUFFER_SIZE)) {
            boolean success = false;
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(output), BUFFER_SIZE)) {
                ConversionResult result = convert(in, out);
                success = true;
                return result;
            } finally {
                if (!success) {
                    Files.deleteIfExists(output);
                }
            }
        }
    }

    /**
     * Преобразует исходный пакет в пакет INFO.
     *
     * @param packet исходный пакет
     * @param offset смещение пакета во входном файле (для сообщений об ошибках)
     * @throws DataTooLongException   если данные (после разжатия) длиннее 65535 байтов
     * @throws DecompressionException если сжатые данные не удалось разжать
     */
    public static SsppInfoPacket toInfoPacket(SourcePacket packet, long offset) throws TmiFormatException {
        // Неизвестные биты Flags (вне KNOWN_FLAGS) игнорируются, см. описание константы.
        byte[] data = packet.isCompressed()
                ? decompress(packet.getData(), offset)
                : packet.getData();
        if (data.length > SsppFormat.MAX_USHORT) {
            throw new DataTooLongException(offset, "Длина данных " + data.length
                    + " байт больше 65535 и не помещается в поле «Данные» формата ССПП");
        }

        return SsppInfoPacket.builder()
                .sourceType(SsppFormat.SOURCE_TYPE_UDP)
                .sourceNumber(packet.getDestinationPort())
                .receiveTime(DotNetTicks.mskTicksToInstant(packet.getTimestampTicks()))
                .data(data)
                .addProperty(SsppProperty.sourceIp(packet.getSourceAddress()))
                .addProperty(SsppProperty.destinationPort(packet.getDestinationPort()))
                .addProperty(SsppProperty.sourcePort(packet.getSourcePort()))
                .build();
    }

    /**
     * Разжимает данные zlib. Результат ограничен 65535 байтами: разжимать
     * больше нет смысла, так как такие данные все равно не поместятся
     * в формат ССПП. Это же защищает от «zip-бомб».
     */
    private static byte[] decompress(byte[] compressed, long offset) throws TmiFormatException {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            byte[] buffer = new byte[SsppFormat.MAX_USHORT + 1];
            int length = 0;
            while (!inflater.finished()) {
                if (length == buffer.length) {
                    throw new DataTooLongException(offset,
                            "Длина данных после разжатия больше 65535 байт: не помещается в поле «Данные» формата ССПП");
                }
                int count = inflater.inflate(buffer, length, buffer.length - length);
                if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new DecompressionException(offset, "Ошибка разжатия данных: поток zlib неполон");
                }
                length += count;
            }
            return Arrays.copyOf(buffer, length);
        } catch (DataFormatException e) {
            String reason = e.getMessage() != null ? e.getMessage() : "некорректный формат zlib";
            throw new DecompressionException(offset, "Ошибка разжатия данных: " + reason, e);
        } finally {
            inflater.end();
        }
    }
}
