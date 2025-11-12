package com.chuseok22.logging.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PrettyJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public boolean isLikelyJson(String body) {
    if (body == null) {
      return false;
    }
    String trimmed = body.trim();
    if (trimmed.isEmpty()) {
      return false;
    }
    char first = trimmed.charAt(0);
    return first == '{' || first == '[';
  }

  public String tryPretty(String body, int indentSize) {
    if (body == null) {
      return "";
    }
    if (!isLikelyJson(body)) {
      return body;
    }
    try {
      JsonNode node = MAPPER.readTree(body);
      DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
      String indentString = buildIndent(indentSize);
      prettyPrinter.indentObjectsWith(new DefaultIndenter(indentString, DefaultIndenter.SYS_LF));
      prettyPrinter.indentArraysWith(new DefaultIndenter(indentString, DefaultIndenter.SYS_LF));
      return MAPPER.writer(prettyPrinter).writeValueAsString(node);
    } catch (Exception e) {
      return body;
    }
  }

  public String tryPrettyAndMask(String body, int indentSize, boolean maskSensitive, List<String> sensitiveKeys, String replacement) {
    if (body == null) {
      return "";
    }
    if (!isLikelyJson(body)) {
      return body;
    }
    try {
      JsonNode node = MAPPER.readTree(body);
      if (maskSensitive && sensitiveKeys != null && !sensitiveKeys.isEmpty()) {
        maskRecursively(node, sensitiveKeys, replacement);
      }
      DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
      String indentString = buildIndent(indentSize);
      prettyPrinter.indentObjectsWith(new DefaultIndenter(indentString, DefaultIndenter.SYS_LF));
      prettyPrinter.indentArraysWith(new DefaultIndenter(indentString, DefaultIndenter.SYS_LF));
      return MAPPER.writer(prettyPrinter).writeValueAsString(node);
    } catch (Exception e) {
      return body;
    }
  }

  public String toJsonOrToStringMasked(Object value, int indentSize, boolean maskSensitive, List<String> sensitiveKeys, String replacement) {
    if (value == null) {
      return "null";
    }
    try {
      JsonNode node = MAPPER.valueToTree(value);
      if (maskSensitive && sensitiveKeys != null && !sensitiveKeys.isEmpty()) {
        maskRecursively(node, sensitiveKeys, replacement);
      }
      DefaultPrettyPrinter prettyPrinter = prettyPrinter(indentSize);
      return MAPPER.writer(prettyPrinter).writeValueAsString(node);
    } catch (Exception e) {
      try {
        DefaultPrettyPrinter prettyPrinter = prettyPrinter(indentSize);
        return MAPPER.writer(prettyPrinter).writeValueAsString(String.valueOf(value));
      } catch (JsonProcessingException ex) {
        String str = String.valueOf(value);
        if (str.length() > 1000) {
          return str.substring(0, 1000) + "...(생략됨)";
        }
        return str;
      }
    }
  }

  private DefaultPrettyPrinter prettyPrinter(int indentSize) {
    DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
    String indentString = buildIndent(indentSize);
    prettyPrinter.indentObjectsWith(new DefaultIndenter(indentString, DefaultIndenter.SYS_LF));
    prettyPrinter.indentArraysWith(new DefaultIndenter(indentString, DefaultIndenter.SYS_LF));
    return prettyPrinter;
  }

  private void maskRecursively(JsonNode node, List<String> keys, String replacement) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      ObjectNode objectNode = (ObjectNode) node;
      Iterator<String> fieldNames = objectNode.fieldNames();
      while (fieldNames.hasNext()) {
        String name = fieldNames.next();
        JsonNode child = objectNode.get(name);
        if (containsIgnoreCase(keys, name)) {
          objectNode.put(name, replacement);
        } else {
          maskRecursively(child, keys, replacement);
        }
      }
    } else if (node.isArray()) {
      for (JsonNode item : node) {
        maskRecursively(item, keys, replacement);
      }
    }
  }

  private boolean containsIgnoreCase(List<String> keys, String name) {
    for (String key : keys) {
      if (key != null && key.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }

  private String buildIndent(int size) {
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < size; i++) {
      builder.append(' ');
    }
    return builder.toString();
  }
}
