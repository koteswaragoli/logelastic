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

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.core.net.ssl.SslConfiguration;
import tech.raaf.logelastic.log4j.config.Header;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Sends log events over HTTP to Elasticsearch.
 */
@Plugin(name = "Elastic", category = Node.CATEGORY, elementType = Appender.ELEMENT_TYPE, printObject = true)
public final class ElasticAppender extends AbstractAppender {

    /**
     * Builds ElasticAppender instances.
     * @param <B> The type to build
     */
    public static class Builder<B extends Builder<B>> extends AbstractAppender.Builder<B>
            implements org.apache.logging.log4j.core.util.Builder<ElasticAppender> {

        private HttpManager httpManager = null;

        @PluginBuilderAttribute
        @Required(message = "No URL provided for ElasticAppender")
        private String url;

        @PluginBuilderAttribute
        private int connectTimeoutMillis = 1000;

        @PluginBuilderAttribute
        private int readTimeoutMillis = 0;

        @PluginElement("Headers")
        private Header[] headers;

        @PluginElement("Properties")
        private Property[] properties;

        @PluginElement("SslConfiguration")
        private SslConfiguration sslConfiguration;

        @PluginBuilderAttribute
        private boolean verifyHostname = true;

        @Override
        public ElasticAppender build() {
            url = new StrSubstitutor(System.getProperties()).replace(url).toLowerCase();
            try {
                httpManager = new HttpManager(getConfiguration(),
                    getName(), new URL(url), new URL(url + "/_doc"), connectTimeoutMillis, readTimeoutMillis, headers, properties, sslConfiguration, verifyHostname);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            return new ElasticAppender(getName(), getLayout(), getFilter(), isIgnoreExceptions(), httpManager);
        }

        public String getUrl() {
            return url;
        }

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public int getReadTimeoutMillis() {
            return readTimeoutMillis;
        }

        public Header[] getHeaders() {
            return headers;
        }

        public Property[] getProperties() {
            return properties;
        }

        public SslConfiguration getSslConfiguration() {
            return sslConfiguration;
        }

        public boolean isVerifyHostname() {
            return verifyHostname;
        }

        public B setUrl(final String url) {
            this.url = url;
            return asBuilder();
        }

        public B setConnectTimeoutMillis(final int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
            return asBuilder();
        }

        public B setReadTimeoutMillis(final int readTimeoutMillis) {
            this.readTimeoutMillis = readTimeoutMillis;
            return asBuilder();
        }

        public B setHeaders(final Header[] headers) {
            this.headers = headers;
            return asBuilder();
        }

        public B setProperties(final Property[] properties) {
            this.properties = properties;
            return asBuilder();
        }

        public B setSslConfiguration(final SslConfiguration sslConfiguration) {
            this.sslConfiguration = sslConfiguration;
            return asBuilder();
        }

        public B setVerifyHostname(final boolean verifyHostname) {
            this.verifyHostname = verifyHostname;
            return asBuilder();
        }
    }

    /**
     * @return a builder for a ElasticAppender.
     */
    @PluginBuilderFactory
    public static <B extends Builder<B>> B newBuilder() {
        return new Builder<B>().asBuilder();
    }

    private final HttpManager manager;

    private ElasticAppender(final String name, final Layout<? extends Serializable> layout, final Filter filter,
                            final boolean ignoreExceptions, final HttpManager manager) {
        super(name, filter, layout, ignoreExceptions);
        Objects.requireNonNull(layout, "layout");
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void append(final LogEvent event) {
        try {
            manager.send(getLayout(), event);
        } catch (final Exception e) {
            error("Unable to send HTTP in appender [" + getName() + "]", event, e);
        }
    }

    @Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
        setStopping();
        boolean stopped = super.stop(timeout, timeUnit, false);
        stopped &= manager.stop(timeout, timeUnit);
        setStopped();
        return stopped;
    }

    @Override
    public String toString() {
        return "ElasticAppender{" +
            "name=" + getName() +
            ", state=" + getState() +
            '}';
    }
}
