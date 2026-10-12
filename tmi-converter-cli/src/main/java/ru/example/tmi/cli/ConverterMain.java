package ru.example.tmi.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import ru.example.tmi.ConversionResult;
import ru.example.tmi.TmiConverter;
import ru.example.tmi.TmiFormatException;

/**
 * Консольный конвертер архива исходной ТМИ в формат архива ССПП ТМИ.
 *
 * <pre>
 * java -jar tmi-converter.jar &lt;входной файл&gt; &lt;выходной файл&gt;
 * </pre>
 */
public final class ConverterMain {

    static final int EXIT_OK = 0;
    static final int EXIT_ERROR = 1;
    static final int EXIT_USAGE = 2;

    private ConverterMain() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Выполняет конвертацию и возвращает код завершения. Ожидаемые ошибки
     * (неверные аргументы, ошибки формата и ввода-вывода) выводятся в
     * {@code err} одной строкой, без stack trace.
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 1 && ("-h".equals(args[0]) || "--help".equals(args[0]))) {
            printUsage(out);
            return EXIT_OK;
        }
        if (args.length != 2) {
            err.println("Ошибка: ожидаются два аргумента: входной и выходной файлы.");
            printUsage(err);
            return EXIT_USAGE;
        }

        Path input;
        Path output;
        try {
            input = Paths.get(args[0]);
            output = Paths.get(args[1]);
        } catch (InvalidPathException e) {
            err.println("Ошибка: некорректный путь: " + e.getInput());
            return EXIT_USAGE;
        }

        try {
            if (!Files.isRegularFile(input)) {
                err.println("Ошибка: входной файл не найден: " + input);
                return EXIT_ERROR;
            }
            if (Files.exists(output) && Files.isSameFile(input, output)) {
                err.println("Ошибка: входной и выходной файлы совпадают: " + input);
                return EXIT_USAGE;
            }

            ConversionResult result = new TmiConverter().convert(input, output);

            out.println("Сконвертировано пакетов: " + result.getPacketCount()
                    + " (из них сжатых: " + result.getCompressedPacketCount() + ")");
            out.println("Прочитано байтов: " + result.getBytesRead()
                    + ", записано байтов: " + Files.size(output));
            out.println("Результат: " + output);
            return EXIT_OK;
        } catch (TmiFormatException e) {
            err.println("Ошибка формата входного файла: " + e.getMessage());
            return EXIT_ERROR;
        } catch (IOException e) {
            err.println("Ошибка ввода-вывода: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return EXIT_ERROR;
        }
    }

    private static void printUsage(PrintStream stream) {
        stream.println("Использование: java -jar tmi-converter.jar <входной файл> <выходной файл>");
        stream.println("Конвертирует архив исходной ТМИ в формат архива ССПП ТМИ (.sspp).");
    }
}
