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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationException;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import org.apache.logging.log4j.core.util.IOUtils;

public class HttpURLConnectionManager extends HttpManager {

    private static final Charset CHARSET = Charset.forName("US-ASCII");

    private final URL url;
    private final boolean isHttps;
    private final String method;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;
    private final Property[] properties;
    private final SslConfiguration sslConfiguration;
    private final boolean verifyHostname;

    public HttpURLConnectionManager(final Configuration configuration, final LoggerContext loggerContext, final String name,
                                    final URL url, final String method, final int connectTimeoutMillis,
                                    final int readTimeoutMillis,
                                    final Property[] properties,
                                    final SslConfiguration sslConfiguration,
                                    final boolean verifyHostname) {
        super(configuration, loggerContext, name);
        this.url = url;
        if (!(url.getProtocol().equalsIgnoreCase("http") || url.getProtocol().equalsIgnoreCase("https"))) {
            throw new ConfigurationException("URL must have scheme http or https");
        }
        this.isHttps = this.url.getProtocol().equalsIgnoreCase("https");
        this.method = Objects.requireNonNull(method, "method");
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
        this.properties = properties != null ? properties : new Property[0];
        this.sslConfiguration = sslConfiguration;
        if (this.sslConfiguration != null && !isHttps) {
            throw new ConfigurationException("SSL configuration can only be specified with URL scheme https");
        }
        this.verifyHostname = verifyHostname;
    }

    @Override
    public void startup() {
        // Check if target host is accessible
        // If yes continue, else set disabled boolean
        // Check for index using HEAD
        // Create index with mapping if non-existing, using PUT
        // If index creation successful continue, else set disabled boolean
    }
    
    public void httpConnect(String method, Property[] headers, byte[] body) throws IOException {
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
        
        if (sslConfiguration != null) {
            ((HttpsURLConnection)urlConnection).setSSLSocketFactory(sslConfiguration.getSslSocketFactory());
        }
        if (isHttps && !verifyHostname) {
            ((HttpsURLConnection)urlConnection).setHostnameVerifier(LaxHostnameVerifier.INSTANCE);
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
            if (urlConnection.getResponseCode() > -1) {
                throw new IOException(errorMessage.toString());
            } else {
                throw e;
            }
        }
    }
    
    @Override
    public void send(final Layout<?> layout, final LogEvent event) throws IOException {
        
        Set<Property> headers = new HashSet<>();
        
        // Get the content type from the layout if possible.
        if (layout.getContentType() != null) {
            headers.add(Property.createProperty(
                    "Content-Type", 
                    layout.getContentType()));
        }
        
        // Get the passed properties and do string substitution on any things like $${java:runtime}.
        for (final Property property : properties) {
            headers.add(Property.createProperty(
                    property.getName(), 
                    property.isValueNeedsLookup() ? getConfiguration().getStrSubstitutor().replace(event, property.getValue()) : property.getValue()));
        }
        
        httpConnect(method, headers.toArray(new Property[headers.size()]), layout.toByteArray(event));
    }

}
