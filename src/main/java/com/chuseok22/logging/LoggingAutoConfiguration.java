package com.chuseok22.logging;

import com.chuseok22.logging.aspect.MethodExecutionLoggingAspect;
import com.chuseok22.logging.filter.CorrelationFilter;
import com.chuseok22.logging.filter.RequestResponseLoggingFilter;
import com.chuseok22.logging.properties.HttpLoggingProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(HttpLoggingProperties.class)
@ConditionalOnWebApplication(type = Type.SERVLET)
public class LoggingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public CorrelationFilter correlationFilter(HttpLoggingProperties properties) {
    return new CorrelationFilter(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public RequestResponseLoggingFilter requestResponseLoggingFilter(HttpLoggingProperties properties) {
    return new RequestResponseLoggingFilter(properties);
  }

  @Bean
  @ConditionalOnMissingBean
  public MethodExecutionLoggingAspect methodExecutionLoggingAspect(HttpLoggingProperties properties) {
    return new MethodExecutionLoggingAspect(properties);
  }
}
