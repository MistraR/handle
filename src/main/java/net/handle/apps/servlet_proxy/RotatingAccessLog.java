/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.apps.servlet_proxy;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import net.handle.hdllib.*;
import net.handle.util.FileSystemReadOnlyChecker;

/*******************************************************************************
 * Log class that writes error and log information to files that rotate on a
 * monthly or daily basis.
 ******************************************************************************/

public class RotatingAccessLog implements Runnable {
    private static final int ACCESS_LOG_BUFFER_SIZE = 100000;
    private static final int EXTRA_LOG_BUFFER_SIZE = 100000;
    public static final int ERRLOG_LEVEL_EVERYTHING = 0; // == log all messages
    public static final int ERRLOG_LEVEL_INFO = 25;
    public static final int ERRLOG_LEVEL_NORMAL = 50;
    public static final int ERRLOG_LEVEL_REALBAD = 75;
    public static final int ERRLOG_LEVEL_FATAL = 100;

    public enum RotationRate {
        ROTATE_MONTHLY(ChronoUnit.MONTHS, "-yyyyMM"),
        ROTATE_DAILY(ChronoUnit.DAYS, "-yyyyMMdd"),
        ROTATE_HOURLY(ChronoUnit.HOURS, "-yyyyMMddHH"),
        ROTATE_NEVER(ChronoUnit.FOREVER, "");

        private final ChronoUnit chronoUnit;
        private final String logFileSuffixPattern;

        RotationRate(ChronoUnit chronoUnit, String logFileSuffixPattern) {
            this.chronoUnit = chronoUnit;
            this.logFileSuffixPattern = logFileSuffixPattern;
        }

        public ChronoUnit getChronoUnit() {
            return chronoUnit;
        }

        public String getLogFileSuffixPattern() {
            return logFileSuffixPattern;
        }
    }

    private int errorLoggingLevel = ERRLOG_LEVEL_INFO; // Default logging level

    private File logDirectory;
    private Path reloadFile;

    private Writer accessWriter;
    private Writer errorWriter;
    private PrintStream errorPrintStream;
    private Writer extraWriter;
    private DateTimeFormatter errorLogDateFormat;

    private boolean continuing = true; // Signal to keep going...or not
    private final boolean loggingAccesses = true;
    private boolean loggingExtras = false;

    private final String ERROR_LOG_LOCK = "error_log_lock";
    private final String ACCESS_LOG_LOCK = "access_log_lock";

    private Thread flusherThread;
    private Thread rotaterThread;
    private final LogRotater logRotater;
    private ScheduledExecutorService reloadExecServ;

    private final AtomicInteger requestsCurrentMinute = new AtomicInteger();
    private final AtomicInteger requestsPastMinute = new AtomicInteger();
    private final AtomicInteger peakRequestsPerMinute = new AtomicInteger();
    private ScheduledExecutorService requestCountingExecServ;

    private static final Object loggerLock = new Object();
    private static volatile RotatingAccessLog logger;

    /**
     * Return the singleton RotatingAccessLog object.
     * If the RotatingAccessLog singleton does not already
     * exist, construct one with the given configuration settings.
     */
    public static RotatingAccessLog getLogger(File logDir, RotationRate rotationRate) throws Exception
    {
        if (logger != null) return logger;

        synchronized (loggerLock) {
            if (logger == null) {
                logger = new RotatingAccessLog(logDir, rotationRate);
            }
        }
        return logger;
    }

