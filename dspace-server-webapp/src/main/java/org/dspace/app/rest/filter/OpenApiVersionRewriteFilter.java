/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.filter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter that rewrites the OpenAPI version in the generated spec JSON
 * to 3.0.0 when serving "/v3/api-docs". This avoids dependency on
 * springdoc-core at compile time while ensuring clients receive 3.0.0.
 */
@Component
public class OpenApiVersionRewriteFilter extends OncePerRequestFilter {

    private static final String API_DOCS_PATH = "/v3/api-docs";

    /**
     * Determines whether this filter should be applied for the given request.
     * Only requests that target the OpenAPI docs endpoint are filtered.
     *
     * @param request the current HTTP request
     * @return true when the filter should be skipped, false otherwise
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        // Only filter requests targeting the OpenAPI docs JSON
        return uri == null || !uri.contains(API_DOCS_PATH);
    }

    /**
     * Intercepts the response for the OpenAPI docs endpoint, buffers the JSON,
     * sets the {@code openapi} field to {@code 3.0.0}, and writes the modified
     * JSON back to the client.
     *
     * @param request the current HTTP request
     * @param response the HTTP response to write to
     * @param filterChain the filter chain to continue processing
     * @throws ServletException if the filter encounters a servlet error
     * @throws IOException if an I/O error occurs during processing
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        BufferingHttpServletResponseWrapper bufferingResponse = new BufferingHttpServletResponseWrapper(response);
        filterChain.doFilter(request, bufferingResponse);

        byte[] body = bufferingResponse.getBody();
        if (body != null && body.length > 0) {
            String contentType = response.getContentType();
            Charset charset = getCharsetFromContentType(contentType);
            // Parse JSON and set openapi to 3.0.0
            ObjectMapper mapper = new ObjectMapper();
            String output;
            try {
                JsonNode root = mapper.readTree(new String(body, charset));
                if (root instanceof ObjectNode) {
                    ((ObjectNode) root).put("openapi", "3.0.0");
                }
                output = mapper.writeValueAsString(root);
            } catch (Exception e) {
                // If parsing fails, fall back to original body
                output = new String(body, charset);
            }

            byte[] out = output.getBytes(charset);
            response.setContentLength(out.length);
            response.getOutputStream().write(out);
        } else {
            // No body captured; just flush underlying response
            response.getOutputStream().flush();
        }
    }

    private Charset getCharsetFromContentType(String contentType) {
        if (contentType != null) {
            int idx = contentType.toLowerCase().indexOf("charset=");
            if (idx >= 0) {
                String cs = contentType.substring(idx + 8).trim();
                try {
                    return Charset.forName(cs);
                } catch (Exception ignore) {
                    // Ignore invalid/unknown charset and fallback to UTF-8
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Simple response wrapper that buffers the response body for post-processing.
     * This allows interceptors to read and modify the full response before it
     * is sent to the client.
     */
    private static class BufferingHttpServletResponseWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private PrintWriter writer;
        private ServletOutputStream outputStream;

        BufferingHttpServletResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        /**
         * Returns a buffering {@link ServletOutputStream} to capture response body
         * bytes for later post-processing.
         *
         * @return a buffering ServletOutputStream
         * @throws IOException if the output stream cannot be obtained
         */
        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (outputStream == null) {
                outputStream = new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return true;
                    }
                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                        // no-op
                    }
                    @Override
                    public void write(int b) {
                        buffer.write(b);
                    }
                    @Override
                    public void write(byte[] b, int off, int len) {
                        buffer.write(b, off, len);
                    }
                };
            }
            return outputStream;
        }

        /**
         * Returns a {@link PrintWriter} backed by the internal buffer to capture
         * response body text for later post-processing.
         *
         * @return a PrintWriter that writes into the buffer
         * @throws IOException if the writer cannot be obtained
         */
        @Override
        public PrintWriter getWriter() throws IOException {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(buffer, getCharacterEncoding()));
            }
            return writer;
        }

        /**
         * Returns the buffered response body as a byte array.
         *
         * @return the buffered body bytes
         * @throws IOException if flushing the writer fails
         */
        byte[] getBody() throws IOException {
            if (writer != null) {
                writer.flush();
            }
            return buffer.toByteArray();
        }
    }
}
