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
package tech.raaf.logelastic.log4j.layout;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.*;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Plugin(name = "ElasticLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public final class ElasticLayout extends AbstractJacksonLayout {

    private static final String DEFAULT_FOOTER = "]";

    private static final String DEFAULT_HEADER = "[";

    static final String CONTENT_TYPE = "application/json";

    public static class Builder<B extends Builder<B>> extends AbstractJacksonLayout.Builder<B>
            implements org.apache.logging.log4j.core.util.Builder<ElasticLayout> {

        @PluginBuilderAttribute
        private boolean locationInfo;

        @PluginBuilderAttribute
        private boolean properties;

        @PluginBuilderAttribute
        private boolean propertiesAsList;

        @PluginBuilderAttribute
        private boolean includeStacktrace = true;

        public Builder() {
            super();
            setCharset(StandardCharsets.UTF_8);
        }

        @Override
        public ElasticLayout build() {
            final boolean encodeThreadContextAsList = properties && propertiesAsList;
            final String headerPattern = toStringOrNull(getHeader());
            final String footerPattern = toStringOrNull(getFooter());
            return new ElasticLayout(getConfiguration(), locationInfo, properties, encodeThreadContextAsList, isComplete(),
                    isCompact(), getEventEol(), headerPattern, footerPattern, getCharset(), includeStacktrace);
        }

        private String toStringOrNull(final byte[] header) {
            return header == null ? null : new String(header, Charset.defaultCharset());
        }

        public boolean isLocationInfo() {
            return locationInfo;
        }

        public boolean isProperties() {
            return properties;
        }

        public boolean isPropertiesAsList() {
            return propertiesAsList;
        }

        /**
         * If "true", includes the stacktrace of any Throwable in the generated JSON, defaults to "true".
         * @return If "true", includes the stacktrace of any Throwable in the generated JSON, defaults to "true".
         */
        public boolean isIncludeStacktrace() {
            return includeStacktrace;
        }

        public B setLocationInfo(boolean locationInfo) {
            this.locationInfo = locationInfo;
            return asBuilder();
        }

        public B setProperties(boolean properties) {
            this.properties = properties;
            return asBuilder();
        }

        public B setPropertiesAsList(boolean propertiesAsList) {
            this.propertiesAsList = propertiesAsList;
            return asBuilder();
        }

        /**
         * If "true", includes the stacktrace of any Throwable in the generated JSON, defaults to "true".
         * @param includeStacktrace If "true", includes the stacktrace of any Throwable in the generated JSON, defaults to "true".
         * @return this builder
         */
        public B setIncludeStacktrace(boolean includeStacktrace) {
            this.includeStacktrace = includeStacktrace;
            return asBuilder();
        }
    }

    protected ElasticLayout(final Configuration config, final boolean locationInfo, final boolean properties,
                            final boolean encodeThreadContextAsList,
                            final boolean complete, final boolean compact, final boolean eventEol, final String headerPattern,
                            final String footerPattern, final Charset charset, final boolean includeStacktrace) {
        super(config, new JacksonFactory.JSON(encodeThreadContextAsList, includeStacktrace).newWriter(
                    locationInfo, properties, compact),
                charset, compact, complete, eventEol,
                PatternLayout.newSerializerBuilder().setConfiguration(config).setPattern(headerPattern).setDefaultPattern(DEFAULT_HEADER).build(),
                PatternLayout.newSerializerBuilder().setConfiguration(config).setPattern(footerPattern).setDefaultPattern(DEFAULT_FOOTER).build());
    }

    /**
     * Returns appropriate JSON header.
     *
     * @return a byte array containing the header, opening the JSON array.
     */
    @Override
    public byte[] getHeader() {
        if (!this.complete) {
            return null;
        }
        final StringBuilder buf = new StringBuilder();
        final String str = serializeToString(getHeaderSerializer());
        if (str != null) {
            buf.append(str);
        }
        buf.append(this.eol);
        return getBytes(buf.toString());
    }

    /**
     * Returns appropriate JSON footer.
     *
     * @return a byte array containing the footer, closing the JSON array.
     */
    @Override
    public byte[] getFooter() {
        if (!this.complete) {
            return null;
        }
        final StringBuilder buf = new StringBuilder();
        buf.append(this.eol);
        final String str = serializeToString(getFooterSerializer());
        if (str != null) {
            buf.append(str);
        }
        buf.append(this.eol);
        return getBytes(buf.toString());
    }

    @Override
    public Map<String, String> getContentFormat() {
        final Map<String, String> result = new HashMap<>();
        result.put("version", "2.0");
        return result;
    }

    @Override
    /**
     * @return The content type.
     */
    public String getContentType() {
        return CONTENT_TYPE + "; charset=" + this.getCharset();
    }

    /**
     * Creates a JSON Layout.
     * @param config
     *           The plugin configuration.
     * @param locationInfo
     *            If "true", includes the location information in the generated JSON.
     * @param properties
     *            If "true", includes the thread context map in the generated JSON.
     * @param propertiesAsList
     *            If true, the thread context map is included as a list of map entry objects, where each entry has
     *            a "key" attribute (whose value is the key) and a "value" attribute (whose value is the value).
     *            Defaults to false, in which case the thread context map is included as a simple map of key-value
     *            pairs.
     * @param complete
     *            If "true", includes the JSON header and footer, and comma between records.
     * @param compact
     *            If "true", does not use end-of-lines and indentation, defaults to "false".
     * @param eventEol
     *            If "true", forces an EOL after each log event (even if compact is "true"), defaults to "false". This
     *            allows one even per line, even in compact mode.
     * @param headerPattern
     *            The header pattern, defaults to {@code "["} if null.
     * @param footerPattern
     *            The header pattern, defaults to {@code "]"} if null.
     * @param charset
     *            The character set to use, if {@code null}, uses "UTF-8".
     * @param includeStacktrace
     *            If "true", includes the stacktrace of any Throwable in the generated JSON, defaults to "true".
     * @return A JSON Layout.
     */
    @Deprecated
    public static ElasticLayout createLayout(
            // @formatter:off
            @PluginConfiguration final Configuration config,
            @PluginAttribute(value = "locationInfo") final boolean locationInfo,
            @PluginAttribute(value = "properties") final boolean properties,
            @PluginAttribute(value = "propertiesAsList") final boolean propertiesAsList,
            @PluginAttribute(value = "complete") final boolean complete,
            @PluginAttribute(value = "compact") final boolean compact,
            @PluginAttribute(value = "eventEol") final boolean eventEol,
            @PluginAttribute(value = "header", defaultString = DEFAULT_HEADER) final String headerPattern,
            @PluginAttribute(value = "footer", defaultString = DEFAULT_FOOTER) final String footerPattern,
            @PluginAttribute(value = "charset", defaultString = "UTF-8") final Charset charset,
            @PluginAttribute(value = "includeStacktrace", defaultBoolean = true) final boolean includeStacktrace
            // @formatter:on
    ) {
        final boolean encodeThreadContextAsList = properties && propertiesAsList;
        return new ElasticLayout(config, locationInfo, properties, encodeThreadContextAsList, complete, compact, eventEol,
                headerPattern, footerPattern, charset, includeStacktrace);
    }

    @PluginBuilderFactory
    public static <B extends Builder<B>> B newBuilder() {
        return new Builder<B>().asBuilder();
    }

    /**
     * Creates a JSON Layout using the default settings. Useful for testing.
     *
     * @return A JSON Layout.
     */
    public static ElasticLayout createDefaultLayout() {
        return new ElasticLayout(new DefaultConfiguration(), false, false, false, false, false, false,
                DEFAULT_HEADER, DEFAULT_FOOTER, StandardCharsets.UTF_8, true);
    }

    @Override
    public void toSerializable(final LogEvent event, final Writer writer) throws IOException {
        if (complete && eventCount > 0) {
            writer.append(", ");
        }
        super.toSerializable(event, writer);
    }
}
