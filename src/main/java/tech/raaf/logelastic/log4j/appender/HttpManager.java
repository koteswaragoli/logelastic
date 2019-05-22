/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */

package tech.raaf.logelastic.log4j.appender;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractManager;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationException;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.util.IOUtils;
import tech.raaf.logelastic.log4j.config.Header;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.ws.http.HTTPException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class HttpManager extends AbstractManager {

    private static int counter = 0;
    private static Date when = new Date();

    private static final Charset CHARSET = Charset.forName("US-ASCII");
    private static final int[] SECONDS = new int[] { 0, 1, 10, 30, 60, 120, 180, 240, 300, 600, 900, 1200 };

    private final Configuration configuration;
    private final URL indexUrl;
    private final URL postUrl;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final Header[] headers;
    private final Property[] properties;
    private final SslConfiguration sslConfiguration;
    private final boolean verifyHostname;

    public HttpManager(
            final Configuration configuration,
            final String name,
            final URL indexUrl,
            final URL postUrl,
            final int connectTimeoutMillis,
            final int readTimeoutMillis,
            final Header[] headers,
            final Property[] properties,
            final SslConfiguration sslConfiguration,
            final boolean verifyHostname) throws MalformedURLException {

        super(configuration.getLoggerContext(), name);
        this.configuration = Objects.requireNonNull(configuration);
        this.indexUrl = indexUrl;
        this.postUrl = postUrl;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.headers = headers != null ? headers : new Header[0];
        this.properties = properties != null ? properties : new Property[0];
        this.sslConfiguration = sslConfiguration;
        this.verifyHostname = verifyHostname;
    }

    private Configuration getConfiguration() {
        return configuration;
    }

    void send(final Layout<?> layout, final LogEvent event) throws IOException {

        // Create a client header set  and add a Content-type header.
        Set<Header> clientHeaders = new HashSet<>();
        if (layout.getContentType() != null) {
            clientHeaders.add(Header.createHeader(
                    "Content-Type",
                    layout.getContentType()));
        }

        // Add headers passed in the configuration.
        for (final Header header : headers) {
            clientHeaders.add(Header.createHeader(
                    header.getName(),
                    header.isValueNeedsLookup() ? getConfiguration().getStrSubstitutor().replace(event, header.getValue()) : header.getValue()));
        }

        // TODO: Create an enriched Object from LogEvent, LogEvent.getMessage() and Property[].
        // The LogEvent would be the root, message would become an embedded object and Property[]
        // would be flatly appended to the root? Or maybe nicer as an embedded object also?
        // This enriched object would be JSON byte array'ed and passed to conditionalConnect instead of
        // layout.toByteArray(event).

        // Example showing an EnrichedLogEvent being JSONified. Todo: add message object smartness
        // like Menno wants it and add kv's from the properties array. This would mean something
        // like "new EnrichedLogEvent(event, properties)"
        //final ObjectWriter ow = new JacksonFactory.JSON(false, false).newWriter(
         //       false, false, false);
        //System.err.println(ow.writeValueAsString(new EnrichedLogEvent(event)));


        // Send the logevent over HTTP, handle conditional connect.
        Date now = new Date();
        if (when.equals(now) || when.before(now)) {
            try {
                if (counter != 0) {
                    fakeLogMessage("WARN", getClass().getSimpleName(), "Ah, it's around " + new SimpleDateFormat("HH:mm:ss").format(when) + ", attempting to resume logging..  ");
                }
                conditionalConnect("POST", postUrl, clientHeaders, layout.toByteArray(event));
                counter = 0;
                when.setTime(now.getTime());

            } catch (ConnectException|SocketTimeoutException|UnknownHostException e) {
                if ( counter <= SECONDS.length ) {
                    counter++;
                } else {
                    counter = 0;
                }

                when.setTime(now.getTime() + (SECONDS[counter] * 1000));
                fakeLogMessage("WARN", e.getClass().getSimpleName(), "Skipping log shipping to Elasticsearch for  " + SECONDS[counter] + " seconds.. Resuming somewhere around " + new SimpleDateFormat("HH:mm:ss").format(when));

            } catch (HTTPException e) {
                if (e.getStatusCode() == 404 ) {
                    fakeLogMessage("WARN", e.getClass().getSimpleName(), "Index does not exist, (re)creating..");
                    byte[] body= toByteArray(this.getClass().getResourceAsStream("/index_mapping.json"));
                    conditionalConnect("PUT", indexUrl, clientHeaders, body);
                    conditionalConnect("POST", postUrl, clientHeaders, layout.toByteArray(event));
                } else {
                    fakeLogMessage("WARN", e.getClass().getSimpleName(), "Got an HTTP status code that I don't handle: " + e.getStatusCode());
                }
            }
        }
    }

    private void conditionalConnect(String method, URL url, Set<Header> headers, byte[] body) throws IOException {
        final HttpURLConnection httpConnection = (HttpURLConnection)url.openConnection();
        httpConnection.setAllowUserInteraction(false);
        httpConnection.setDoOutput(true);
        httpConnection.setDoInput(true);
        httpConnection.setRequestMethod(method);

        if (connectTimeoutMillis > 0) {
            httpConnection.setConnectTimeout(connectTimeoutMillis);
        }
        if (readTimeoutMillis > 0) {
            httpConnection.setReadTimeout(readTimeoutMillis);
        }

        for (final Header header : headers) {
            httpConnection.setRequestProperty(
                    header.getName(),
                    header.getValue());
        }

        if (!(url.getProtocol().equalsIgnoreCase("http") || url.getProtocol().equalsIgnoreCase("https"))) {
            throw new ConfigurationException("URL must have scheme http or https");
        }

        boolean isHttps = url.getProtocol().equalsIgnoreCase("https");

        if (this.sslConfiguration != null && !isHttps) {
            throw new ConfigurationException("SSL configuration can only be specified with URL scheme https");
        }

        if (isHttps && !verifyHostname) {
            ((HttpsURLConnection)httpConnection).setHostnameVerifier(LaxHostnameVerifier.INSTANCE);
        }

        if (sslConfiguration != null) {
            ((HttpsURLConnection)httpConnection).setSSLSocketFactory(sslConfiguration.getSslSocketFactory());
        }

        httpConnection.setFixedLengthStreamingMode(body.length);




        httpConnection.connect();

        try (OutputStream os = httpConnection.getOutputStream()) {
            os.write(body);
        }

        final byte[] buffer = new byte[1024];

        try (InputStream is = httpConnection.getInputStream()) {
            while (IOUtils.EOF != is.read(buffer)) {
            }
        } catch (IOException e) {
            final StringBuilder errorMessage = new StringBuilder();

            try (InputStream es = httpConnection.getErrorStream()) {
                errorMessage.append(httpConnection.getResponseCode());

                if (httpConnection.getResponseMessage() != null) {
                    errorMessage.append(' ').append(httpConnection.getResponseMessage());
                }

                if (es != null) {
                    errorMessage.append(" - ");
                    int n;
                    while (IOUtils.EOF != (n = es.read(buffer))) {
                        errorMessage.append(new String(buffer, 0, n, CHARSET));
                    }
                }
            }

            switch (httpConnection.getResponseCode()) {
                case 404:
                    throw new HTTPException(404);
                default:
                    if (httpConnection.getResponseCode() > -1) {
                        throw new IOException(errorMessage.toString());
                    } else {
                        throw e;
                    }
            }
        }

    }

    private void  fakeLogMessage(String level, String logger, String message) {
        String hostname = "unknown";

        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            // empty
        }

        System.err.println("[" + level.toUpperCase() + "] [" +  getCurrentDateTime() + "] [" + hostname  +  "] " +  logger + ": " + message);

    }
    private String getCurrentDateTime() {
        return LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"));
    }

    private byte[] toByteArray(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int nRead;
        byte[] data = new byte[1024];

        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }

        return buffer.toByteArray();
    }
}
