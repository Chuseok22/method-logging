package com.chuseok22.logging.properties;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "chuseok22.logging")
public class HttpLoggingProperties {

  // 라이브러리 전체 on/off
  private boolean enabled = true;

  // 요청 헤더 출력 여부
  private boolean logRequestHeaders = true;
  // 요청 바디 출력 여부
  private boolean logRequestBody = true;

  // 응답 헤더 출력 여부
  private boolean logResponseHeaders = true;
  // 응답 바디 출력 여부
  private boolean logResponseBody = true;

  private int maxBodyLength = 2000;

  private String correlationHeaderName = "X-Request-Id";
  private String mdcKey = "requestId";

  private boolean maskSensitive = true; // 민감 키 마스킹
  private List<String> sensitiveKeys = new ArrayList<String>(); // 민감 키 ex) password, authorization
  private String maskReplacement = "****";
}
