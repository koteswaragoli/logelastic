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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.ws.http.HTTPException;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationException;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.util.IOUtils;

public class HttpURLConnectionManager extends HttpManager {

    private static final Charset CHARSET = Charset.forName("US-ASCII");

    private final String urlString;
    private final String parsedUrlString;
    private final String method;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final Property[] properties;
    private final SslConfiguration sslConfiguration;
    private final boolean verifyHostname;

    public HttpURLConnectionManager(final Configuration configuration, final LoggerContext loggerContext, final String name,
                                    final String urlString, final String method, final int connectTimeoutMillis,
                                    final int readTimeoutMillis,
                                    final Property[] properties,
                                    final SslConfiguration sslConfiguration,
                                    final boolean verifyHostname) {
        super(configuration, loggerContext, name);
        this.urlString = urlString;
        this.parsedUrlString = new StrSubstitutor(System.getProperties()).replace(urlString).toLowerCase();
        this.method = Objects.requireNonNull(method, "method");
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.properties = properties != null ? properties : new Property[0];
        this.sslConfiguration = sslConfiguration;
        this.verifyHostname = verifyHostname;
    }

    @Override
    public void startup() {
    }

    @Override
    public void send(final Layout<?> layout, final LogEvent event) throws IOException {
        
        Set<Property> headers = new HashSet<>();
        if (layout.getContentType() != null) {
            headers.add(Property.createProperty(
                    "Content-Type",
                    layout.getContentType()));
        }

        for (final Property property : properties) {
            headers.add(Property.createProperty(
                    property.getName(),
                    property.isValueNeedsLookup() ? getConfiguration().getStrSubstitutor().replace(event, property.getValue()) : property.getValue()));
        }
        
        
        // Send the logevent over HTTP.
        try {
            httpConnect(method, parsedUrlString, headers, layout.toByteArray(event));
        } catch (final ConnectException|SocketTimeoutException|UnknownHostException e) {
            fakeLogMessage("ERROR", e.getClass().getSimpleName(), e.getMessage());
        } catch (final HTTPException e) {
            if (e.getStatusCode() == 404 ) {
                fakeLogMessage("WARN", e.getClass().getSimpleName(), "Index does not exist, (re)creating..");
                byte[] body= toByteArray(this.getClass().getResourceAsStream("/index_mapping.json"));
                httpConnect("PUT", parsedUrlString.toString().replaceAll("/_doc$", ""), headers, body);
                httpConnect(method, parsedUrlString, headers, layout.toByteArray(event));
            }
        }
    }
    
    private void httpConnect(String method, String urlString, Set<Property> headers, byte[] body) throws IOException {
        final URL url = new URL(urlString);
        final HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
        urlConnection.setAllowUserInteraction(false);
        urlConnection.setDoOutput(true);
        urlConnection.setDoInput(true);
        urlConnection.setRequestMethod(method);
        if (connectTimeoutMillis > 0) {
            urlConnection.setConnectTimeout(connectTimeoutMillis);
        }
        if (readTimeoutMillis > 0) {
            urlConnection.setReadTimeout(readTimeoutMillis);
        }
        
        for (final Property header : headers) {
            urlConnection.setRequestProperty(
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
            ((HttpsURLConnection)urlConnection).setHostnameVerifier(LaxHostnameVerifier.INSTANCE);
        }
        
        if (sslConfiguration != null) {
            ((HttpsURLConnection)urlConnection).setSSLSocketFactory(sslConfiguration.getSslSocketFactory());
        }
        
        urlConnection.setFixedLengthStreamingMode(body.length);
        urlConnection.connect();
        try (OutputStream os = urlConnection.getOutputStream()) {
            os.write(body);
        }

        final byte[] buffer = new byte[1024];
        try (InputStream is = urlConnection.getInputStream()) {
            while (IOUtils.EOF != is.read(buffer)) {
                // empty
            }
        } catch (final IOException e) {
            final StringBuilder errorMessage = new StringBuilder();
            
            try (InputStream es = urlConnection.getErrorStream()) {
                errorMessage.append(urlConnection.getResponseCode());
            
                if (urlConnection.getResponseMessage() != null) {
                    errorMessage.append(' ').append(urlConnection.getResponseMessage());
                }
            
                if (es != null) {
                    errorMessage.append(" - ");
                    int n;
                    while (IOUtils.EOF != (n = es.read(buffer))) {
                        errorMessage.append(new String(buffer, 0, n, CHARSET));
                    }
                }
            }

            switch (urlConnection.getResponseCode()) {
                case 404:
                    throw new HTTPException(404);
                default:
                    if (urlConnection.getResponseCode() > -1) {
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
