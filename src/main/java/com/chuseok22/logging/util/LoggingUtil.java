package com.chuseok22.logging.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

@UtilityClass
public class LoggingUtil {

  public String getRequestId(HttpServletRequest request) {
    Object fromAttribute = request.getAttribute("RequestID");
    if (fromAttribute instanceof String) {
      return (String) fromAttribute;
    }
    Object fromLower = request.getAttribute("requestId");
    if (fromLower instanceof String) {
      return (String) fromLower;
    }
    String fromMdcLower = MDC.get("requestId");
    if (StringUtils.hasText(fromMdcLower)) {
      return fromMdcLower;
    }
    String fromMdcUpper = MDC.get("RequestID");
    if (StringUtils.hasText(fromMdcUpper)) {
      return fromMdcUpper;
    }
    return null;
  }

  public String truncate(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength) + "...(생략됨)";
  }

  public boolean isTextBody(String value) {
    if (value == null) {
      return false;
    }
    int length = value.length();
    for (int i = 0; i < length; i++) {
      char c = value.charAt(i);
      if (Character.isISOControl(c) && !Character.isWhitespace(c)) {
        return false;
      }
    }
    return true;
  }

  public boolean isFormUrlEncoded(String contentType) {
    if (contentType == null) {
      return false;
    }
    String lower = contentType.toLowerCase();
    return lower.startsWith("application/x-www-form-urlencoded");
  }

  public boolean isJson(String contentType) {
    if (contentType == null) {
      return false;
    }
    String lower = contentType.toLowerCase();
    return lower.contains("application/json") || lower.endsWith("+json");
  }

  public boolean isMultipart(String contentType) {
    if (contentType == null) {
      return false;
    }
    String lower = contentType.toLowerCase();
    return lower.startsWith("multipart/");
  }
}
