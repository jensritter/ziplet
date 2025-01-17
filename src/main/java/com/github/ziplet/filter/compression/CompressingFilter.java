/*
 * Copyright 2004 and onwards Sean Owen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.ziplet.filter.compression;

import com.github.ziplet.filter.compression.statistics.CompressingFilterStats;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * <p>This filter can, based on HTTP headers in a {@link HttpServletRequest}, compress data written
 * to the {@link HttpServletResponse}, or decompress data read from the request. When supported by
 * the client browser, this can potentially greatly reduce the number of bytes written across the
 * network from and to the client. As a {@link Filter}, this class can also be easily added to any
 * J2EE 1.3+ web application.</p>
 *
 * <h3>Features</h3>
 *
 * <p>Why might you want to use this solution compared to others?</p>
 *
 * <ol> <li>Little in-memory buffering</li> <li>Handles compressed requests too</li> <li>Selective
 * compression based on content type, size, or user agent</li> <li>Exposes compression
 * statistics</li> </ol>
 *
 * <h3>Installation</h3>
 *
 * <ol> <li>Add the {@code pjl-comp-filter-XX.jar} file containing CompressingFilter to your web
 * application's {@code WEB-INF/lib} directory.</li> <li>Add the following entries to your {@code
 * web.xml} deployment
 * descriptor: <pre>
 * {@code
 *
 * <filter>
 *  <filter-name>CompressingFilter</filter-name>
 *  <filter-class>com.github.ziplet.filter.compression.CompressingFilter</filter-class>
 * </filter>
 * ...
 * <filter-mapping>
 *  <filter-name>CompressingFilter</filter-name>
 *  <url-pattern>/*</url-pattern>
 * </filter-mapping>
 * }
 *      </pre>
 * </li> </ol>
 *
 * <h3>Configuration</h3>
 *
 * <p>{@link CompressingFilter} supports the following parameters:</p> <ul>
 *
 * <li><strong>debug</strong> (optional): if set to "true", additional debug information will be
 * written to the servlet log. Defaults to false.</li>
 *
 * <li><strong>compressionThreshold</strong> (optional): sets the size of the smallest response that
 * will be compressed, in bytes. That is, if less than {@code compressionThreshold} bytes are
 * written to the response, it will not be compressed and the response will go to the client
 * unmodified. If 0, compression always begins immediately. Defaults to 1024.</li>
 *
 * <li><strong>compressionLevel</strong> (optional): sets the compression level used for response
 * gzip/deflate compression, from 1 (fastest compression, less CPU) to 9 (best compression, more
 * CPU), or -1 (platform default, currently equivalent to 6). Defaults to -1.</li>
 *
 * <li><strong>statsEnabled</strong> (optional): enables collection of statistics. See {@link
 * CompressingFilterStats}. Defaults to false.</li>
 *
 * <li><strong>includeContentTypes</strong> (optional): if specified, this is treated as a
 * comma-separated list of content types (e.g. {@code text/html,text/xml}). The filter will attempt
 * to only compress responses which specify one of these values as its content type, for example via
 * {@link HttpServletResponse#setContentType(String)}. Note that the filter does not know the
 * response content type at the time it is applied, and so must apply itself and later attempt to
 * disable compression when content type has been set. This will fail if the response has already
 * been committed. Also note that this parameter cannot be specified if {@code excludeContentTypes}
 * is too.</li>
 *
 * <li><strong>excludeContentTypes</strong> (optional): same as above, but specifies a list of
 * content types to <strong>not</strong> compress. Everything else will be compressed. However note
 * that any content type that indicates a compressed format (e.g. application/gzip,
 * application/x-compress) will not be compressed in any event.</li>
 *
 * <li><strong>includePathPatterns</strong> (optional): if specified, this is treated as a
 * comma-separated list of regular expressions (of the type accepted by {@link Pattern}) which match
 * exactly those paths which should be compressed by this filter. Anything else will not be
 * compressed. One can also merely apply the filter to a subset of all URIs served by the web
 * application using standard {@code filter-mapping} elements in {@code web.xml}; this element
 * provides more fine-grained control for when that mechanism is insufficient. "Paths" here means
 * values returned by {@link HttpServletRequest#getRequestURI()}. Note that the regex must match the
 * filename exactly; pattern "static" does <strong>not</strong> match everything containing the
 * string "static. Use ".*static.*" for that, for example. This cannot be specified if {@code
 * excludeFileTypes} is too.</li>
 *
 * <li><strong>excludePathPatterns</strong> (optional): same as above, but specifies a list of
 * patterns which match paths that should <strong>not</strong> be compressed. Everything else will
 * be compressed.</li>
 *
 * <li><strong>includeUserAgentPatterns</strong> (optional): Like {@code includePathPatterns}. Only
 * requests with {@code User-Agent} headers whose value matches one of these regular expressions
 * will be compressed. Can't be specified if {@code excludeUserAgentPatterns} is too.</li>
 *
 * <li><strong>excludeUserAgentPatterns</strong> (optional): as above, requests whose {@code
 * User-Agent} header matches one of these patterns will not be compressed.</li>
 *
 * <li><strong>noVaryHeaderPatterns</strong> (optional): Like {@code includeUserAgentPatterns}.
 * Requests with {@code User-Agent} headers whose value matches one of these regular expressions
 * result in a response that does not contain the {@code Vary-header} Since version 1.8</li>
 *
 * <li><strong>javaUtilLogger</strong> (optional): if specified, the named {@code
 * java.util.logging.Logger} will also receive log messages from this filter.</li>
 *
 * <li><strong>jakartaCommonsLogger</strong> (optional): if specified the named Jakarta Commons Log
 * will also receive log messages from this filter.</li>
 *
 * </ul>
 *
 * <p>These values are configured in {@code web.xml} as well with init-param
 * elements: <pre>
 * {@code
 *
 *  <filter>
 *      <filter-name>CompressingFilterStatsImpl</filter-name>
 *      <filter-class>com.github.ziplet.filter.compression.CompressingFilter</filter-class>
 *      <init-param>
 *          <param-name>debug</param-name>
 *          <param-value>true</param-value>
 *      </init-param>
 *  </filter>
 * }
 * </pre>
 *
 * <h3>Supported compression algorithms</h3>
 *
 * <h4>Response</h4>
 *
 * <p>This filter supports the following compression algorithms when compressing data to the
 * repsonse, as specified in the "Accept-Encoding" HTTP request header:</p>
 *
 * <ul> <li>gzip</li> <li>x-gzip</li> <li>compress</li> <li>x-compress</li> <li>deflate</li>
 * <li>identity (that is, no compression)</li> </ul>
 *
 * <h4>Request</h4>
 *
 * <p>This filter supports the following compression algorithms when decompressing data from the
 * request body, as specified in the "Content-Encoding" HTTP request header:</p>
 *
 * <ul> <li>gzip</li> <li>x-gzip</li> <li>compress</li> <li>x-compress</li> <li>deflate</li>
 * <li>identity</li> </ul>
 *
 * <h3>Controlling runtime behavior</h3>
 *
 * <p>An application may force the encoding / compression used by setting an "Accept-Encoding" value
 * into the request as an attribute under the key {@link #FORCE_ENCODING_KEY}. Obviously this has to
 * be set upstream from the filter, not downstream.</p>
 *
 * <h3>Caveats and Notes</h3>
 *
 * <p>The filter requires Java 5 and J2EE 1.4 or better.</p>
 *
 * <p>Note that if this filter decides that it should try to compress the response, it <em>will</em>
 * close the response (whether or not it ends up compressing the response). No more can be written
 * to the response after this filter has been applied; this should never be necessary anyway. Put
 * this filter ahead of any filters that might try to write to the repsonse, since presumably you
 * want this content compressed too anyway.</p>
 *
 * <p>If a {@link java.io.OutputStream#flush()} occurs before the filter has decided whether to
 * compress or not, it will be forced into compression mode.</p>
 *
 * <p>The filter will not compress if the response sets {@code Cache-Control: no-transform} header
 * in the response.</p>
 *
 * <p>The filter attempts to modify the {@code ETag} response header, if present, when compressing.
 * This is because the compressed response must be considered a separate entity by caches. It simply
 * appends, for example, "-gzip" to the ETag header value when compressing with gzip. This is not
 * guaranteed to work in all containers, in the sense that some containers may not properly
 * associated this ETag with the compressed content and simply return the response again.</p>
 *
 * <p>The filter always sets the {@code Vary} response header to indicate that a different response
 * may be returned based on the {@code Accept-Encoding} header of the request.</p>
 *
 * @author Sean Owen
 * @since 1.0
 */
