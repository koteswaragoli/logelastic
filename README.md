
# LogElastic - A Log4J2 plugin for logging directly to Elasticsearch

LogElastic is a simple Log4j2 logging component intended for shippling logs directly to Elasticsearch without intermediary components. Building results in a jar file you can include as a dependency in your pom or gradle build or drop in an existing runtime (For example in Mulesoft Anypoint Platform 3.x, usually in the /boot directory) that enables a Log4j2-using Java application to ship its logging output directly to Elasticsearch without intermediate components required (i.e, no FileBeats, no LogStash, no fluentd, etc).

The service works much like the HTTP Appender plugin it's based on with a few added niceties: It checks at plugin-start if the endpoint is available. If yes, it checks for index existence, creates the index with an explicit mapping if not and enables itself. If the endpoint is not available, it disables itself so you don't get logs with stacktraces all over the place about the connector not being able to reach the target system every log message.

Todo: document how to use. In short: Add jar to boot of runtime, add something like this to log4j2.xml:

```
<?xml version="1.0" encoding="utf-8"?>
<Configuration packages="com.ahold.development.logging.log4j.appender">
    <Appenders>
        <Console name="console" target="SYSTEM_OUT">
            <PatternLayout pattern="[%level] [%d] [${hostName}] %c{1}: %msg%n" />
        </Console>
        <Elastic name="elastic" url="http://localhost:9200/someindex/_doc">
            <Property name="X-Java-Runtime" value="$${java:runtime}" />
            <JsonLayout complete="true" properties="true"/>
        </Elastic>
    </Appenders>
    <Loggers>
        <AsyncRoot level="INFO">
            <AppenderRef ref="console" />
            <AppenderRef ref="elastic" />
        </AsyncRoot>
    </Loggers>
</Configuration>
```

A message POST'ed to Elasticsearch looks like this:

```
{
    "timeMillis": 1548849776639,
    "thread": "main",
    "level": "DEBUG",
    "loggerName": "tech.raaf.logelastic.log4j.appender.ElasticAppenderTest",
    "message": "this is a message from me, world!",
    "endOfBatch": true,
    "loggerFqcn": "org.apache.logging.log4j.spi.AbstractLogger",
    "contextMap": {},
    "threadId": 1,
    "threadPriority": 5
}
```