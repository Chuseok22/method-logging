package com.chuseok22.logging.aspect;

import com.chuseok22.logging.annotation.LogMonitoring;
import com.chuseok22.logging.properties.HttpLoggingProperties;
import com.chuseok22.logging.util.KeyValueFormatter;
import com.chuseok22.logging.util.LoggingUtil;
import com.chuseok22.logging.util.PrettyJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

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

    // =============== HTTP REQUEST ===============
    if (request != null) {
      String method = request.getMethod();
      String uri = request.getRequestURI();
      String queryString = request.getQueryString();
      String contentType = request.getContentType();
      Charset cs = resolve(request.getCharacterEncoding());

      b.append("[HTTP REQUEST] [RequestId: ").append(requestId).append("]\n");
      b.append("-> ").append(method).append(" ").append(uri).append("\n");

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

      Map<String, List<String>> qp = KeyValueFormatter.parseQuery(queryString, cs);
      b.append("  Query:\n").append(KeyValueFormatter.formatBlockMasked(
        qp, 2, properties.isMaskSensitive(), properties.getSensitiveKeys(), properties.getMaskReplacement()
      ));

      // @RequestBody 기반 바디 출력
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

    // =============== METHOD ARGS ===============
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

      // =============== RESPONSE / RESULT ===============
      if (thrown == null) {
        if (logMonitoring.logResult()) {
          Object printable = unwrapResponse(result);
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
        // === 간단 에러 출력: Exception / Status / (가능하면) Body ===
        Integer status = resolveStatus(thrown);
        Object errorBody = extractErrorBody(thrown); // 베스트에포트

        b.append("<- ").append(className).append(".").append(methodName)
          .append(" ERROR (").append(took).append(" ms):\n")
          .append("  Exception: ").append(thrown.getClass().getName()).append("\n");

        if (status != null) {
          b.append("  Status: ").append(status).append("\n");
        }

        if (errorBody != null) {
          String pretty = PrettyJson.toJsonOrToStringMasked(
            errorBody,
            properties.isMaskSensitive(),
            properties.getSensitiveKeys(),
            properties.getMaskReplacement()
          );
          pretty = LoggingUtil.truncate(pretty, properties.getMaxBodyLength());
          b.append("  Body:\n  ").append(pretty.replace("\n", "\n  ")).append("\n");
        } else {
          b.append("  Body: (omitted or handled by global exception handler)\n");
        }
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
      Map<String, Object> printable = new LinkedHashMap<>();
      printable.put("_type", "ResponseEntity");
      printable.put("status", re.getStatusCode().value());

      if (properties.isLogResponseHeaders()) {
        printable.put("headers", safeHeaders(re.getHeaders()));
      }
      printable.put("body", properties.isLogResponseBody() ? re.getBody() : "[omitted]");
      return printable;
    }
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

  private Integer resolveStatus(Throwable t) {
    if (t instanceof ResponseStatusException rse) {
      try {
        return rse.getStatusCode().value();
      } catch (Throwable ignore) {
      }
    }
    ResponseStatus rs = t.getClass().getAnnotation(ResponseStatus.class);
    if (rs != null) {
      try {
        HttpStatus code = rs.code() != HttpStatus.INTERNAL_SERVER_ERROR ? rs.code() : rs.value();
        return code.value();
      } catch (Throwable ignore) {
      }
    }
    return null;
  }

  // 예외에서 “응답 바디로 쓸 만한 것”을 최대한 추출 (없으면 null)
  private Object extractErrorBody(Throwable t) {
    // 1) ResponseStatusException: reason 사용
    if (t instanceof ResponseStatusException rse) {
      String reason = rse.getReason();
      if (reason != null) {
        return Map.of("reason", reason);
      }
    }

    // 2) 흔한 커스텀 예외 패턴: getBody(), getPayload(), getErrorResponse(), toResponseEntity()
    List<String> candidates = List.of("getBody", "getPayload", "getErrorResponse", "getResponse", "toResponseEntity");
    for (String name : candidates) {
      try {
        Method m = t.getClass().getMethod(name);
        Object o = m.invoke(t);
        if (o instanceof ResponseEntity<?> re) {
          return re.getBody();
        }
        if (o != null) {
          return o; // 맵/DTO 등
        }
      } catch (Throwable ignore) { /* 메서드 없음 or 실패 */ }
    }

    // 3) 마지막으로 메시지만 노출
    String msg = t.getMessage();
    return (msg != null && !msg.isBlank()) ? Map.of("message", msg) : null;
  }
}