package net.sandum.servlet;

import net.sandum.util.servlet.RequestHandlingException;
import net.sandum.util.servlet.RequestProxy;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author     osa
 * @since      26-08-2011
 * @version    $Id: SimpleProxyServlet.java 32190 2012-01-02 12:24:29Z osa $
 */
public class ProxyServlet extends HttpServlet {
    private final static Logger log = LoggerFactory.getLogger(ProxyServlet.class);

    // private final static Pattern PATH_PATTERN = Pattern.compile("/(https?):?/([^/]+)(:[0-9]+)?(?:/([^?]*))?(\\?.*)?");
    // private final static String TARGET_URL = "$1://$2$3/$4$5";

    private Pattern stripRequestHeadersPattern;
    private Pattern stripResponseHeadersPattern;
    private PatternReplacements pathPatterns;
    private boolean followRedirects;
    private int maxAge = -1;

    @Override
    public void init() throws ServletException {
        String s = getInitParameter("strip-response-header");
        if (!StringUtils.isEmpty(s))
            stripResponseHeadersPattern = Pattern.compile(s, Pattern.CASE_INSENSITIVE);

        s = getInitParameter("strip-request-header");
        if (!StringUtils.isEmpty(s))
            stripRequestHeadersPattern = Pattern.compile(s, Pattern.CASE_INSENSITIVE);

        s = getInitParameter("follow-redirects");
        if (!StringUtils.isEmpty(s))
            followRedirects = Boolean.valueOf(s);

        s = getInitParameter("max-age");
        if (!StringUtils.isEmpty(s))
            maxAge = Integer.valueOf(s);

        pathPatterns = new PatternReplacements();
        Enumeration pns = getInitParameterNames();
        while (pns.hasMoreElements()) {
            String pn = (String)pns.nextElement();
            if (!pn.startsWith("path-pattern"))
                continue;

            String parameterSuffix = pn.substring("path-pattern".length());

            String pattern = getInitParameter("path-pattern" + parameterSuffix);
            String target = getInitParameter("target-url" + parameterSuffix);
            addProxyPatternTarget(pattern, target);
        }
    }

    protected void addProxyPatternTarget(String regex, String target) {
        pathPatterns.addPatternReplacement(regex, target);
        log.info(getServletName() + " proxy " + regex + " -> " + target);
    }

    private final RequestProxy proxy = new RequestProxy() {
        @Override
        public URL getTargetUrl(HttpServletRequest request) throws RequestHandlingException {
            return getProxyTargetUrl(request);
        }

        @Override
        public void addRequestProperties(URLConnection connection, HttpServletRequest request) {
            addProxyRequestProperties(connection, request);
        }

        @Override
        protected boolean ignoreRequestHeader(String hdr) {
            return stripProxyRequestHeader(hdr) || super.ignoreRequestHeader(hdr);
        }

        @Override
        protected boolean ignoreResponseHeader(String hdr) {
            return stripProxyResponseHeader(hdr) || super.ignoreResponseHeader(hdr);
        }

        @Override
        protected boolean getFollowRedirects() {
            return getProxyFollowRedirects();
        }

        @Override
        protected int getMaxAge() {
            return getProxyMaxAge();
        }

        @Override
        protected void addResponseHeaders(HttpServletResponse response) {
            addProxyResponseHeaders(response);
        }
    };

    protected String getReplacement(HttpServletRequest request) throws RequestHandlingException {
        String pathInfo = request.getPathInfo();
        if (StringUtils.isEmpty(pathInfo))
            pathInfo = "";

        String query = request.getQueryString();
        if (!StringUtils.isEmpty(query))
            pathInfo += "?" + query;

        return pathPatterns.match(pathInfo);
    }

    protected URL getProxyTargetUrl(HttpServletRequest request) throws RequestHandlingException {
        String resUrl = getReplacement(request);
        if (resUrl == null)
            throw RequestHandlingException.notFoundError("\"" + request.getPathInfo() + "\": unrecognized proxy target URL");

        if (resUrl.startsWith("/"))
            throw RequestHandlingException.forward(resUrl);

        try {
            return new URL(resUrl);
        }
        catch (MalformedURLException ex) {
            throw RequestHandlingException.internalServerError("\"" + resUrl + "\"" + " is not a valid URL. " + ex.getMessage());
        }
    }

    protected void addProxyRequestProperties(URLConnection connection, HttpServletRequest request) {
        connection.setRequestProperty("Via", "1.1 (" + getServletName() + ")");
    }

    protected boolean getProxyFollowRedirects() {
        return followRedirects;
    }

    protected int getProxyMaxAge() {
        return maxAge;
    }

    protected void addProxyResponseHeaders(HttpServletResponse response) {
    }

    protected boolean stripProxyRequestHeader(String hdr) {
        return stripRequestHeadersPattern != null && stripRequestHeadersPattern.matcher(hdr).matches();
    }

    protected boolean stripProxyResponseHeader(String hdr) {
        return stripResponseHeadersPattern != null && stripResponseHeadersPattern.matcher(hdr).matches();
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        proxy.service(request, response);
    }
}