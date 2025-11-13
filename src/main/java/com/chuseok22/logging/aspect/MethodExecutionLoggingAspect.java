package com.chuseok22.logging.aspect;

import com.chuseok22.logging.annotation.LogMonitoring;
import com.chuseok22.logging.properties.HttpLoggingProperties;
import com.chuseok22.logging.util.LoggingUtil;
import com.chuseok22.logging.util.PrettyJson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;

@Aspect
@Slf4j
@RequiredArgsConstructor
public class MethodExecutionLoggingAspect {

  private final HttpLoggingProperties properties;

  private static final String HEADER_LINE = "==========================[메서드 로깅 시작]==========================";
  private static final String FOOTER_LINE = "==================================================================";

  @Around("@annotation(logMonitoring)")
  public Object logExecution(ProceedingJoinPoint joinPoint, LogMonitoring logMonitoring) throws Throwable {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    String className = signature.getDeclaringType().getSimpleName();
    String methodName = signature.getMethod().getName();

    String requestId = MDC.get(properties.getMdcKey());

    long startTime = System.currentTimeMillis();

    StringBuilder builder = new StringBuilder();
    builder.append("\n\n").append(HEADER_LINE).append("\n\n");

    if (logMonitoring.logParameters()) {
      String argsPretty = PrettyJson.toJsonOrToStringMasked(joinPoint.getArgs(), 2, properties.isMaskSensitive(), properties.getSensitiveKeys(), properties.getMaskReplacement());
      builder.append("[METHOD] [RequestId: ").append(requestId).append("]\n")
        .append("-> ").append(className).append(".").append(methodName).append(" Args:\n ")
        .append(argsPretty.replace("\n", "\n  "))
        .append("\n");
    } else {
      builder.append("[METHOD] [RequestId: ").append(requestId).append("]\n")
        .append("-> ").append(className).append(".").append(methodName)
        .append("\n");
    }

    Object result = null;
    Throwable throwable = null;

    try {
      result = joinPoint.proceed();
      return result;
    } catch (Throwable ex) {
      throwable = ex;
      throw ex;
    } finally {
      long duration = System.currentTimeMillis() - startTime;

      if (throwable == null) {
        if (logMonitoring.logResult()) {
          String resultPretty = PrettyJson.toJsonOrToStringMasked(result, 2, properties.isMaskSensitive(), properties.getSensitiveKeys(), properties.getMaskReplacement());
          String truncated = LoggingUtil.truncate(resultPretty, 4000);
          builder.append("<- ").append(className).append(".").append(methodName)
            .append(" Result (").append(duration).append(" ms):\n ")
            .append(truncated.replace("\n", "\n  "))
            .append("\n");
        } else if (logMonitoring.logExecutionTime()) {
          builder.append("<- ").append(className).append(".").append(methodName)
            .append(" (").append(duration).append(" ms)")
            .append("\n");
        }
      } else {
        builder.append("x ").append(className).append(".").append(methodName)
          .append(" Error (").append(duration).append(" ms): ")
          .append(throwable.getMessage())
          .append("\n");
      }
      builder.append("\n").append(FOOTER_LINE).append("\n");
      log.info(builder.toString());
    }
  }

}
