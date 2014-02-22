package net.sandum.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author     osa
 * @since      07-03-2011
 * @version    $Id: SimpleFormatTransformer.java 28205 2011-03-09 09:07:39Z osa $
 */
public interface SimpleFormatTransformer {
    void init(FormatTransformerConfig cfg);

    String getSourceMimeType();
    String getTargetMimeType();

    void transform(InputStream input, OutputStream output) throws IOException;
}
