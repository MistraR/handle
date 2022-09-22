/**********************************************************************\
 © COPYRIGHT 2019 Corporation for National Research Initiatives (CNRI);
                        All rights reserved.

        The HANDLE.NET software is made available subject to the
      Handle.Net Public License Agreement, which may be obtained at
          http://hdl.handle.net/20.1000/112 or hdl:20.1000/112
\**********************************************************************/

package net.handle.server;

import java.io.*;
import java.util.*;
import java.net.InetAddress;

import net.cnri.util.StreamTable;
import net.handle.hdllib.*;

/*******************************************************************************
 *
 * Object that writes information to access and error log files.
 *
 ******************************************************************************/

public class ServerLog implements Runnable {
    public static final int ACCESS_LOG_BUFFER_SIZE = 100000;
    public static final int ERRLOG_LEVEL_EVERYTHING = 0; // == log all messages
    public static final int ERRLOG_LEVEL_INFO = 25;
    public static final int ERRLOG_LEVEL_NORMAL = 50;
    public static final int ERRLOG_LEVEL_REALBAD = 75;
    public static final int ERRLOG_LEVEL_FATAL = 100;

    private int errorLoggingLevel = ERRLOG_LEVEL_INFO; // Default logging level

    private static Map<String,Integer> calendarDays;
    static {
        calendarDays = new HashMap<>();
        calendarDays.put(HSG.SUNDAY, Integer.valueOf(Calendar.SUNDAY));
        calendarDays.put(HSG.MONDAY, Integer.valueOf(Calendar.MONDAY));
        calendarDays.put(HSG.TUESDAY, Integer.valueOf(Calendar.TUESDAY));
        calendarDays.put(HSG.WEDNESDAY, Integer.valueOf(Calendar.WEDNESDAY));
        calendarDays.put(HSG.THURSDAY, Integer.valueOf(Calendar.THURSDAY));
        calendarDays.put(HSG.FRIDAY, Integer.valueOf(Calendar.FRIDAY));
        calendarDays.put(HSG.SATURDAY, Integer.valueOf(Calendar.SATURDAY));
    }

    private File logDirectory = null;

    private Writer accessWriter = null;
    private Writer errorWriter = null;
    private PrintStream errorPrintStream = null;

    private boolean continuing = true; // Signal to keep going...or not
    private boolean loggingAccesses = false;

    private boolean redirectStdErr = true;

    private final String ERROR_LOG_LOCK = "error_log_lock";
    private final String ACCESS_LOG_LOCK = "access_log_lock";
    private final Calendar accessCal = Calendar.getInstance();

    private Thread flusherThread = null;
    private Thread rotaterThread = null;

    /**
     * Construct a log handler to write to log files under the specified directory.
     * If the directory doesnt exist, isnt writable, or is null, the handler
     * will just write messages to stderr.
     */
    public ServerLog(File logDir, StreamTable config) throws Exception {
        this.logDirectory = logDir;

        if (logDirectory != null) {
            loadConfig(config);

            logError(ERRLOG_LEVEL_INFO, "Started new run.");

            flusherThread = new Thread(this);
            flusherThread.setDaemon(true);
            flusherThread.start();
        }
    }