    /**
     * Construct a log handler to write to log files under the specified directory.
     * If the directory doesnt exist, isn't writable, or is null, the handler
     * will just write messages to stderr.
     */
    RotatingAccessLog(File logDir, RotationRate rotationRate) throws Exception {
        // make sure the log directory exists and is a directory
        if (logDir == null) throw new NullPointerException("Log directory cannot be null");
        if (logDir.exists()) {
            if (!logDir.isDirectory())
                throw new Exception("\"" + logDir.getAbsolutePath() + "\" is not a directory.");
        } else {
            boolean created = logDir.mkdirs();
            if (!created || FileSystemReadOnlyChecker.isReadOnly(logDir)) {
                throw new Exception("Log directory is not writable.");
            }
        }

        this.logDirectory = logDir;
        this.reloadFile = logDir.toPath().resolve("log_file_reload_required");
        this.errorLogDateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss z").withZone(ZoneId.systemDefault());
        this.loggingExtras = true;

        requestCountingExecServ = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RotatingAccessLog-counter");
            t.setDaemon(true);
            return t;
        });
        requestCountingExecServ.scheduleAtFixedRate(this::rotateRequestsPerMinute, 1, 1, TimeUnit.MINUTES);

        reloadExecServ = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RotatingAccessLog-reloadWatcher");
            t.setDaemon(true);
            return t;
        });
        reloadExecServ.scheduleAtFixedRate(this::reloadLogFileIfRequested, 1, 1, TimeUnit.MINUTES);

        logRotater = new LogRotater(rotationRate);
        logRotater.setLogFiles(System.currentTimeMillis());

        if (rotationRate != RotationRate.ROTATE_NEVER) {
            rotaterThread = new Thread(logRotater);
            rotaterThread.setPriority(Thread.MIN_PRIORITY);
            rotaterThread.setDaemon(true);
            rotaterThread.start();
        }

        logError(ERRLOG_LEVEL_INFO, "Started new run.");

        flusherThread = new Thread(this);
        flusherThread.setDaemon(true);
        flusherThread.start();
    }

    public void setLoggingExtras(boolean logExtras) {
        this.loggingExtras = logExtras;
    }

    /**
     * Sets the level at which messages equal to or over will be logged.
     */
    public void setErrorLogLevel(int newLogLevel) {
        errorLoggingLevel = newLogLevel;
    }

    private static String removeNewlines(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r");
    }

    public void logAccessAndExtra(String accessLogStr, String extraLogStr) {
        requestsCurrentMinute.incrementAndGet();
        synchronized (ACCESS_LOG_LOCK) {
            if (accessWriter == null) {
                System.err.println(accessLogStr);
            } else {
                try {
                    accessWriter.write(removeNewlines(accessLogStr));
                    accessWriter.write('\n');
                } catch (Exception e) {
                    System.err.println("Error writing to access log: (" + e + "): " + accessLogStr);
                }
            }
            if (extraWriter == null) {
                if (extraLogStr != null) System.err.println(extraLogStr);
            } else {
                try {
                    if (extraLogStr != null) extraWriter.write(removeNewlines(extraLogStr));
                    extraWriter.write('\n');
                } catch (Exception e) {
                    System.err.println("Error writing to extra log: (" + e + "): " + extraLogStr);
                }
            }
        }
    }

    /**
     * Write a message to the error log
     */
    public void logError(int level, String logString) {
        if (level < errorLoggingLevel || logString == null) { // No-op in either case
            return;
        }

        String msg = "\"" + errorLogDateFormat.format(Instant.now()) + "\" " + level + ' ' + logString;

        // If level is "fatal", write to stderr
        // even if writing to the error log, too

        if ((level == ERRLOG_LEVEL_FATAL) || (errorWriter == null)) System.err.println(msg);

        if (errorWriter != null) {
            synchronized (ERROR_LOG_LOCK) {
                try {
                    errorWriter.write(msg + '\n');
                    errorWriter.flush();
                } catch (Exception e) {
                    System.err.println("Error (" + e + ") writing \"" + logString + "\" to error log.");
                }
            }
        }
    }

    /**
     * Sets the file where access log entries will be written.  If the file already exists
     * then new entries will be appended to the file.
     */
    private void setAccessLogFile(File newAccessLogFile) throws IOException {
        synchronized (ACCESS_LOG_LOCK) {
            // close the old access log
            if (accessWriter != null) {
                accessWriter.flush();
                accessWriter.close();
                accessWriter = null;
            }

            if (loggingAccesses) {
                accessWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(newAccessLogFile.getAbsolutePath(), true), "UTF-8"), ACCESS_LOG_BUFFER_SIZE);
            }
        }
    }

    /**
     * Sets the file where extra log entries will be written.  If the file already exists
     * then new entries will be appended to the file.
     */
    private void setExtraLogFile(File newExtraLogFile) throws IOException {
        synchronized (ACCESS_LOG_LOCK) {
            // close the old extra log
            if (extraWriter != null) {
                extraWriter.flush();
                extraWriter.close();
                extraWriter = null;
            }

            if (loggingExtras) {
                extraWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(newExtraLogFile.getAbsolutePath(), true), "UTF-8"), EXTRA_LOG_BUFFER_SIZE);
            }
        }
    }

    /*
     * Sets the file where error log entries will be written.  If the file already exists
     * then new entries will be appended to the file.
     */
    private void setErrorLogFile(File newErrorLogFile) throws IOException {
        synchronized (ERROR_LOG_LOCK) {
            // close the old error log
            Writer oldWriter = null;
            PrintStream oldPrintStream = null;
            try {
                oldPrintStream = errorPrintStream;
                oldWriter = errorWriter;
                errorWriter = null;

                FileOutputStream errf = new FileOutputStream(newErrorLogFile.getAbsolutePath(), true);
                errorPrintStream = new PrintStream(errf);
                System.setErr(errorPrintStream); // Redirect stderr to the error log
                errorWriter = new OutputStreamWriter(errf, "UTF-8");
            } finally {
                try {
                    if (oldPrintStream != null) oldPrintStream.flush();
                    if (oldWriter != null) oldWriter.close();
                    if (oldPrintStream != null) oldPrintStream.close();
                } catch (Throwable t) {
                    System.err.println("Error setting error log file: " + t);
                    t.printStackTrace(System.err);
                }
            }
        }
    }

    private void reloadLogFileIfRequested() {
        try {
            if (Files.exists(reloadFile)) {
                System.err.println("Log rotate watch file found. Reloading log files.");
                long now = System.currentTimeMillis();
                logRotater.setLogFiles(now);
                Files.delete(reloadFile);
            }
        } catch (Exception e) {
            System.err.println("Error reloading log files: " + e);
            e.printStackTrace(System.err);
        }
    }

    /**
     * The run() implementation for the flusher thread:
     * Flush the Access-Log output about every 60 seconds (asynchronously, to
     * minimize the impact on server speed).  (Error-log output is flushed with
     * each write.)
     */
    @Override
    public void run() {
        while (continuing) {
            try {
                synchronized (ACCESS_LOG_LOCK) {
                    if (accessWriter != null) accessWriter.flush();
                    if (extraWriter != null) extraWriter.flush();
                }
            } catch (Exception e) { /* Ignore */ }

            try {
                Thread.sleep(60000);
            } catch (Exception e) { /* Ignore */ }
        }
    }

    public void shutdown() {
        continuing = false;

        if (flusherThread != null) {
            synchronized (ACCESS_LOG_LOCK) {
                try { flusherThread.interrupt(); } catch (Exception e) { /* Ignore */ }
            }
        }

        if (rotaterThread != null) {
            synchronized(ACCESS_LOG_LOCK) {
                try { rotaterThread.interrupt(); } catch (Exception e) { /* Ignore */ }
            }
        }

        synchronized (ERROR_LOG_LOCK) {
            if (errorWriter != null) {
                try { errorWriter.close(); } catch (Exception e) { /* Ignore */ }
            }
        }

        synchronized (ACCESS_LOG_LOCK) {
            if (accessWriter != null) {
                try { accessWriter.close(); } catch (Exception e) { /* Ignore */ }
            }

            if (extraWriter != null) {
                try { extraWriter.close(); } catch (Exception e) { /* Ignore */ }
            }
        }

        if (requestCountingExecServ != null) {
            requestCountingExecServ.shutdown();
            try {
                requestCountingExecServ.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (reloadExecServ != null) {
            reloadExecServ.shutdown();
            try {
                reloadExecServ.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void rotateRequestsPerMinute() {
        int newRequestsPastMinute = requestsCurrentMinute.getAndSet(0);
        requestsPastMinute.set(newRequestsPastMinute);
        int pastPeak = peakRequestsPerMinute.get();
        if (newRequestsPastMinute > pastPeak) {
            peakRequestsPerMinute.set(newRequestsPastMinute);
        }
    }

    public AtomicInteger getRequestsPastMinute() {
        return requestsPastMinute;
    }

    public AtomicInteger getPeakRequestsPerMinute() {
        return peakRequestsPerMinute;
    }

    class LogRotater implements Runnable {
        private final ChronoUnit chronoUnit;
        private final DateTimeFormatter dateTimeFormatter;

        LogRotater(RotationRate rotationRate) {
            chronoUnit = rotationRate.getChronoUnit();
            String suffixPattern = rotationRate.getLogFileSuffixPattern();
            dateTimeFormatter = DateTimeFormatter.ofPattern(suffixPattern).withZone(ZoneId.systemDefault());
        }

        long getNextRotationTimeInMilli(long currentTime) {
            if (chronoUnit == ChronoUnit.FOREVER) return Long.MAX_VALUE;
            Instant instant = Instant.ofEpochMilli(currentTime);
            if (chronoUnit.isDateBased()) {
                ZonedDateTime date = instant.atZone(ZoneId.systemDefault());
                date = date.truncatedTo(ChronoUnit.DAYS);
                if (chronoUnit == ChronoUnit.MONTHS) date = date.with(ChronoField.DAY_OF_MONTH, 1);
                date = date.plus(1, chronoUnit);
                return Instant.from(date).toEpochMilli();
            } else {
                return instant.truncatedTo(chronoUnit).plus(1, chronoUnit).toEpochMilli();
            }
        }

        private String getLogFileSuffix(long currentTime) {
            Instant instant = Instant.ofEpochMilli(currentTime);
            return dateTimeFormatter.format(instant);
        }

        void setLogFiles(long currentTimeInMillis) throws IOException {
            String logFileSuffix = getLogFileSuffix(currentTimeInMillis);
            synchronized (ERROR_LOG_LOCK) {
                setErrorLogFile(new File(logDirectory, HSG.ERROR_LOG_FILE_NAME_BASE + logFileSuffix));
            }
            synchronized (ACCESS_LOG_LOCK) {
                setAccessLogFile(new File(logDirectory, HSG.ACCESS_LOG_FILE_NAME_BASE + logFileSuffix));
                setExtraLogFile(new File(logDirectory, HSG.EXTRA_LOG_FILE_NAME_BASE + logFileSuffix));
            }
        }

        @Override
        public void run() {
            while (continuing) {
                try {
                    long nextRotationTime = getNextRotationTimeInMilli(System.currentTimeMillis());
                    while (continuing && nextRotationTime > System.currentTimeMillis()) {
                        try {
                            Thread.sleep(Math.max(60000, nextRotationTime - System.currentTimeMillis()));
                        } catch (InterruptedException e) {
                            // no-op. We're probably just shutting down.
                        } catch (Throwable t) {
                            System.err.println("Error sleeping in log rotation thread: " + t);
                            t.printStackTrace(System.err);
                        }
                    }
                    setLogFiles(System.currentTimeMillis());
                } catch (Exception e) {
                    System.err.println("Error in log rotation thread " + e);
                    e.printStackTrace(System.err);
                    if (continuing) try { Thread.sleep(60000); } catch (Exception ex) { }
                }
            }
        }
    }

}