public final class CompressingFilter implements Filter {

    /**
     * One may force the filter to use a particular encoding by setting its value as an attribute of
     * the {@link ServletRequest} passed to this filter, under this key. The value should be a valid
     * "Accept-Encoding" header value, like "gzip". Specify "identity" to force no compression.
     *
     * @since 1.2
     */
    public static final String FORCE_ENCODING_KEY = "com.planet.servlet.filter.compression.ForceEncoding";
    /**
     * A request attribute is set under this key with a non-null value if this filter has applied
     * compression to the response. Upstream filters may check for this flag. Note that if the
     * response has been compressed, then it will be closed by the time this filter finishes as
     * well.
     *
     * @since 1.2
     */
    public static final String COMPRESSED_KEY = "com.github.ziplet.filter.compression.Compressed";
    static final String VARY_HEADER = "Vary";
    static final String VERSION = "1.7.1";
    static final String VERSION_STRING = CompressingFilter.class.getName() + '/' + VERSION;
    private static final Logger LOGGER = LoggerFactory.getLogger(CompressingFilter.class);
    private static final String ALREADY_APPLIED_KEY = "com.github.ziplet.filter.compression.AlreadyApplied";
    private CompressingFilterContext context;
    private CompressingFilterStats stats;

    @Override
    public void init(FilterConfig config) throws ServletException {
        assert config != null;
        context = new CompressingFilterContext(config);
        if (stats != null) {
            context.setCompressingFilterStats(this.stats);
        }
        LOGGER.info("CompressingFilter has initialized");
    }

