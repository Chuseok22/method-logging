package com.chuseok22.logging.aspect;

import com.chuseok22.logging.annotation.LogMonitoring;
import com.chuseok22.logging.properties.HttpLoggingProperties;
import com.chuseok22.logging.util.KeyValueFormatter;
import com.chuseok22.logging.util.LoggingUtil;
import com.chuseok22.logging.util.PrettyJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Slf4j
@RequiredArgsConstructor
public class MethodExecutionLoggingAspect {

  private final HttpLoggingProperties properties;

  private static final String HEADER_LINE = "==========================[메서드 로깅 시작]==========================";
  private static final String FOOTER_LINE = "==================================================================";

  @Around("@annotation(logMonitoring)")
  public Object logExecution(ProceedingJoinPoint joinPoint, LogMonitoring logMonitoring) throws Throwable {
    if (!properties.isEnabled()) {
      return joinPoint.proceed();
    }

    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String className = signature.getDeclaringType().getSimpleName();
    String methodName = signature.getMethod().getName();

    ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
    HttpServletResponse response = attributes != null ? attributes.getResponse() : null;

    // CorrelationId (MDC + 응답헤더)
    String requestId = MDC.get(properties.getMdcKey());
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString().replace("-", "");
      MDC.put(properties.getMdcKey(), requestId);
    }
    if (response != null) {
      response.setHeader(properties.getCorrelationHeaderName(), requestId);
    }
    if (request != null) {
      request.setAttribute("RequestID", requestId);
      request.setAttribute("requestId", requestId);
    }

    long start = System.currentTimeMillis();
    StringBuilder b = new StringBuilder();
    b.append("\n\n").append(HEADER_LINE).append("\n\n");

    // ======================= HTTP REQUEST =======================
    if (request != null) {
      String method = request.getMethod();
      String uri = request.getRequestURI();
      String queryString = request.getQueryString();
      String contentType = request.getContentType();
      Charset cs = resolve(request.getCharacterEncoding());

      b.append("[HTTP REQUEST] [RequestId: ").append(requestId).append("]\n");
      b.append("-> ").append(method).append(" ").append(uri).append("\n");

      // [CHANGED] 요청 헤더 출력 여부
      if (properties.isLogRequestHeaders()) {
        b.append("  Headers:\n");
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
          String name = names.nextElement();
          List<String> values = Collections.list(request.getHeaders(name));
          String joined = String.join(", ", values);
          if (properties.isMaskSensitive() && containsIgnoreCase(properties.getSensitiveKeys(), name)) {
            joined = properties.getMaskReplacement();
          }
          b.append("  - ").append(name).append(": ").append(joined).append("\n");
        }
      }

      // Query
      Map<String, List<String>> qp = KeyValueFormatter.parseQuery(queryString, cs);
      b.append("  Query:\n").append(KeyValueFormatter.formatBlockMasked(
        qp, 2, properties.isMaskSensitive(), properties.getSensitiveKeys(), properties.getMaskReplacement()
      ));

