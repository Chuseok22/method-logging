package com.chuseok22.logging.filter;

import com.chuseok22.logging.properties.HttpLoggingProperties;
import com.chuseok22.logging.util.KeyValueFormatter;
import com.chuseok22.logging.util.LoggingUtil;
import com.chuseok22.logging.util.PrettyJson;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
@RequiredArgsConstructor
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

  private final HttpLoggingProperties properties;
  private final PathMatcher pathMatcher = new AntPathMatcher();

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    if (!properties.isEnabled() || !properties.isHttpFilterEnabled()) {
      return true;
    }
    String path = request.getRequestURI();
    List<String> excludedPaths = properties.getExcludedPaths();
    for (String pattern : excludedPaths) {
      if (pathMatcher.match(pattern, path)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
    if (isAsyncDispatch(request)) {
      filterChain.doFilter(request, response);
      return;
    }

    ContentCachingRequestWrapper cachingRequest = wrapRequest(request);
    ContentCachingResponseWrapper cachingResponse = wrapResponse(response);

    long startTime = System.currentTimeMillis();
    try {
      filterChain.doFilter(cachingRequest, cachingResponse);
    } finally {
      long duration = System.currentTimeMillis() - startTime;
      logRequestAndResponse(cachingRequest, cachingResponse, duration);
      cachingResponse.copyBodyToResponse();
    }
  }

  private ContentCachingRequestWrapper wrapRequest(HttpServletRequest request) {
    if (request instanceof ContentCachingRequestWrapper) {
      return (ContentCachingRequestWrapper) request;
    }
    return new ContentCachingRequestWrapper(request);
  }

  private ContentCachingResponseWrapper wrapResponse(HttpServletResponse response) {
    if (response instanceof ContentCachingResponseWrapper) {
      return (ContentCachingResponseWrapper) response;
    }
    return new ContentCachingResponseWrapper(response);
  }

  private void logRequestAndResponse(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, long duration) {
    String requestId = LoggingUtil.getRequestId(request);
    String method = request.getMethod();
    String uri = request.getRequestURI();
    String queryString = request.getQueryString();

    String requestContentType = request.getContentType();
    String responseContentType = response.getContentType();

    Charset requestCharset = resolveCharset(request.getCharacterEncoding());
    Charset responseCharset = resolveCharset(response.getCharacterEncoding());

    String requestBody = "";
    if (properties.isLogRequestBody() && !LoggingUtil.isMultipart(requestContentType)) {
      requestBody = getTextBodyIfAny(request.getContentAsByteArray(), requestCharset, requestContentType);
    }

    String responseBody = "";
    if (properties.isLogResponseBody() && !LoggingUtil.isMultipart(responseContentType)) {
      responseBody = getTextBodyIfAny(response.getContentAsByteArray(), responseCharset, responseContentType);
    }

    String logText;
    if (properties.isMultiline()) {
      logText = buildMultilineLog(
        requestId,
        method,
        uri,
        queryString,
        requestContentType,
        response.getStatus(),
        duration,
        requestBody,
        responseBody,
        requestCharset,
        request,
        response
      );
    } else {
      logText = buildOnelineLog(
        requestId,
        method,
        uri,
        queryString,
        response.getStatus(),
        duration,
        requestBody,
        responseBody
      );
    }

    log.info(logText);
  }

  private Charset resolveCharset(String encoding) {
    if (encoding == null || encoding.isBlank()) {
      return StandardCharsets.UTF_8;
    }
    try {
      return Charset.forName(encoding);
    } catch (IllegalArgumentException e) {
      return StandardCharsets.UTF_8;
    }
  }

  private String buildMultilineLog(String requestId, String method, String uri, String queryString, String requestContentType, int status, long duration, String requestBody, String responseBody,
    Charset charset, HttpServletRequest request, HttpServletResponse response) {
    int indent = properties.getIndentSize();
    String indentStr = repeat(' ', indent);
    StringBuilder builder = new StringBuilder();
    builder.append("[HTTP]");
    if (requestId != null) {
      builder.append(" [RequestId: ").append(requestId).append("]");
    }
    builder.append("\n");

    // Request
    builder.append("-> Request: ").append(method).append(" ").append(uri).append("\n");

    // 헤더
    if (properties.isLogHeaders()) {
      builder.append(indentStr).append("Headers-Request:\n");
      builder.append(formatRequestHeaders(request, indent));
    }

    // 쿼리파라미터
    if (properties.isPrettyQueryParams()) {
      Map<String, List<String>> queryParameter = KeyValueFormatter.parseQuery(queryString, charset);
      builder.append(indentStr).append("Query:\n");
      builder.append(KeyValueFormatter.formatBlockMasked(queryParameter, indent, properties.isMaskSensitive(), properties.getSensitiveKeys(), properties.getMaskReplacement()));
    } else if (queryString != null && !queryString.isEmpty()) {
      builder.append(indentStr).append("QueryRaw: ").append(queryString).append("\n");
    } else {
      builder.append(indentStr).append("Query: (empty)\n");
    }

    // Request Body
    if (requestBody != null && !requestBody.isEmpty()) {
      // 폼 데이터
      if (LoggingUtil.isFormUrlEncoded(requestContentType) && properties.isPrettyFormBody()) {
        Map<String, List<String>> form = KeyValueFormatter.parseFormUrlEncoded(requestBody, charset);
        builder.append(indentStr).append("Form:\n");
        builder.append(KeyValueFormatter.formatBlockMasked(form, indent, properties.isMaskSensitive(), properties.getSensitiveKeys(), properties.getMaskReplacement()));
        builder.append(indentStr).append("Body: (suppressed, see Form)\n");
      } else {
        // JSON
        String prettyRequest = requestBody;
        if (properties.isPrettyJson()) {
          prettyRequest = PrettyJson.tryPrettyAndMask(
            requestBody,
            properties.getIndentSize(),
            properties.isMaskSensitive(),
            properties.getSensitiveKeys(),
            properties.getMaskReplacement()
          );
        }
        prettyRequest = LoggingUtil.truncate(prettyRequest, properties.getMaxBodyLength());
        builder.append(indentStr).append("Body:\n").append(indentStr).append(prettyRequest.replace("\n", "\n" + indentStr)).append("\n");
      }
    } else {
      builder.append(indentStr).append("Body: (empty)\n");
    }

    // Response
    builder.append("<- Response: ").append(status).append(" (").append(duration).append(" ms)").append("\n");

    // Header (response)
    if (properties.isLogHeaders()) {
      builder.append(indentStr).append("Headers-Response:\n");
      builder.append(formatResponseHeaders(response, indent));
    }

    if (responseBody != null && !responseBody.isEmpty()) {
      String prettyResponse = responseBody;
      if (properties.isPrettyJson()) {
        prettyResponse = PrettyJson.tryPrettyAndMask(
          responseBody,
          properties.getIndentSize(),
          properties.isMaskSensitive(),
          properties.getSensitiveKeys(),
          properties.getMaskReplacement()
        );
      }
      prettyResponse = LoggingUtil.truncate(prettyResponse, properties.getMaxBodyLength());
      builder.append(indentStr).append("Body: \n").append(indentStr).append(prettyResponse.replace("\n", "\n" + indentStr));
    } else {
      builder.append(indentStr).append("Body: (empty)");
    }
    return builder.toString();
  }

  private String buildOnelineLog(String requestId, String method, String uri, String queryString, int status, long duration, String requestBody, String responseBody) {
    StringBuilder builder = new StringBuilder();
    builder.append("[HTTP] ");
    if (requestId != null) {
      builder.append("[RequestId: ").append(requestId).append("] ");
    }
    builder.append(method).append(" ").append(uri);
    if (queryString != null && !queryString.isEmpty()) {
      builder.append("?").append(queryString);
    }
    if (requestBody != null && !requestBody.isEmpty()) {
      builder.append(" RequestBody=").append(LoggingUtil.truncate(requestBody, properties.getMaxBodyLength()));
    }
    builder.append(" => Status=").append(status).append(", DurationMs=").append(duration);
    if (responseBody != null && !responseBody.isEmpty()) {
      builder.append(", ResponseBody=").append(LoggingUtil.truncate(responseBody, properties.getMaxBodyLength()));
    }
    return builder.toString();
  }

  private String repeat(char c, int count) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < count; i++) {
      builder.append(c);
    }
    return builder.toString();
  }

  private String getTextBodyIfAny(byte[] content, Charset charset, String contentType) {
    if (content == null || content.length == 0) {
      return "";
    }
    if (LoggingUtil.isMultipart(contentType)) {
      return "";
    }
    String body = new String(content, charset);
    if (LoggingUtil.isJson(contentType)) {
      return body;
    }
    if (LoggingUtil.isFormUrlEncoded(contentType)) {
      return body;
    }
    if (!LoggingUtil.isTextBody(body)) {
      return "";
    }
    return body;
  }

  private String formatRequestHeaders(HttpServletRequest request, int indent) {
    String indentStr = repeat(' ', indent);
    StringBuilder builder = new StringBuilder();
    Enumeration<String> names = request.getHeaderNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      Enumeration<String> values = request.getHeaders(name);
      String line = maskHeaderLine(name, enumerationToString(values));
      builder.append(indentStr).append("- ").append(line).append("\n");
    }
    return builder.toString();
  }

  private String formatResponseHeaders(HttpServletResponse response, int indent) {
    String indentStr = repeat(' ', indent);
    StringBuilder builder = new StringBuilder();
    Collection<String> names = response.getHeaderNames();
    for (String name : names) {
      Collection<String> values = response.getHeaders(name);
      String line = maskHeaderLine(name, collectionToString(values));
      builder.append(indentStr).append("- ").append(line).append("\n");
    }
    return builder.toString();
  }

  private String maskHeaderLine(String name, String value) {
    if (properties.isMaskSensitive() && containsIgnoreCase(properties.getSensitiveKeys(), name)) {
      return name + ": " + properties.getMaskReplacement();
    }
    return name + ": " + value;
  }

  private boolean containsIgnoreCase(List<String> keys, String name) {
    if (keys == null || name == null) {
      return false;
    }
    for (String key : keys) {
      if (key != null && key.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }


  private String enumerationToString(Enumeration<String> e) {
    StringBuilder b = new StringBuilder();
    boolean first = true;
    while (e.hasMoreElements()) {
      String v = e.nextElement();
      if (!first) {
        b.append(", ");
      }
      b.append(v);
      first = false;
    }
    return b.toString();
  }

  private String collectionToString(Collection<String> c) {
    StringBuilder b = new StringBuilder();
    boolean first = true;
    for (String v : c) {
      if (!first) {
        b.append(", ");
      }
      b.append(v);
      first = false;
    }
    return b.toString();
  }
}
