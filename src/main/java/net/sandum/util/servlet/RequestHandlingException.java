package net.sandum.util.servlet;

import java.io.IOException;
import java.net.URL;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exception class that encapsulates an "exceptional" way to respond to a web request, e.g.
 * by reporting an error, by redirecting the request, or by forwarding it to a different servlet.
 * <p>The catch block should invoke the "go"-method to perform the response.
 *
 * @version    $Id: RequestHandlingException.java 32636 2012-02-06 12:02:03Z osa $
 */
public abstract class RequestHandlingException
        extends ServletException implements RequestHandler {
    private static Logger LOG = LoggerFactory.getLogger(RequestHandlingException.class);

    private boolean quiet;

    public RequestHandlingException(String message) {
        super(message);
    }

    public RequestHandlingException(String message, Throwable cause) {
        super(message, cause);
    }

    public static RequestHandlingException forward(final String uri) {
        return new RequestHandlingException("forward to " + uri) {
            @Override
            public void go(HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                forward(request, response, uri);
            }
        };
    }

    public static RequestHandlingException notFoundError() {
        return notFoundError("resource not found");
    }

    public static RequestHandlingException notFoundError(String message) {
        return error(message, HttpServletResponse.SC_NOT_FOUND);
    }

    public static RequestHandlingException forbiddenError(String message) {
        return error(message, HttpServletResponse.SC_FORBIDDEN);
    }

    public static RequestHandlingException internalServerError(String message) {
        return error(message, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    public static RequestHandlingException internalServerError(Throwable cause) {
        return internalServerError(cause.getMessage());
    }

    public static RequestHandlingException error(String message, final int errorCode) {
        return new RequestHandlingException(message) {
            @Override
            public void go(HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                if (isLogEnabled()) {
                    StringBuffer url = request.getRequestURL();
                    if (request.getQueryString() != null)
                        url.append("?").append(request.getQueryString());
                    LOG.info(request.getMethod() + " " + url + " - " + errorCode + ": " + getMessage(), this);
                }

                response.sendError(errorCode, getMessage());
            }
        };
    }

    public static RequestHandlingException redirect(final String url) {
        return new RequestHandlingException("redirect to " + url) {
            @Override
            public void go(HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                redirect(request, response, url);
            }
        };
    }

    public static RequestHandlingException redirect(URL url) {
        return redirect(url.toExternalForm());
    }

    public static RequestHandlingException redirectLocal(final String localPath) {
        return new RequestHandlingException("redirect to " + localPath) {
            @Override
            public void go(HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                URL requestedUrl = new URL(request.getRequestURL().toString());
                String protocol = requestedUrl.getProtocol();
                String host = requestedUrl.getHost();
                int port = requestedUrl.getPort();

                URL url = new URL(protocol, host, port, request.getContextPath() + localPath);

                redirect(request, response, url.toExternalForm());
            }
        };
    }

    public static RequestHandlingException appendToUrl(final String s) {
        return new RequestHandlingException("append '" + s + "'") {
            @Override
            public void go(HttpServletRequest request, HttpServletResponse response)
                    throws IOException {
                StringBuffer url = request.getRequestURL();
                url.append(s);
                String parms = request.getQueryString();
                if (parms != null)
                    url.append("?").append(parms);

                redirect(request, response, url.toString());
            }
        };
    }

    protected void redirect(HttpServletRequest request, HttpServletResponse response, String url)
            throws IOException {
        if (isLogEnabled() && LOG.isDebugEnabled())
            LOG.debug("Redirect [" + request.getRequestURI() + "] -> [" + url + "]");
        response.sendRedirect(url);
    }

    protected void forward(HttpServletRequest request, HttpServletResponse response, String uri)
            throws IOException, ServletException {
        if (isLogEnabled() && LOG.isDebugEnabled())
            LOG.debug("Forward [" + request.getRequestURI() + "] -> [" + uri + "]");
        request.getRequestDispatcher(uri).forward(request, response);
    }

    public RequestHandlingException noLog() {
        this.quiet = true;
        return this;
    }

    protected boolean isLogEnabled() {
        return !quiet;
    }
}