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

    log.info("\n\n{}\n\n", HEADER_LINE);

    if (logMonitoring.logParameters()) {
      String argsPretty = PrettyJson.toJsonOrToStringMasked(joinPoint.getArgs(), 2, properties.isMaskSensitive(), properties.getSensitiveKeys(), properties.getMaskReplacement());
      log.info("[METHOD] [RequestId: {}]\n-> {}.{} Args:\n {}",
        requestId, className, methodName, argsPretty.replace("\n", "\n  "));
    } else {
      log.info("[METHOD] [RequestId: {}]\n-> {}.{}", requestId, className, methodName);
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
          log.info("<- {}.{} Result ({} ms):\n {}", className, methodName, duration, truncated.replace("\n", "\n  "));
        } else if (logMonitoring.logExecutionTime()) {
          log.info("<- {}.{} ({} ms)", className, methodName, duration);
        }
      } else {
        log.error("x {}.{} Error ({} ms): {}", className, methodName, duration, throwable.getMessage(), throwable);
      }

      log.info("\n\n{}\n", FOOTER_LINE);
    }
  }

}
