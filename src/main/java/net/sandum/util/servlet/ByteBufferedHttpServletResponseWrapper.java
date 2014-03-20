
package net.sandum.util.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author      osa
 * @since       01-03-2014
 * @version     $Id: ByteBufferedHttpServletResponseWrapper.java -1 01-03-2014 16:45:33 osa $
 */
public class ByteBufferedHttpServletResponseWrapper extends HttpServletResponseWrapper
        implements TransformingResponse {
    private final static Logger LOG = LoggerFactory.getLogger(ByteBufferedHttpServletResponseWrapper.class);

    private String downstreamContentType;
    private int downstreamContentLength;
    private int downstreamStatus = 0;
    private ServletOutputStream stream;
    private PrintWriter writer;
    private ByteArrayOutputStream bo;

    public ByteBufferedHttpServletResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    public byte[] getDownstreamContent() {
        if (bo == null)
            return null;

        return bo.toByteArray();
    }

    public String getDownstreamContentType() {
        return downstreamContentType;
    }

    public int getDownstreamContentLength() {
        return downstreamContentLength;
    }

    public int getDownstreamStatus() {
        return downstreamStatus;
    }

    public HttpServletResponse getUpstreamResponse() {
        return (HttpServletResponse)getResponse();
    }

    private void createOutputStream() throws IOException {
        bo = new ByteArrayOutputStream();
        stream = new ServletOutputStreamWrapper(bo);
    }

    @Override
    public void flushBuffer() throws IOException {
        stream.flush();
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null)
            throw new IllegalStateException("getOutputStream() has already been called!");

        if (stream == null)
            createOutputStream();

        return stream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer != null)
            return writer;

        if (stream != null)
            throw new IllegalStateException("getOutputStream() has already been called!");

        createOutputStream();
        writer = new PrintWriter(new OutputStreamWriter(stream, "UTF-8"));

        return writer;
    }

    @Override
    public void setContentLength(int length) {
        if (LOG.isDebugEnabled())
            LOG.debug("setContentLength(" + length + ")");
        this.downstreamContentLength = length;
        // ((HttpServletResponse) getResponse()).setContentLength(length);
    }

    @Override
    public void setContentType(String type) {
        if (LOG.isDebugEnabled())
            LOG.debug("setContentType(\"" + type + "\")");
        this.downstreamContentType = type;
        // ((HttpServletResponse) getResponse()).setContentType(type);
    }

    @Override
    public void addHeader(String name, String value) {
        if (LOG.isDebugEnabled())
            LOG.debug("addHeader(\"" + name + "\", \"" + value + "\")");
        if ("Content-Type".equalsIgnoreCase(name))
            this.downstreamContentType = value;
        else if ("Content-Length".equalsIgnoreCase(name))
            this.downstreamContentLength = Integer.valueOf(value);
        else
            super.addHeader(name, value);
    }

    @Override
    public void setHeader(String name, String value) {
        if (LOG.isDebugEnabled())
            LOG.debug("setHeader(\"" + name + "\", \"" + value + "\")");
        if ("Content-Type".equalsIgnoreCase(name))
            this.downstreamContentType = value;
        else if ("Content-Length".equalsIgnoreCase(name))
            this.downstreamContentLength = Integer.valueOf(value);
        else
            super.setHeader(name, value);
    }

    @Override
    public void setStatus(int sc) {
        if (LOG.isDebugEnabled())
            LOG.debug("setStatus(" + sc + ")");
        this.downstreamStatus = sc;
        super.setStatus(sc);
    }

    @Override
    public void setStatus(int sc, String sm) {
        if (LOG.isDebugEnabled())
            LOG.debug("setStatus(" + sc + ", \"" + sm + "\")");
        this.downstreamStatus = sc;
        super.setStatus(sc, sm);
    }
}
