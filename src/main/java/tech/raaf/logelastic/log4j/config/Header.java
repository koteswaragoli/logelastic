package tech.raaf.logelastic.log4j.config;

import java.util.Objects;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.config.plugins.PluginValue;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.Strings;

/**
 * Represents a key/value pair in the configuration.
 */
@Plugin(name = "header", category = Node.CATEGORY, printObject = true)
public final class Header {

    private static final Logger LOGGER = StatusLogger.getLogger();

    private final String name;
    private final String value;
    private final boolean valueNeedsLookup;

    private Header(final String name, final String value) {
        this.name = name;
        this.value = value;
        this.valueNeedsLookup = value != null && value.contains("${");
    }

    /**
     * Returns the header name.
     * @return the header name.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the header value.
     * @return the value of the header.
     */
    public String getValue() {
        return Objects.toString(value, Strings.EMPTY);
    }

    /**
     * Returns {@code true} if the value contains a substitutable header that requires a lookup to be resolved.
     * @return {@code true} if the value contains {@code "${"}, {@code false} otherwise
     */
    public boolean isValueNeedsLookup() {
        return valueNeedsLookup;
    }

    /**
     * Creates a Header.
     *
     * @param name The key.
     * @param value The value.
     * @return A Header.
     */
    @PluginFactory
    public static Header createHeader(
            @PluginAttribute("name") final String name,
            @PluginValue("value") final String value) {
        if (name == null) {
            LOGGER.error("Header name cannot be null");
        }
        return new Header(name, value);
    }

    @Override
    public String toString() {
        return name + '=' + getValue();
    }
}