    private String getAccessLogDate() {
        StringBuffer sb = new StringBuffer(40);
        int tmpInt;
        synchronized (accessCal) {
            accessCal.setTimeInMillis(System.currentTimeMillis());
            sb.append(accessCal.get(Calendar.YEAR));

            sb.append('-');
            tmpInt = accessCal.get(Calendar.MONTH) + 1;
            if (tmpInt < 10) sb.append('0');
            sb.append(tmpInt);

            sb.append('-');
            tmpInt = accessCal.get(Calendar.DATE);
            if (tmpInt < 10) sb.append('0');
            sb.append(tmpInt);

            sb.append(' ');
            tmpInt = accessCal.get(Calendar.HOUR_OF_DAY);
            if (tmpInt < 10) sb.append('0');
            sb.append(tmpInt);

            sb.append(':');
            tmpInt = accessCal.get(Calendar.MINUTE);
            if (tmpInt < 10) sb.append('0');
            sb.append(tmpInt);

            sb.append(':');
            tmpInt = accessCal.get(Calendar.SECOND);
            if (tmpInt < 10) sb.append('0');
            sb.append(tmpInt);

            sb.append('.');
            tmpInt = accessCal.get(Calendar.MILLISECOND);
            if (tmpInt < 10) sb.append('0').append('0');
            else if (tmpInt < 100) sb.append('0');
            sb.append(tmpInt);

            tmpInt = (accessCal.get(Calendar.ZONE_OFFSET) + accessCal.get(Calendar.DST_OFFSET)) / 60000; // offset in minutes from UTC
            if (tmpInt < 0) {
                tmpInt *= -1;
                sb.append('-');
                int tzHours = tmpInt / 60;
                tmpInt = tmpInt % 60;
                if (tzHours < 10) sb.append('0');
                sb.append(tzHours);
                if (tmpInt < 10) sb.append(tmpInt);
                sb.append(tmpInt);
            } else if (tmpInt > 0) {
                sb.append('+');
                int tzHours = tmpInt / 60;
                tmpInt = tmpInt % 60;
                if (tzHours < 10) sb.append('0');
                sb.append(tzHours);
                if (tmpInt < 10) sb.append(tmpInt);
                sb.append(tmpInt);
            } else {
                sb.append('Z');
            }
        }
        return sb.toString();
    }

    /**
     * Load the settings from the configuration table and start the log
     * rotation thread.
     */
    private void loadConfig(StreamTable config) throws Exception {
        // find out if we are going to be logging any accesses at all
        Object obj = config.get(HSG.INTERFACES);
        if (obj instanceof Vector) {
            Vector<?> frontEndLabels = (Vector<?>) obj;
            for (Object labelObj : frontEndLabels) {
                if (labelObj instanceof String) {
                    StreamTable conf = (StreamTable) config.get((String) labelObj + "_config");
                    loggingAccesses = conf.getBoolean(HSG.LOG_ACCESSES);
                    if (loggingAccesses) {
                        break;
                    }
                }
            }
        }

        LogRotater logRotater = null;

        StreamTable conf = (StreamTable) config.get(HSG.LOG_SAVE_CONFIG);
        if (conf == null) conf = new StreamTable();

        redirectStdErr = conf.getBoolean(HSG.LOG_REDIRECT_STDERR, redirectStdErr);

        String saveLogInterval = conf.getStr(HSG.LOG_SAVE_INTERVAL, HSG.NEVER);

        // get the alternate log directory, relative to the server directory
        File serverDir = logDirectory;
        logDirectory = new File(conf.getStr(HSG.LOG_SAVE_DIRECTORY, "logs"));
        if (!logDirectory.isAbsolute()) {
            logDirectory = new File(serverDir, conf.getStr(HSG.LOG_SAVE_DIRECTORY, "logs"));
        }

        if (saveLogInterval.equalsIgnoreCase(HSG.WEEKLY)) {
            String saveLogWeekdayStr = conf.getStr(HSG.LOG_SAVE_WEEKDAY, "");
            Integer saveLogWeekdayObj = calendarDays.get(saveLogWeekdayStr);
            int saveLogWeekday = saveLogWeekdayObj == null ? Calendar.SUNDAY : saveLogWeekdayObj.intValue();

            // set the log rotater to the weekly rotater
            logRotater = new WeeklyRotater(saveLogWeekday);
        } else if (saveLogInterval.equalsIgnoreCase(HSG.DAILY)) {

            // set the log rotater to the daily rotater
            logRotater = new DailyRotater();
        } else if (saveLogInterval.equalsIgnoreCase(HSG.MONTHLY)) {

            // set the log rotater to the monthly rotater
            logRotater = new MonthlyRotater();
        } else if (saveLogInterval.equalsIgnoreCase(HSG.NEVER)) {
            logRotater = new DefaultRotater();
        } else {
            // an invalid log rotation interval was specified
            throw new Exception("Invalid log rotation interval: \"" + saveLogInterval + "\" for " + HSG.LOG_SAVE_INTERVAL + " setting in config file");
        }

        // make sure the log directory exists and is a directory
        if (logDirectory.exists()) {
            if (!logDirectory.isDirectory()) throw new Exception("\"" + logDirectory.getAbsolutePath() + "\" is not a directory.");
        } else {
            logDirectory.mkdirs();
        }

        // allow the rotater to initialize itself now that all the settings are loaded
        logRotater.init();

        // kick off the rotater
        rotaterThread = new Thread(logRotater);
        rotaterThread.setPriority(Thread.MIN_PRIORITY);
        rotaterThread.setDaemon(true);
        rotaterThread.start();

        // wait for the log rotater to get started
        while (!logRotater.initialized()) {
            try {
                Thread.sleep(500);
            } catch (Throwable t) {
            }
        }
    }

