package net.sandum.servlet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import net.sandum.util.servlet.ByteBufferedHttpServletResponseWrapper;
import net.sandum.util.servlet.TransformedRequest;
import net.sandum.util.servlet.TransformingResponse;
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
        TransformingResponse wrappedResponse = wrap(response);

        try {
            log.debug(">>>> " + wrappedRequest);
            chain.doFilter(wrappedRequest, wrappedResponse);
            log.debug(" ==> " + wrappedRequest + " down-chain finished. Result status: " + wrappedResponse.getDownstreamStatus());

            postProcess(wrappedResponse);
            log.debug(" ==> " + wrappedRequest + " post-process finished");
        } catch (RuntimeException ex) {
            log.error("     " + wrappedRequest + " failed", ex);
            throw new ServletException(ex);
        } finally {
            log.debug("<<<< " + wrappedRequest);
        }
    }

    protected TransformingResponse wrap(HttpServletResponse response) {
        return new ByteBufferedHttpServletResponseWrapper(response);
    }

    private void postProcess(TransformingResponse response) throws IOException, ServletException {
        byte downstreamContent[] = response.getDownstreamContent();

        if (downstreamContent == null)
            return;

        HttpServletResponse upstreamResponse = response.getUpstreamResponse();
        if (response.getDownstreamStatus() == HttpServletResponse.SC_OK) {
            ByteArrayInputStream is = new ByteArrayInputStream(downstreamContent);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            transformer.transform(is, os);
            byte result[] = os.toByteArray();

            upstreamResponse.addHeader("X-Transformed-From", transformer.getSourceMimeType());
            upstreamResponse.setContentType(transformer.getTargetMimeType());
            upstreamResponse.setContentLength(result.length);
            upstreamResponse.getOutputStream().write(result);
        } else {
            upstreamResponse.setContentType(response.getDownstreamContentType());
            upstreamResponse.setContentLength(response.getDownstreamContentLength());
            upstreamResponse.getOutputStream().write(downstreamContent);
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
}
