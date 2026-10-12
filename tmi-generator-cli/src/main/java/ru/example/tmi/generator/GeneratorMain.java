package ru.example.tmi.generator;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Консольный генератор тестовых файлов в исходном формате архива ТМИ.
 *
 * <pre>
 * java -jar tmi-generator.jar &lt;выходной файл&gt; [--packets N] [--seed S]
 * </pre>
 */
public final class GeneratorMain {

    static final int EXIT_OK = 0;
    static final int EXIT_ERROR = 1;
    static final int EXIT_USAGE = 2;

    static final int DEFAULT_PACKETS = 20;
    static final int MAX_PACKETS = 1_000_000;

    private GeneratorMain() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            err.println("Ошибка: " + e.getMessage());
            printUsage(err);
            return EXIT_USAGE;
        }
        if (options.help) {
            printUsage(out);
            return EXIT_OK;
        }

        try (OutputStream stream = new BufferedOutputStream(Files.newOutputStream(options.output))) {
            new SourceFileGenerator(options.seed).generate(stream, options.packets);
        } catch (IOException e) {
            err.println("Ошибка ввода-вывода: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return EXIT_ERROR;
        }
        out.println("Сгенерировано пакетов: " + options.packets + " (seed " + options.seed + ")");
        out.println("Результат: " + options.output);
        return EXIT_OK;
    }

    private static void printUsage(PrintStream stream) {
        stream.println("Использование: java -jar tmi-generator.jar <выходной файл> [--packets N] [--seed S]");
        stream.println("  --packets N  количество пакетов, по умолчанию " + DEFAULT_PACKETS);
        stream.println("  --seed S     зерно генератора случайных чисел; при одинаковом зерне файлы совпадают.");
        stream.println("               По умолчанию выбирается случайно и выводится на экран.");
    }

    /** Разобранные аргументы командной строки. */
    static final class Options {

        Path output;
        int packets = DEFAULT_PACKETS;
        long seed;
        boolean help;

        static Options parse(String[] args) {
            Options options = new Options();
            Long seed = null;
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "-h":
                    case "--help":
                        options.help = true;
                        return options;
                    case "--packets":
                        options.packets = parseInt(value(args, ++i, arg), arg);
                        if (options.packets < 0 || options.packets > MAX_PACKETS) {
                            throw new IllegalArgumentException(
                                    "количество пакетов должно быть от 0 до " + MAX_PACKETS + ": " + options.packets);
                        }
                        break;
                    case "--seed":
                        seed = parseLong(value(args, ++i, arg), arg);
                        break;
                    default:
                        if (arg.startsWith("--")) {
                            throw new IllegalArgumentException("неизвестный параметр: " + arg);
                        }
                        if (options.output != null) {
                            throw new IllegalArgumentException("выходной файл указан дважды: " + arg);
                        }
                        try {
                            options.output = Paths.get(arg);
                        } catch (InvalidPathException e) {
                            throw new IllegalArgumentException("некорректный путь: " + arg, e);
                        }
                }
            }
            if (options.output == null) {
                throw new IllegalArgumentException("не указан выходной файл");
            }
            options.seed = seed != null ? seed : System.nanoTime();
            return options;
        }

        private static String value(String[] args, int index, String name) {
            if (index >= args.length) {
                throw new IllegalArgumentException("не указано значение параметра " + name);
            }
            return args[index];
        }

        private static int parseInt(String value, String name) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("значение " + name + " должно быть целым числом: " + value, e);
            }
        }

        private static long parseLong(String value, String name) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("значение " + name + " должно быть целым числом: " + value, e);
            }
        }
    }
}
