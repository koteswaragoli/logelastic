package tech.raaf.logelastic.log4j.appender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

public class ElasticAppenderTest {


    @Test
    public void testSomeLoggingMessages() {
        Logger logger = LogManager.getLogger(ElasticAppenderTest.class.getName());
        System.out.println("About to print first log message..");
        logger.debug("this is a message from me, world!");
        System.out.println("About to print second log message..");
        logger.warn("But what is this");
        System.out.println("About to print third log message..");
        logger.error("Oh no, more lemmings!");
        System.out.println("About to print last log message..");
        logger.info("Verily I say unto thee, end thyself, logger!");
    }
}
