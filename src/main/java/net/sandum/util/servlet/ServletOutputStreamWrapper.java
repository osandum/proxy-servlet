package net.sandum.util.servlet;

import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.ServletOutputStream;

/**
 * @author     osa
 * @since      19-11-2009
 * @version    $Id: ServletOutputStreamWrapper.java 25567 2010-08-13 15:13:16Z osa $
 */
public class ServletOutputStreamWrapper extends ServletOutputStream {
    private final OutputStream out;
    private boolean closed;

    public ServletOutputStreamWrapper(OutputStream out) {
        this.out = out;

        closed = false;
    }

    @Override
    public void close() throws IOException {
        if (closed)
            throw new IOException("output stream has already been closed");
        out.flush();
        out.close();

        closed = true;
    }

    @Override
    public void flush() throws IOException {
        if (closed)
            throw new IOException("output stream has already been closed");
        out.flush();
    }

    @Override
    public void write(int b) throws IOException {
        if (closed)
            throw new IOException("output stream has already been closed");
        out.write((byte) b);
    }

    @Override
    public void write(byte b[]) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte b[], int off, int len) throws IOException {
        if (closed)
            throw new IOException("output stream has already been closed");
        out.write(b, off, len);
    }
}
