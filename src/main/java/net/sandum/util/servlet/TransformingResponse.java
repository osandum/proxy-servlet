package net.sandum.util.servlet;

import javax.servlet.http.HttpServletResponse;

/**
 * @author osa
 */
public interface TransformingResponse extends HttpServletResponse {

    byte[] getDownstreamContent();
    int getDownstreamStatus();
    String getDownstreamContentType();
    int getDownstreamContentLength();

    HttpServletResponse getUpstreamResponse();

}