    /**
     * (Re-)establish ErrorLoggingLevel.
     */
    public void setErrorLogLevel(int newLogLevel) {
        errorLoggingLevel = newLogLevel;
    }

    private static String removeNewlines(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r");
    }

    /**
     * Write a message to the access log
     */
    public void logAccess(String accessType, InetAddress clientAddr, int opCode, int rsCode, String logString, long time) {
        if (logString != null && accessWriter != null) {
            String msg = ((clientAddr == null) ? "" : Util.rfcIpRepr(clientAddr)) + " " + accessType + " \"" + getAccessLogDate() + "\" " + opCode + " " + rsCode + " " + time + "ms " + removeNewlines(logString);

            synchronized (ACCESS_LOG_LOCK) {
                if (accessWriter == null) {
                    // System.err.println(msg);
                } else {
                    try {
                        accessWriter.write(msg + "\n");
                    } catch (Exception e) {
                        System.err.println("Error writing to access log: (" + e + "): " + logString);
                    }
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
        String msg = "\"" + getAccessLogDate() + "\" " + level + ' ' + logString;

        // If level is "fatal", write to stderr
        // even if writing to the error log, too

        if (errorWriter != null) {
            synchronized (ERROR_LOG_LOCK) {
                try {
                    errorWriter.write(msg + '\n');
                    errorWriter.flush();
                } catch (Throwable e) {
                    System.err.println("Error (" + e + ") writing \"" + logString + "\" to error log.");
                }
            }
        } else {
            System.err.println(msg);
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
                oldWriter = errorWriter;
                errorWriter = null;
                oldPrintStream = errorPrintStream;
                errorPrintStream = null;

                FileOutputStream errf = new FileOutputStream(newErrorLogFile.getAbsolutePath(), true);
                errorPrintStream = new PrintStream(errf);
                if (redirectStdErr) System.setErr(errorPrintStream); // Redirect stderr to the error log
                errorWriter = new OutputStreamWriter(errf, "UTF-8");
            } finally {
                try {
                    if (oldPrintStream != null) oldPrintStream.flush();
                    if (oldWriter != null) oldWriter.close();
                    if (oldPrintStream != null) oldPrintStream.close();
                } catch (Throwable t) {
                }
            }
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
                }
            } catch (Exception e) {
                /* Ignore */ }

            try {
                Thread.sleep(60000);
            } catch (Exception e) {
                /* Ignore */ }
        }
    }

    /**
     * Stop the flusher thread and close the logs.
     */
    public void shutdown() {
        continuing = false; // Tell flusher thread to quit
        if (flusherThread != null) {
            synchronized (ACCESS_LOG_LOCK) { // Wake the flusher thread, if it's asleep
                try {
                    flusherThread.interrupt();
                } catch (Exception e) {
                    /* Ignore */ }
                try {
                    rotaterThread.interrupt();
                } catch (Exception e) {
                    /* Ignore */ }
            }
        }

        if (accessWriter != null) {
            try {
                accessWriter.close();
            } catch (Exception e) {
                /* Ignore */ }
        }

        if (errorWriter != null) {
            try {
                errorWriter.close();
            } catch (Exception e) {
                /* Ignore */ }
        }
    }

    private abstract class LogRotater implements Runnable {
        private volatile boolean isInitialized = false;

        /**
         * Initialize the log rotater
         */
        public void init() {
        }

        /**
         * Return the next time that the logs should be rotated in milliseconds
         */
        public abstract long getNextRotationTime(long currentTime);

        /**
         * Return the suffix that should be appended to access/error log file names
         * for the time period containing the given time.
         */
        public abstract String getLogFileSuffix(long currentTime);

        public boolean initialized() {
            return isInitialized;
        }