    public void doFilter(ServletRequest request,
        ServletResponse response,
        FilterChain chain) throws IOException, ServletException {

        ServletRequest chainRequest = getRequest(request);
        ServletResponse chainResponse = getResponse(request, response);

        boolean attemptingToDecompressRequest = chainRequest != null;
        boolean attemptingToCompressResponse = chainResponse != null;

        if (chainRequest == null) {
            chainRequest = request;
        }
        if (chainResponse == null) {
            chainResponse = response;
        }

        if (attemptingToDecompressRequest) {
            context.getStats().incrementNumRequestsCompressed();
        } else {
            context.getStats().incrementTotalRequestsNotCompressed();
        }

        request.setAttribute(ALREADY_APPLIED_KEY, Boolean.TRUE);
        chain.doFilter(chainRequest, chainResponse);

        if (attemptingToCompressResponse) {

            CompressingHttpServletResponse compressingResponse = (CompressingHttpServletResponse) chainResponse;

            // We close the response in all cases since much of the logic in this filter depends upon
            // close() getting called at some point; it seems that some containers do not explicitly
            // call close() in all situations, so we do here. We also close it because if compressed data
            // has been written to the stream, it's almost certainly not valid or even possible to write more
            // to the stream after this filter anyway.
            LOGGER.debug("Closing the response (if not already closed)...");
            try {
                // This will also flush
                compressingResponse.close();
            } catch (IOException ioe) {
                // underlying stream might have been closed -- ignore IOException here
                LOGGER.debug("Error while flushing buffer", ioe);
            }

            if (compressingResponse.isCompressing()) {
                chainRequest.setAttribute(COMPRESSED_KEY, Boolean.TRUE);
            }

            context.getStats().incrementNumResponsesCompressed();
        } else {
            context.getStats().incrementTotalResponsesNotCompressed();
        }

    }

    private ServletRequest getRequest(ServletRequest request) {
        if (!(request instanceof HttpServletRequest)) {
            LOGGER.debug("Can't compress non-HTTP request");
            return null;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String contentEncoding = httpRequest
            .getHeader(CompressingHttpServletResponse.CONTENT_ENCODING_HEADER);
        if (contentEncoding == null) {
            LOGGER.debug("Request is not compressed, so not decompressing");
            return null;
        }

        if (!CompressingStreamFactory.isSupportedRequestContentEncoding(contentEncoding)) {
            LOGGER.debug("Can't decompress request with encoding: " + contentEncoding);
            return null;
        }

        return new CompressedHttpServletRequest(httpRequest,
            CompressingStreamFactory.getFactoryForContentEncoding(contentEncoding),
            context);
    }

    private ServletResponse getResponse(ServletRequest request,
        ServletResponse response) {
        if (response.isCommitted() || request.getAttribute(ALREADY_APPLIED_KEY) != null) {
            LOGGER.debug("Response committed or filter has already been applied");
            return null;
        }

        if (!(request instanceof HttpServletRequest)
            || !(response instanceof HttpServletResponse)) {
            LOGGER.debug("Can't compress non-HTTP request, response");
            return null;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Request for: '" + httpRequest.getRequestURI() + '\'');
        }

        String requestURI = httpRequest.getRequestURI();
        if (!isCompressablePath(requestURI)) {
            LOGGER.debug("Compression disabled for path: " + requestURI);
            return null;
        }

        String userAgent = httpRequest.getHeader("User-Agent");
        if (!isCompressableUserAgent(userAgent)) {
            LOGGER.debug("Compression disabled for User-Agent: " + userAgent);
            return null;
        }

        // The "Vary" header must be applied if the response *could be compressed*, not *if it is compressed*.
        // So we have to set it here as we now know the request is allowed by the filter-mapping (web.xml)
        // and by the path and user-agent patterns (above). It may or may not be compressed (depending
        // on the request "Accept-Encoding" header, below) - this is why we have to add the Vary Header now:
        setVaryHeader(httpResponse, userAgent);

        String contentEncoding = CompressingStreamFactory.getBestContentEncoding(httpRequest);
        assert contentEncoding != null;

        if (CompressingStreamFactory.NO_ENCODING.equals(contentEncoding)) {
            LOGGER.debug("Compression not supported or declined by request");
            return null;
        }

        if (LOGGER.isDebugEnabled()) {
            LOGGER
                .debug("Compression supported; using content encoding '" + contentEncoding + '\'');
        }

        CompressingStreamFactory compressingStreamFactory =
            CompressingStreamFactory.getFactoryForContentEncoding(contentEncoding);

        return new CompressingHttpServletResponse(httpResponse,
            compressingStreamFactory,
            contentEncoding,
            context);
    }

