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

  private boolean enabled = true;
  private boolean httpFilterEnabled = true;
  private boolean logRequestBody = true;
  private boolean logResponseBody = true;
  private boolean logHeaders = true;
  private int maxBodyLength = 2000;
  private List<String> excludedPaths = new ArrayList<String>();
  private String correlationHeaderName = "X-Request-Id";
  private String mdcKey = "requestId";
  private boolean multiline = true; // 멀티라인
  private boolean prettyJson = true; // JSON pretty
  private int indentSize = 2; // 들여쓰기 크기
  private boolean prettyQueryParams = true; // 쿼리 파라미터 키=값 블록
  private boolean prettyFormBody = true; // form-urlencoded 바디 키=값 블록
  private boolean maskSensitive = true; // 민감 키 마스킹
  private List<String> sensitiveKeys = new ArrayList<String>(); // 민감 키 ex) password, authorization
  private String maskReplacement = "****";
}
