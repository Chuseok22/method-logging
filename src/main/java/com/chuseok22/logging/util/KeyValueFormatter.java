package com.chuseok22.logging.util;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class KeyValueFormatter {

  public Map<String, List<String>> parseQuery(String query, Charset charset) {
    Map<String, List<String>> map = new LinkedHashMap<>();
    if (query == null || query.isEmpty()) {
      return map;
    }
    String[] parts = query.split("&");
    for (String part : parts) {
      int index = part.indexOf('=');
      String key;
      String value;
      if (index >= 0) {
        key = URLDecoder.decode(part.substring(0, index), charset);
        value = URLDecoder.decode(part.substring(index + 1), charset);
      } else {
        key = URLDecoder.decode(part, charset);
        value = "";
      }
      List<String> list = map.get(key);
      if (list == null) {
        list = new ArrayList<>();
        map.put(key, list);
      }
      list.add(value);
    }
    return map;
  }

  public Map<String, List<String>> parseFormUrlEncoded(String body, Charset charset) {
    return parseQuery(body, charset);
  }

  public String formatBlockMasked(Map<String, List<String>> map, int indentSize, boolean maskSensitive, List<String> sensitiveKeys, String replacement) {
    String indent = buildIndent(indentSize);
    if (map == null || map.isEmpty()) {
      return "(empty)\n";
    }
    StringBuilder builder = new StringBuilder();
    for (Map.Entry<String, List<String>> entry : map.entrySet()) {
      String key = entry.getKey();
      List<String> values = entry.getValue();
      builder.append(indent).append("- ").append(key).append(": ");
      if (values == null || values.isEmpty()) {
        builder.append("\n");
      } else if (values.size() == 1) {
        String value = values.get(0);
        if (maskSensitive && containsIgnoreCase(sensitiveKeys, key)) {
          value = replacement;
        }
        builder.append(value).append("\n");
      } else {
        List<String> masked = new ArrayList<>();
        for (String value : values) {
          if (maskSensitive && containsIgnoreCase(sensitiveKeys, key)) {
            masked.add(replacement);
          } else {
            masked.add(value);
          }
        }
        builder.append(masked).append("\n");
      }
    }
    return builder.toString();
  }

  private static boolean containsIgnoreCase(List<String> keys, String name) {
    if (keys == null || name == null) {
      return false;
    }
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