    /**
     * <p>Adds the "Vary" header; must be applied if the response <b>could be compressed</b>, not
     * <b>if it is compressed</b>.</p>
     *
     * <p>This is necessary to make sure the response is not cached (e.g. by Web proxies or Squid
     * Reverse Proxy setups) as the content depends on the "Accept-Encoding" header of the client
     * browser.</p>
     */
    void setVaryHeader(HttpServletResponse httpResponse, String userAgent) {
        // Note: There is an IE6/7 issue with the "Vary" header:
        //     http://www.fiddler2.com/fiddler/perf/aboutvary.asp
        //
        // But setting the "Vary" header if the response *could be compressed*
        // (as it is done here) should not hurt as IE6/7 will send a "Accept-Encoding"
        // header so that the response *will be compressed*, and the IE6/7 issue
        // does not apply for compressed responses (from the above URL):
        //     IE6: will ignore the Vary header entirely if the response was delivered with HTTP Compression.
        //     IE7: WinINET will remove the Vary: Accept-Encoding header if it decompressed the response.
        //          Therefore, you should only send a Vary: Accept-Encoding header when you have
        //          compressed the content (e.g. Content-Encoding: gzip).
        if (sendVaryHeader(userAgent)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Setting Vary Header because the response *could be compressed*. "
                    + VARY_HEADER + " : " + CompressingHttpServletResponse.ACCEPT_ENCODING_HEADER);
            }
            httpResponse
                .addHeader(VARY_HEADER, CompressingHttpServletResponse.ACCEPT_ENCODING_HEADER);
        } else {
            if (LOGGER.isDebugEnabled()) {
                LOGGER
                    .debug("Vary header not set, because user agent should not receive the header");
            }
        }
    }

    public void destroy() {
        LOGGER.info("CompressingFilter is being destroyed...");
    }

    /**
     * Checks to see if the given path should be compressed. This checks against the {@code
     * includePathPatterns} and {@code excludePathPatterns} filter init parameters; if the former is
     * set and the given path matches a regular expression in that parameter's list, or if the
     * latter is set and the path does not match, then this method returns {@code true}.
     *
     * @param path request path
     * @return true if and only if the path should be compressed
     */
    private boolean isCompressablePath(String path) {
        if (path != null) {
            for (Pattern pattern : context.getPathPatterns()) {
                if (pattern.matcher(path).matches()) {
                    return context.isIncludePathPatterns();
                }
            }
        }
        return !context.isIncludePathPatterns();
    }

    private boolean isCompressableUserAgent(String userAgent) {
        if (userAgent != null) {
            for (Pattern pattern : context.getUserAgentPatterns()) {
                if (pattern.matcher(userAgent).matches()) {
                    return context.isIncludeUserAgentPatterns();
                }
            }
        }
        return !context.isIncludeUserAgentPatterns();
    }

    private boolean sendVaryHeader(String userAgent) {
        if (userAgent != null) {
            for (Pattern pattern : context.getNoVaryHeaderPatterns()) {
                if (pattern.matcher(userAgent).matches()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return VERSION_STRING;
    }

    /**
     * Method used to inject stats from outside if you are implementing your own metrics to report
     * stats
     *
     * @param stats filter status
     */
    public void setCompressingFilterStats(CompressingFilterStats stats) {
        this.stats = stats;
    }
}
