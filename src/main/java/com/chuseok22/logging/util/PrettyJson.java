package com.chuseok22.logging.util;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MultipartFile;

@UtilityClass
public class PrettyJson {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private DefaultPrettyPrinter pp() {
    DefaultPrettyPrinter p = new DefaultPrettyPrinter();
    String indent = "  "; // [CHANGED] 들여쓰기 2 고정
    p.indentObjectsWith(new DefaultIndenter(indent, DefaultIndenter.SYS_LF));
    p.indentArraysWith(new DefaultIndenter(indent, DefaultIndenter.SYS_LF));
    return p;
  }

  public String toJsonOrToStringMasked(Object value,
    boolean maskSensitive,
    List<String> sensitiveKeys,
    String replacement) {
    if (value == null) {
      return "null";
    }
    try {
      JsonNode node = toSafeJson(value, maskSensitive, sensitiveKeys, replacement);
      return MAPPER.writer(pp()).writeValueAsString(node);
    } catch (Exception e) {
      String s = String.valueOf(value);
      if (s.length() > 1000) {
        return s.substring(0, 1000) + "...(생략됨)";
      }
      return s;
    }
  }

  public String tryPrettyAndMaskBody(String body,
    boolean maskSensitive,
    List<String> sensitiveKeys,
    String replacement) {
    if (body == null) {
      return "";
    }
    String trimmed = body.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    char c = trimmed.charAt(0);
    if (c != '{' && c != '[') {
      return body; // JSON 아니면 원문 반환
    }

    try {
      JsonNode node = MAPPER.readTree(body);
      if (maskSensitive && sensitiveKeys != null && !sensitiveKeys.isEmpty()) {
        maskRecursively(node, sensitiveKeys, replacement);
      }
      return MAPPER.writer(pp()).writeValueAsString(node);
    } catch (Exception e) {
      return body;
    }
  }

  private JsonNode toSafeJson(Object v,
    boolean maskSensitive,
    List<String> sensitiveKeys,
    String replacement) {
    if (v == null) {
      return NullNode.getInstance();
    }
    if (v instanceof InputStream) {
      return TextNode.valueOf("[InputStream]");
    }
    if (v instanceof OutputStream) {
      return TextNode.valueOf("[OutputStream]");
    }
    if (v instanceof HttpServletRequest) {
      return TextNode.valueOf("[HttpServletRequest]");
    }
    if (v instanceof HttpServletResponse) {
      return TextNode.valueOf("[HttpServletResponse]");
    }

    if (v instanceof MultipartFile f) {
      ObjectNode n = MAPPER.createObjectNode();
      n.put("_type", "MultipartFile");
      n.put("name", f.getName());
      n.put("originalFilename", f.getOriginalFilename());
      n.put("size", f.getSize());
      n.put("contentType", f.getContentType());
      return n;
    }

    if (v instanceof BindingResult br) {
      ObjectNode n = MAPPER.createObjectNode();
      n.put("_type", "BindingResult");
      n.put("errorCount", br.getErrorCount());
      ArrayNode errors = n.putArray("fieldErrors");
      for (FieldError fe : br.getFieldErrors()) {
        ObjectNode e = MAPPER.createObjectNode();
        e.put("field", fe.getField());
        e.put("code", fe.getCode());
        e.put("message", fe.getDefaultMessage());
        errors.add(e);
      }
      return n;
    }

    if (v instanceof Principal p) {
      ObjectNode n = MAPPER.createObjectNode();
      n.put("_type", "Principal");
      n.put("name", p.getName());
      return n;
    }

    if (v instanceof byte[] bytes) {
      ObjectNode n = MAPPER.createObjectNode();
      n.put("_type", "byte[]");
      n.put("length", bytes.length);
      return n;
    }

    if (v.getClass().isArray()) {
      int len = java.lang.reflect.Array.getLength(v);
      ArrayNode arr = MAPPER.createArrayNode();
      for (int i = 0; i < len; i++) {
        Object elem = java.lang.reflect.Array.get(v, i);
        arr.add(toSafeJson(elem, maskSensitive, sensitiveKeys, replacement));
      }
      return arr;
    }

    if (v instanceof Iterable<?> it) {
      ArrayNode arr = MAPPER.createArrayNode();
      for (Object e : it) {
        arr.add(toSafeJson(e, maskSensitive, sensitiveKeys, replacement));
      }
      return arr;
    }

    if (v instanceof Map<?, ?> m) {
      ObjectNode n = MAPPER.createObjectNode();
      for (Map.Entry<?, ?> e : m.entrySet()) {
        String key = String.valueOf(e.getKey());
        JsonNode child = toSafeJson(e.getValue(), maskSensitive, sensitiveKeys, replacement);
        if (maskSensitive && containsIgnoreCase(sensitiveKeys, key)) {
          n.put(key, replacement);
        } else {
          n.set(key, child);
        }
      }
      return n;
    }

    if (v instanceof ResponseEntity<?> re) {
      ObjectNode n = MAPPER.createObjectNode();
      n.put("_type", "ResponseEntity");
      n.put("status", re.getStatusCode().value());
      // 헤더/바디는 Aspect에서 정책적으로 포함/제외 결정
      n.set("headers", toSafeJson(re.getHeaders(), maskSensitive, sensitiveKeys, replacement));
      n.set("body", toSafeJson(re.getBody(), maskSensitive, sensitiveKeys, replacement));
      return n;
    }

    try {
      JsonNode node = MAPPER.valueToTree(v);
      if (maskSensitive && node.isObject()) {
        maskRecursively(node, sensitiveKeys, replacement);
      }
      return node;
    } catch (Exception ex) {
      String s = String.valueOf(v);
      if (s.length() > 1000) {
        s = s.substring(0, 1000) + "...(생략됨)";
      }
      return TextNode.valueOf(s);
    }
  }

  private void maskRecursively(JsonNode node, List<String> keys, String replacement) {
    if (node == null) {
      return;
    }
    if (node.isObject()) {
      ObjectNode obj = (ObjectNode) node;
      Iterator<String> it = obj.fieldNames();
      List<String> fields = new ArrayList<>();
      while (it.hasNext()) {
        fields.add(it.next());
      }
      for (String f : fields) {
        if (containsIgnoreCase(keys, f)) {
          obj.put(f, replacement);
        } else {
          JsonNode child = obj.get(f);
          maskRecursively(child, keys, replacement);
        }
      }
    } else if (node.isArray()) {
      for (JsonNode c : node) {
        maskRecursively(c, keys, replacement);
      }
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
}
