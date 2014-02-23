package net.sandum.servlet;

import net.sandum.util.servlet.ServletOutputStreamWrapper;
import net.sandum.util.servlet.TransformedRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author     osa
 * @since      07-03-2011
 * @version    $Id: FormatConversionFilter.java 28820 2011-04-28 12:04:11Z osa $
 */
public class FormatConversionFilter implements Filter {
    private final static Logger log = LoggerFactory.getLogger(FormatConversionFilter.class);
    private SimpleFormatTransformer transformer;

    public final void init(final FilterConfig filterConfig) throws ServletException {
        String className = filterConfig.getInitParameter("transformer");
        try {
            Class<?> klass = Class.forName(className);
            transformer = (SimpleFormatTransformer)klass.newInstance();
        } catch (Exception ex) {
            throw new ServletException(className + ": invalid format transformer", ex);
        }

        transformer.init(new FormatTransformerConfig() {
            public String getInitParameter(String name) {
                return filterConfig.getInitParameter(name);
            }

            public URL getResource(String path) throws MalformedURLException {
                return filterConfig.getServletContext().getResource(path);
            }

            public Iterable<String> getInitParameterNames() {
                final Enumeration e = filterConfig.getInitParameterNames();
                return new Iterable<String>() {
                    public Iterator<String> iterator() {
                        return new Iterator<String>() {

                            public boolean hasNext() {
                                return e.hasMoreElements();
                            }

                            public String next() {
                                return (String)e.nextElement();
                            }

                            public void remove() {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }
                };
            }
        });
    }

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            tryFilter((HttpServletRequest) request, (HttpServletResponse) response, chain);
        } catch (Throwable err) {
            log.error("failed to serve " + ((HttpServletRequest) request).getRequestURL() + " [" + ((HttpServletRequest) request).getQueryString() + "]", err);
        }
    }

    public void destroy() {
    }

    private void tryFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        log.debug("HIT: " + request.getRequestURL() + " [" + request.getQueryString() + "]");

        RequestWrapper wrappedRequest = new RequestWrapper(request);
        ResponseWrapper wrappedResponse = new ResponseWrapper(response);