      // Body (@RequestBody 기반)
      if (properties.isLogRequestBody()) {
        if (LoggingUtil.isJson(contentType)) {
          List<Object> bodies = extractRequestBodyArgs(signature, joinPoint.getArgs());
          if (!bodies.isEmpty()) {
            Object only = bodies.size() == 1 ? bodies.get(0) : bodies;
            String pretty = PrettyJson.toJsonOrToStringMasked(
              only,
              properties.isMaskSensitive(),
              properties.getSensitiveKeys(),
              properties.getMaskReplacement()
            );
            pretty = LoggingUtil.truncate(pretty, properties.getMaxBodyLength());
            b.append("  Body:\n  ").append(pretty.replace("\n", "\n  ")).append("\n");
          } else {
            Map<String, List<String>> form = KeyValueFormatter.fromParamMap(request.getParameterMap());
            b.append("  Body:\n").append(KeyValueFormatter.formatBlockMasked(
              form, 2, properties.isMaskSensitive(), properties.getSensitiveKeys(), properties.getMaskReplacement()
            ));
          }
        } else if (LoggingUtil.isFormUrlEncoded(contentType)) {
          Map<String, List<String>> form = KeyValueFormatter.fromParamMap(request.getParameterMap());
          b.append("  Form:\n").append(KeyValueFormatter.formatBlockMasked(
            form, 2, properties.isMaskSensitive(), properties.getSensitiveKeys(), properties.getMaskReplacement()
          ));
          b.append("  Body: (suppressed, see Form)\n");
        } else if (LoggingUtil.isMultipart(contentType)) {
          b.append("  Body: [multipart] (files/parts omitted)\n");
        } else {
          Map<String, List<String>> form = KeyValueFormatter.fromParamMap(request.getParameterMap());
          b.append("  Body:\n").append(KeyValueFormatter.formatBlockMasked(
            form, 2, properties.isMaskSensitive(), properties.getSensitiveKeys(), properties.getMaskReplacement()
          ));
        }
      }
      b.append("\n");
    }

    // ======================= METHOD ARGS =======================
    if (logMonitoring.logParameters()) {
      String argsJson = PrettyJson.toJsonOrToStringMasked(
        sanitizeArgs(joinPoint.getArgs()),
        properties.isMaskSensitive(),
        properties.getSensitiveKeys(),
        properties.getMaskReplacement()
      );
      b.append("[METHOD] ").append(className).append(".").append(methodName).append(" Args:\n  ")
        .append(argsJson.replace("\n", "\n  "))
        .append("\n\n");
    }

    Object result = null;
    Throwable thrown = null;
    try {
      result = joinPoint.proceed();
      return result;
    } catch (Throwable ex) {
      thrown = ex;
      throw ex;
    } finally {
      long took = System.currentTimeMillis() - start;

      // ======================= RESPONSE / RESULT =======================
      if (thrown == null) {
        if (logMonitoring.logResult()) {
          Object printable = unwrapResponse(result); // [CHANGED] 응답 헤더/바디 정책 반영
          String pretty = PrettyJson.toJsonOrToStringMasked(
            printable,
            properties.isMaskSensitive(),
            properties.getSensitiveKeys(),
            properties.getMaskReplacement()
          );
          pretty = LoggingUtil.truncate(pretty, properties.getMaxBodyLength());
          b.append("<- ").append(className).append(".").append(methodName)
            .append(" Result (").append(took).append(" ms):\n  ")
            .append(pretty.replace("\n", "\n  "))
            .append("\n");
        } else if (logMonitoring.logExecutionTime()) {
          b.append("<- ").append(className).append(".").append(methodName)
            .append(" (").append(took).append(" ms)\n");
        }
      } else {
        b.append("[ERROR] ").append(className).append(".").append(methodName)
          .append(" (").append(took).append(" ms): ").append(thrown.getMessage()).append("\n");
      }

      b.append("\n").append(FOOTER_LINE).append("\n");
      log.info(b.toString());
    }
  }

  // ===== helpers =====

  private Charset resolve(String enc) {
    if (enc == null || enc.isBlank()) {
      return StandardCharsets.UTF_8;
    }
    try {
      return Charset.forName(enc);
    } catch (Exception e) {
      return StandardCharsets.UTF_8;
    }
  }

  private boolean containsIgnoreCase(List<String> keys, String name) {
    if (keys == null || name == null) {
      return false;
    }
    for (String k : keys) {
      if (k != null && k.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  private List<Object> extractRequestBodyArgs(MethodSignature sig, Object[] args) {
    List<Object> bodies = new ArrayList<>();
    Parameter[] params = sig.getMethod().getParameters();
    for (int i = 0; i < params.length; i++) {
      for (Annotation an : params[i].getAnnotations()) {
        if (an.annotationType() == RequestBody.class) {
          bodies.add(args[i]);
          break;
        }
      }
    }
    return bodies;
  }

  private Object unwrapResponse(Object result) {
    if (result instanceof ResponseEntity<?> re) {
      // 상태/헤더/바디를 JSON 구조로 표현.
      Map<String, Object> printable = new LinkedHashMap<>();
      printable.put("_type", "ResponseEntity");
      printable.put("status", re.getStatusCode().value());

      if (properties.isLogResponseHeaders()) { // [CHANGED] 응답 헤더 출력 제어
        printable.put("headers", safeHeaders(re.getHeaders()));
      }

      if (properties.isLogResponseBody()) {    // [CHANGED] 응답 바디 출력 제어
        printable.put("body", re.getBody());
      } else {
        printable.put("body", "[omitted]");
      }
      return printable;
    }
    // 일반 객체 반환
    if (!properties.isLogResponseBody()) {
      return "[omitted]";
    }
    return result;
  }

  private Map<String, List<String>> safeHeaders(HttpHeaders headers) {
    Map<String, List<String>> h = new LinkedHashMap<>();
    headers.forEach((k, v) -> {
      if (properties.isMaskSensitive() && containsIgnoreCase(properties.getSensitiveKeys(), k)) {
        h.put(k, List.of(properties.getMaskReplacement()));
      } else {
        h.put(k, v);
      }
    });
    return h;
  }

  private Object sanitizeArgs(Object[] args) {
    if (args == null) {
      return null;
    }
    return Arrays.asList(args);
  }
}
