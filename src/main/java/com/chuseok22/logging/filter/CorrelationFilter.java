package com.chuseok22.logging.filter;

import com.chuseok22.logging.properties.HttpLoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationFilter extends OncePerRequestFilter {

  private final HttpLoggingProperties properties;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
    String headerName = properties.getCorrelationHeaderName();
    String mdcKey = properties.getMdcKey();

    String correlationId = request.getHeader(headerName);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString().replace("-", "");
    }

    MDC.put(mdcKey, correlationId);
    request.setAttribute("RequestID", correlationId);
    request.setAttribute("requestId", correlationId);
    response.setHeader(headerName, correlationId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(mdcKey);
    }
  }
}