        @Override
        public void run() {
            while (continuing) {
                long now = System.currentTimeMillis();
                if (isInitialized) logError(ERRLOG_LEVEL_INFO, "Rotating log files");
                try {
                    // get the name of the log file for the current period
                    String logFileSuffix = getLogFileSuffix(now);
                    setAccessLogFile(new File(logDirectory, HSG.ACCESS_LOG_FILE_NAME_BASE + logFileSuffix));
                    setErrorLogFile(new File(logDirectory, HSG.ERROR_LOG_FILE_NAME_BASE + logFileSuffix));
                } catch (Throwable t) {
                    System.err.println("Error setting log files: " + t);
                    t.printStackTrace(System.err);
                }
                isInitialized = true;

                // wait until the next rotation time
                long nextRotationTime = getNextRotationTime(now);
                while (continuing && nextRotationTime > System.currentTimeMillis()) {
                    try {
                        long waitTime = Math.max(1000, nextRotationTime - System.currentTimeMillis());
                        Thread.sleep(waitTime);
                    } catch (Throwable t) {
                    }
                }
            }
        }

        /**
         * Return a date-stamp for the given date.
         */
        protected String getSuffixForDate(Calendar cal) {
            return "-" + String.valueOf(cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH));
        }
    }

    class WeeklyRotater extends LogRotater {
        protected Calendar cal = Calendar.getInstance();
        private final int dayOfRotation;

        WeeklyRotater(int dayOfRotation) {
            this.dayOfRotation = dayOfRotation;
        }

        /**
         * Return the next time that the logs should be rotated in milliseconds
         */
        @Override
        public long getNextRotationTime(long currentTime) {
            cal.setTime(new Date(currentTime));
            do {
                cal.add(Calendar.DATE, 1);
            } while (cal.get(Calendar.DAY_OF_WEEK) != dayOfRotation);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 1);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTime().getTime();
        }

        /**
         * Return the suffix that should be appended to access/error log file names
         * for the time period containing the given time.
         */
        @Override
        public String getLogFileSuffix(long currentTime) {
            cal.setTime(new Date(currentTime));
            while (cal.get(Calendar.DAY_OF_WEEK) != dayOfRotation) {
                cal.add(Calendar.DATE, -1);
            }
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 1);
            return getSuffixForDate(cal);
        }
    }

    class MonthlyRotater extends LogRotater {
        protected Calendar cal = Calendar.getInstance();

        /**
         * Return the next time that the logs should be rotated in milliseconds
         */
        @Override
        public long getNextRotationTime(long currentTime) {
            cal.setTime(new Date(currentTime));
            int thisMonth = cal.get(Calendar.MONTH);
            while (cal.get(Calendar.MONTH) == thisMonth) {
                cal.add(Calendar.DATE, 1);
            }
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 1);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTime().getTime();
        }

        /**
         * Return the suffix that should be appended to access/error log file names
         * for the time period containing the given time.
         */
        @Override
        public String getLogFileSuffix(long currentTime) {
            cal.setTime(new Date(currentTime));
            while (cal.get(Calendar.DAY_OF_MONTH) != 1) {
                cal.add(Calendar.DATE, -1);
            }
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 1);

            return "-" + String.valueOf(cal.get(Calendar.YEAR) * 100 + (cal.get(Calendar.MONTH) + 1));
        }
    }

    class DailyRotater extends LogRotater {
        protected Calendar cal = Calendar.getInstance();

        /**
         * Return the next time that the logs should be rotated in milliseconds
         */
        @Override
        public long getNextRotationTime(long currentTime) {
            cal.setTime(new Date(currentTime));
            cal.add(Calendar.DATE, 1);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 1);
            cal.set(Calendar.MILLISECOND, 0);
            return cal.getTime().getTime();
        }

        /**
         * Return the suffix that should be appended to access/error log file names
         * for the time period containing the given time.
         */
        @Override
        public String getLogFileSuffix(long currentTime) {
            cal.setTime(new Date(currentTime));
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 1);
            return getSuffixForDate(cal);
        }
    }

    class DefaultRotater extends LogRotater {
        protected Calendar cal = Calendar.getInstance();

        /**
         * Return the next time that the logs should be rotated in milliseconds
         */
        @Override
        public long getNextRotationTime(long currentTime) {
            return Long.MAX_VALUE;
        }

        /**
         * Return the suffix that should be appended to access/error log file names
         * for the time period containing the given time.
         */
        @Override
        public String getLogFileSuffix(long currentTime) {
            return "";
        }
    }

}
