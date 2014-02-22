package net.sandum.util.servlet;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.*;

/**
 * @author     osa
 * @since      Feb 26, 2009
 * @version    $Id: RequestHandler.java 31725 2011-11-28 09:17:04Z osa $
 */
public interface RequestHandler {
    abstract void go(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException;
}
