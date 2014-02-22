package net.sandum.util.servlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Experimental HTTP request proxy. Inspired by the proxy servlet found in Jetty.
 * @author     osa
 * @since      24-11-2010
 */
public abstract class RequestProxy {
    private final static Logger LOG = LoggerFactory.getLogger(RequestProxy.class);

    private final static HashSet<String> IGNORE_HEADER =
            new HashSet<String>(Arrays.asList(new String[]{
                "proxy-connection",
                "connection",
                "keep-alive",
                "transfer-encoding",
                "accept-encoding",
                "te",
                "trailer",
                "proxy-authorization",
                "proxy-authenticate",
                "upgrade",
                "referer",
                "cookie",
                "set-cookie"
            }));
    private final static HashSet<String> CACHE_RESPONSE_HEADER =
            new HashSet<String>(Arrays.asList(new String[]{
                "cache-control",
                "expires"
            }));

    public abstract URL getTargetUrl(HttpServletRequest request) throws RequestHandlingException;

    // Override as necessary
    protected void addRequestProperty(URLConnection connection, String name, String value) {
        connection.addRequestProperty(name, value);
        LOG.debug("[request ->] " + name + ": " + value);
    }

    // Override as necessary
    protected void addRequestProperties(URLConnection connection, HttpServletRequest request) {
    }

    // Override as necessary
    protected void addResponseHeader(HttpServletResponse response, String name, String value) {
        response.addHeader(name, value);
        LOG.debug("[<- response] " + name + ": " + value);
    }

    // Override as necessary
    protected void addResponseHeaders(HttpServletResponse response) {
    }

    public String getCookieDomain(HttpServletRequest request) {
        String host = request.getServerName();
        if (host.indexOf('.') != host.lastIndexOf('.'))
            // host contains at least two '.'s. Strip away before the first one:
            host = host.substring(host.indexOf('.'));

        return host;
    }

    public String getCookiePath(HttpServletRequest request) {
        return request.getServletPath();
    }

    public static void service(HttpServletRequest request, HttpServletResponse response, final URL target) throws IOException, ServletException {
        new RequestProxy() {
            @Override
            public URL getTargetUrl(HttpServletRequest request) {
                return target;
            }
        }.service(request, response);
    }

    protected boolean ignoreRequestHeader(String hdr) {
        return IGNORE_HEADER.contains(hdr);
    }

    protected boolean ignoreResponseHeader(String hdr) {
        return IGNORE_HEADER.contains(hdr)
                || getMaxAge() > 0 && CACHE_RESPONSE_HEADER.contains(hdr);
    }

    protected boolean getFollowRedirects() {
        return false;
    }

    /* (non-Javadoc)
     * @see javax.servlet.Servlet#service(javax.servlet.ServletRequest, javax.servlet.ServletResponse)
     */
    @SuppressWarnings("deprecation")
    public void service(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

        try {
            URLConnection connection = proxyRequest(request, response);
            proxyResponse(request, connection, response);
        } catch (RequestHandlingException ex) {
            ex.go(request, response);
        }
    }

    private URLConnection proxyRequest(HttpServletRequest request, HttpServletResponse response) throws RequestHandlingException, IOException {
        String method = request.getMethod();
        LOG.debug(method + " request: " + request.getRequestURI());
        URL url = getTargetUrl(request);
            LOG.debug("URL=" + url);

        if ("CONNECT".equalsIgnoreCase(method)) {
            connectSocket(request, response, url);
            return null;
        }

        URLConnection connection = url.openConnection();
        connection.setAllowUserInteraction(false);

        // Set method
        if (connection instanceof HttpURLConnection) {
            HttpURLConnection http = (HttpURLConnection) connection;
            http.setRequestMethod(method);
      //      http.setRequestProperty("Host", "jerry:8080");
            http.setInstanceFollowRedirects(getFollowRedirects());

            http.setRequestProperty("X-Forwarded-Context-Path", RequestHelper.rootPath(request) + request.getContextPath());
            http.setRequestProperty("X-Forwarded-Servlet-Path", request.getServletPath());
        }

        // check connection header
        String connectionHdr = request.getHeader("Connection");
        if (connectionHdr != null) {
            connectionHdr = connectionHdr.toLowerCase();
            if (connectionHdr.equals("keep-alive") || connectionHdr.equals("close"))
                connectionHdr = null;
        }

        // copy headers
        boolean xForwardedFor = false;
        boolean hasContentType = false;
        Enumeration enm = request.getHeaderNames();
        while (enm.hasMoreElements()) {
            // TODO could be better than this!
            String hdr = (String) enm.nextElement();
            String lhdr = hdr.toLowerCase();

            if ("cookie".equals(lhdr)) {
                Enumeration headers = request.getHeaders(hdr);
                while (headers.hasMoreElements()) {
                    String val = (String) headers.nextElement();
                    proxyCookies(connection, request, val);
                }
                continue;
            }
            if (ignoreRequestHeader(lhdr))
                continue;
            if (connectionHdr != null && connectionHdr.indexOf(lhdr) >= 0)
                continue;

            if ("host".equals(lhdr)) {
                String host = url.getAuthority();
            //  addRequestProperty(connection, hdr, host);
                LOG.debug(hdr + ": " + host + " - ignored");
                continue;
            }

            if ("content-type".equals(lhdr))
                hasContentType = true;

            Enumeration vals = request.getHeaders(hdr);
            while (vals.hasMoreElements()) {
                String val = (String) vals.nextElement();
                if (val != null) {
                    addRequestProperty(connection, hdr, val);
                    xForwardedFor |= "X-Forwarded-For".equalsIgnoreCase(hdr);
                }
            }
        }

        // Proxy headers
        addRequestProperties(connection, request);
        if (!xForwardedFor)
            addRequestProperty(connection, "X-Forwarded-For", request.getRemoteAddr());

        // a little bit of cache control
        String cache_control = request.getHeader("Cache-Control");
        if (cache_control != null
                && (cache_control.indexOf("no-cache") >= 0
                || cache_control.indexOf("no-store") >= 0))
            connection.setUseCaches(false);

        // customize Connection
        try {
            connection.setDoInput(true);

            // do input thang!
            InputStream in = request.getInputStream();
            if (hasContentType && ("POST".equals(method) || "PUT".equals(method))) {
                connection.setDoOutput(true);
                IOUtils.copy(in, connection.getOutputStream());
            }

            // Connect
            connection.connect();
        } finally {
            copyResponseHeaders(request, connection, response);
        }

        return connection;
    }

