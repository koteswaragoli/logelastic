package tech.raaf.logelastic.log4j.appender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

public class ElasticAppenderTest {


    @Test
    public void testSomeLoggingMessages() {
        Logger logger = LogManager.getLogger(ElasticAppenderTest.class.getName());
        logger.debug("this is a message from me, world!");
        doWait(1);
        logger.warn("But what is this");
        doWait(1);
        logger.error("Oh no, more lemmings!");
        doWait(1);

        for (int i = 0; i < 3; i++) {
            logger.info("Boring log message " + i);
            doWait(1);
        }

        logger.info("Verily I say unto thee, end thyself, logger!");
    }

    @Test
    public void testLoggingInitialization() {
        Logger logger = LogManager.getLogger(ElasticAppenderTest.class.getName());
    }



    @Test
    public void testIndexFrequency () {
        //System.out.println(IndexFrequency.valueOf(""));
        //System.out.println(IndexFrequency.valueOf("HOURLY"));
        System.out.println(IndexFrequency.valueOf("YEAR"));
        System.out.println(IndexFrequency.MINUTE.toString());
    }

    private static void doWait(int seconds) {
        try {
            Thread.sleep(seconds  * 1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
