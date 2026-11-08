package dev.momentum.desktop.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Minimal logging utility for MOMENTUM desktop.
 * <p>
 * Provides INFO/WARN/ERROR levels with timestamps and optional file output.
 * Intentionally dependency-free to keep the desktop shell lightweight.
 */
public final class Logger {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());

    private static PrintWriter fileWriter;

    private Logger() {}

    /**
     * Initialize file logging to the given directory. Call once at startup.
     */
    public static void init(Path logDir) {
        try {
            java.nio.file.Files.createDirectories(logDir);
            Path logFile = logDir.resolve("momentum.log");
            fileWriter = new PrintWriter(new FileWriter(logFile.toFile(), true), true);
        } catch (IOException e) {
            System.err.println("MOMENTUM: Could not initialize file logging: " + e.getMessage());
        }
    }

    public static void info(String message) {
        log("INFO", message);
    }

    public static void warn(String message) {
        log("WARN", message);
    }

    public static void error(String message) {
        log("ERROR", message);
    }

    public static void error(String message, Throwable cause) {
        log("ERROR", message + " | " + cause.getMessage());
        cause.printStackTrace(System.err);
    }

    private static void log(String level, String message) {
        String line = String.format("%s [%s] %s", TS.format(Instant.now()), level, message);
        System.out.println(line);
        if (fileWriter != null) {
            fileWriter.println(line);
            fileWriter.flush();
        }
    }
}