    private void proxyResponse(HttpServletRequest request, URLConnection connection, HttpServletResponse response) throws IOException {
        InputStream proxy_in = null;

        // handler status codes etc.
        int code = 500;
        String msg = "Error";
        if (connection instanceof HttpURLConnection) {
            HttpURLConnection http = (HttpURLConnection) connection;
            proxy_in = http.getErrorStream();
            code = http.getResponseCode();
            msg = http.getResponseMessage();
            LOG.debug("response = " + http.getResponseCode());
        }

        if (proxy_in == null)
            try {
                proxy_in = connection.getInputStream();
            } catch (FileNotFoundException ex) {
                LOG.warn(code + ": " + ex.getMessage());
            } catch (Exception ex) {
                LOG.info("stream", ex);
            }

        // Handle
        if (proxy_in == null) {
            response.sendError(code, msg);
        } else {
            response.setStatus(code);

            long t1 = System.currentTimeMillis();

            int byteCount = IOUtils.copy(proxy_in, response.getOutputStream());

            long t2 = System.currentTimeMillis();
            if (t2 - t1 > 200)
                LOG.warn(connection.getURL() + ": slow URL - " + byteCount + " byte(s) proxied in " + (t2 - t1) + "ms");
            else if (LOG.isDebugEnabled())
                LOG.debug(connection.getURL() + ": " + byteCount + " byte(s) proxied in " + (t2 - t1) + "ms");

            String s = (String)request.getAttribute("_response.content-length");
            if (s != null) {
                int contentLength = Integer.parseInt(s);
                if (contentLength != byteCount)
                    LOG.error(connection.getURL() + ": wrong Content-Length: " + contentLength + " - " + byteCount + " byte(s) proxied");
            }
        }
    }

    private void copyResponseHeaders(HttpServletRequest request, URLConnection connection, HttpServletResponse response) {
        //  response.setHeader("Date", null);
        //  response.setHeader("Server", null);

        // set response headers
        int h = 0;
        String hdr = connection.getHeaderFieldKey(h);
        String val = connection.getHeaderField(h);
        while (hdr != null || val != null) {
            String lhdr = hdr != null ? hdr.toLowerCase() : null;
            if (hdr != null && val != null) {
                if ("set-cookie".equals(lhdr)) {
                    proxySetCookie(request, connection, response, hdr, val);
                } else if (ignoreResponseHeader(lhdr)) {
                    LOG.debug("res " + hdr + ": " + val + " - trapped by proxy");
                } else {
                    addResponseHeader(response, hdr, val);
                }

                request.setAttribute("_response." + lhdr, val);
            }
            h++;
            hdr = connection.getHeaderFieldKey(h);
            val = connection.getHeaderField(h);
        }

        if (getMaxAge() > 0) {
            response.setHeader("Cache-Control", "public, max-age=" + getMaxAge());
            response.setDateHeader("Expires", System.currentTimeMillis() + getMaxAge() * 1000L);
        }

        addResponseHeaders(response);
    }

    /* ------------------------------------------------------------ */
    protected void connectSocket(HttpServletRequest request, HttpServletResponse response, URL url) throws IOException {
        InetAddress host = InetAddress.getByName(url.getHost());
        int port = url.getPort();

        final InputStream in = request.getInputStream();
        final OutputStream out = response.getOutputStream();

        final Socket socket = new Socket(host, port);
        LOG.info("Socket: " + socket);

        response.setStatus(200);
        response.setHeader("Connection", "close");
        response.flushBuffer();

        new Thread() {
            @Override
            public void run() {
                try {
                    LOG.debug("copy socket -> response");
                    IOUtils.copy(socket.getInputStream(), out);
                } catch (Exception ex) {
                    LOG.info("proxy failure", ex);
                }
            }
        }.start();

        LOG.debug("copy request -> socket");
        IOUtils.copy(in, socket.getOutputStream());
    }

    protected void proxyCookies(URLConnection connection, HttpServletRequest request, String val) {
        for (String cookie : val.split(";\\s*"))
            LOG.debug("Cookie: " + cookie + " ignored");
    }

    protected void proxySetCookie(HttpServletRequest request, URLConnection connection, HttpServletResponse response, String hdr, String val) {
        for (HttpCookie cookie : HttpCookie.parse(val)) {
            Cookie c = new Cookie("proxy-" + cookie.getName(), cookie.getValue());
            c.setComment("(proxied from " + connection.getURL().getHost() + ")");
            c.setDomain(getCookieDomain(request));
            c.setPath(getCookiePath(request));
            c.setMaxAge(getCookieMaxAge());
            c.setVersion(cookie.getVersion());

            LOG.debug("proxying set-cookie: " + c.getName() + "=" + c.getValue());
            response.addCookie(c);
        }
    }

    private int getCookieMaxAge() {
        return 3600;
    }

    protected int getMaxAge() {
        return -1;
    }
}