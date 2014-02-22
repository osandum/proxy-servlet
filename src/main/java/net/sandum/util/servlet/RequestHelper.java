package net.sandum.util.servlet;

import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.http.HttpServletRequest;

/**
 * @author     osa
 * @since      19-05-2011
 * @version    $Id$
 */
public class RequestHelper {
    private final static Pattern URL_PATTERN = Pattern.compile("^(\\w+://[^/]+)(/.*)$");

    public static String rootPath(HttpServletRequest request) {
        StringBuffer myUrl = request.getRequestURL();
        Matcher m = URL_PATTERN.matcher(myUrl);
        if (!m.matches())
            throw new IllegalArgumentException("'" + myUrl + "': unrecognized");

        return m.group(1);
    }

    public static String remoteAddr(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        Enumeration<String> e = request.getHeaders("X-Forwarded-For");
        if (e.hasMoreElements())
            // If request has one or more 'X-Forwarded-For: nn.nn.nn.nn, mm.mm.mm.mm, oo.oo.oo.oo, ...',
            // return the first IP listed on the first of these headers:
            return e.nextElement().split("\\s*,\\s*")[0];

        return request.getRemoteAddr();
    }
}