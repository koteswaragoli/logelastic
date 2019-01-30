package tech.raaf.logelastic.log4j.appender;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

public class ElasticAppenderTest {


    @Test
    public void testImmutableCollections() {
        Logger logger = LogManager.getLogger(ElasticAppenderTest.class.getName());
        logger.debug("this is a message from me, world!");
    }
}
