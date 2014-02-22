package net.sandum.servlet;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author osa
 */
public interface FormatTransformerConfig {
    URL getResource(String path) throws MalformedURLException;

    String getInitParameter(String string);
}
