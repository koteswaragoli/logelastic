package tech.raaf.logelastic.log4j;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.AbstractLogEvent;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.message.Message;

public class EnrichedLogEvent extends AbstractLogEvent {

    final long timeMillis;
    final Level level;
    final String loggerName;
    final Message message;
    final boolean  endOfBatch;
    final String threadName;
    final int threadPriority;
    final long threadId;

    public EnrichedLogEvent(LogEvent event) {
        this.timeMillis = event.getTimeMillis();
        this.level = event.getLevel();
        this.loggerName = event.getLoggerName();
        this.message = event.getMessage();
        this.endOfBatch = event.isEndOfBatch();
        this.threadName = event.getThreadName();
        this.threadPriority = event.getThreadPriority();
        this.threadId = event.getThreadId();
    }

    @Override
    public long getTimeMillis() {
        return timeMillis;
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public String getLoggerName() {
        return loggerName;
    }

    @Override
    public Message getMessage() {
        return message;
    }

    @Override
    public boolean isEndOfBatch() {
        return endOfBatch;
    }

    @Override
    public String getThreadName() {
        return threadName;
    }

    @Override
    public int getThreadPriority() {
        return threadPriority;
    }

    @Override
    public long getThreadId() {
        return threadId;
    }
}
