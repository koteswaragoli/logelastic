package tech.raaf.logelastic.log4j.appender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

public class ElasticAppenderTest {


    @Test
    public void testSomeLoggingMessages() {
        Logger logger = LogManager.getLogger(ElasticAppenderTest.class.getName());
        logger.debug("this is a message from me, world!");
        logger.warn("But what is this");
        logger.error("Oh no, more lemmings!");
        logger.info("Verily I say unto thee, end thyself, logger!");
    }
}