        try {
            log.debug(">>>> " + wrappedRequest);
            chain.doFilter(wrappedRequest, wrappedResponse);
            log.debug(" ==> " + wrappedRequest + " down-chain finished. Result status: " + wrappedResponse.status);

            postProcess(wrappedRequest, wrappedResponse);
            log.debug(" ==> " + wrappedRequest + " post-process finished");
        } finally {
            log.debug("<<<< " + wrappedRequest);
        }
    }

    private void postProcess(RequestWrapper request, ResponseWrapper response) throws IOException {
        String contentType = response.contentType;
        int contentLength = response.contentLength;

        log.debug(request.getRequestURI() + " served " + contentLength + " bytes of " + contentType);

        if (response.bo == null)
            return;

        byte content[] = response.bo.toByteArray();

        if (response.status == HttpServletResponse.SC_OK) {
            HttpServletResponse clientResponse = (HttpServletResponse) response.getResponse();

            ByteArrayInputStream is = new ByteArrayInputStream(content);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            transformer.transform(is, os);
            byte result[] = os.toByteArray();

            clientResponse.addHeader("X-Transformed-From", transformer.getSourceMimeType());
            clientResponse.setContentType(transformer.getTargetMimeType());
            clientResponse.setContentLength(result.length);
            clientResponse.getOutputStream().write(result);
        } else {
            HttpServletResponse clientResponse = (HttpServletResponse) response.getResponse();

            clientResponse.setContentType(contentType);
            clientResponse.setContentLength(contentLength);
            clientResponse.getOutputStream().write(content);
        }
    }
    private final static Pattern MAGIC_FORMAT_TAILPATTERN = Pattern.compile("(.*)\\.([a-zA-Z0-9_]+)_([a-zA-Z0-9]+)");

    static TransformedRequest parseRequestPath(String path) {
        Matcher m = MAGIC_FORMAT_TAILPATTERN.matcher(path);
        if (!m.matches()) {
            log.error(path + ": not recognized");
            return null;
        }

        final String src = m.group(1) + "." + m.group(2);

        String suffix = "." + m.group(3);
        log.info(path + "': fetching " + src + ", converting to '" + suffix + "'");

        return new TransformedRequest() {
            public String getSourcePath() {
                return src;
            }
        };
    }

    @SuppressWarnings("deprecation")
    private static class RequestWrapper extends HttpServletRequestWrapper {
        private final String pathInfo;
        private final String requestUri;

        private RequestWrapper(HttpServletRequest request) {
            super(request);

            String contextPath = request.getContextPath();
            String servletPath = request.getServletPath();
            String path = request.getPathInfo();
            String _uri = request.getRequestURI(); // = cpath + spath + path

            log.debug("RequestWrapper(requestURI=\"" + _uri + "\" / contextPath=\"" + contextPath + "\" / servletPath=\"" + servletPath + "\" / pathInfo=\"" + path + "\")");

            if (path == null) {
                // When serving static resources from the default servlet, getPathInfo() returns null and the
                // whole path is in getServletPath(). Weird but true. Google 'getPathInfo returns null' if you
                // don't believe me.
                TransformedRequest imageRequest = parseRequestPath(servletPath);
                requestUri = contextPath + imageRequest.getSourcePath();
                pathInfo = null;
            } else {
                TransformedRequest imageRequest = parseRequestPath(path);
                requestUri = contextPath + servletPath + imageRequest.getSourcePath();
                pathInfo = imageRequest.getSourcePath();
            }
        }

        @Override
        public String getRequestURI() {
            return requestUri;
        }

        @Override
        public String getPathInfo() {
            return pathInfo;
        }
    }

    @SuppressWarnings("deprecation")
    private static class ResponseWrapper extends HttpServletResponseWrapper {
        private String contentType;
        private int contentLength;
        private int status = 0;
        private ServletOutputStream stream;
        private PrintWriter writer;
        private ByteArrayOutputStream bo;

        private ResponseWrapper(HttpServletResponse response) {
            super(response);
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
            if (log.isDebugEnabled())
                log.debug("setContentLength(" + length + ")");
            this.contentLength = length;
            // ((HttpServletResponse) getResponse()).setContentLength(length);
        }

        @Override
        public void setContentType(String type) {
            if (log.isDebugEnabled())
                log.debug("setContentType(\"" + type + "\")");
            this.contentType = type;
            // ((HttpServletResponse) getResponse()).setContentType(type);
        }

        @Override
        public void addHeader(String name, String value) {
            if (log.isDebugEnabled())
                log.debug("addHeader(\"" + name + "\", \"" + value + "\")");
            if ("Content-Type".equalsIgnoreCase(name))
                this.contentType = value;
            else if ("Content-Length".equalsIgnoreCase(name))
                this.contentLength = Integer.valueOf(value);
            else
                super.addHeader(name, value);
        }

        @Override
        public void setHeader(String name, String value) {
            if (log.isDebugEnabled())
                log.debug("setHeader(\"" + name + "\", \"" + value + "\")");
            if ("Content-Type".equalsIgnoreCase(name))
                this.contentType = value;
            else if ("Content-Length".equalsIgnoreCase(name))
                this.contentLength = Integer.valueOf(value);
            else
                super.setHeader(name, value);
        }

        @Override
        public void setStatus(int sc) {
            if (log.isDebugEnabled())
                log.debug("setStatus(" + sc + ")");
            this.status = sc;
            super.setStatus(sc);
        }

        @Override
        public void setStatus(int sc, String sm) {
            if (log.isDebugEnabled())
                log.debug("setStatus(" + sc + ", \"" + sm + "\")");
            this.status = sc;
            super.setStatus(sc, sm);
        }
    }
}
