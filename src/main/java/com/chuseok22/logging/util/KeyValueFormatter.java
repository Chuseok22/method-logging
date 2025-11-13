package com.chuseok22.logging.util;

import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
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
      map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }
    return map;
  }

  public Map<String, List<String>> fromParamMap(Map<String, String[]> paramMap) {
    Map<String, List<String>> map = new LinkedHashMap<>();
    if (paramMap == null) {
      return map;
    }
    for (Map.Entry<String, String[]> e : paramMap.entrySet()) {
      map.put(e.getKey(), e.getValue() == null ? List.of() : Arrays.asList(e.getValue()));
    }
    return map;
  }

  public String formatBlockMasked(Map<String, List<String>> map,
    int indentSize,
    boolean maskSensitive,
    List<String> sensitiveKeys,
    String replacement) {
    String indent = " ".repeat(Math.max(0, indentSize));
    if (map == null || map.isEmpty()) {
      return "(empty)\n";
    }

    StringBuilder b = new StringBuilder();
    for (Map.Entry<String, List<String>> e : map.entrySet()) {
      String key = e.getKey();
      List<String> values = e.getValue();
      b.append(indent).append("- ").append(key).append(": ");
      if (values == null || values.isEmpty()) {
        b.append("\n");
      } else if (values.size() == 1) {
        String v = values.get(0);
        if (maskSensitive && containsIgnoreCase(sensitiveKeys, key)) {
          v = replacement;
        }
        b.append(v).append("\n");
      } else {
        List<String> masked = new ArrayList<>();
        for (String v : values) {
          masked.add(maskSensitive && containsIgnoreCase(sensitiveKeys, key) ? replacement : v);
        }
        b.append(masked).append("\n");
      }
    }
    return b.toString();
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
}
