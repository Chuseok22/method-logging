# chuseok22-logging (Servlet HTTP & Method Logger)

스프링 부트(서블릿 MVC)에서 **HTTP 요청/응답**과 **메서드 호출(파라미터·반환값·실행시간)**을
한눈에 보기 좋게 로깅하는 경량 라이브러리입니다.  
JSON은 프리티 프린트/마스킹, `application/x-www-form-urlencoded`는 키=값 블록으로 예쁘게 정리하며,
멀티파트/바이너리는 자동으로 생략합니다.

> 요구사항: **Java 21**, **Spring Boot 3.x (Servlet)**  
> 비대상: Spring WebFlux(리액티브)는 본 라이브러리의 필터/캐싱 방식과 호환되지 않습니다.

---

## ✨ 주요 기능

- **HTTP 인/아웃 로깅**
  - 메서드, URL, 쿼리 파라미터(블록 표기), 헤더(옵션), 바디(옵션), 상태코드, 소요시간
  - JSON **프리티 출력**(+ 민감키 **마스킹**) / `application/x-www-form-urlencoded`는 **키=값 블록**
  - **멀티파트·바이너리 자동 미출력** (업로드/다운로드 안전)
  - **RequestId(MDC)** 헤더 자동 주입 (`X-Request-Id` 기본값)

- **메서드 레벨 로깅**
  - `@LogMonitoring`로 메서드 **파라미터/결과/실행시간** 로깅
  - 결과/인자에도 JSON 프리티 + 민감키 마스킹 적용

- **운영 친화적인 설정**
  - **경로 제외 패턴**(Ant 스타일) / **본문 길이 제한** / **헤더 로깅 on/off**
  - **원라인/멀티라인** 로그 모드
  - **민감키 마스킹**(헤더/바디/쿼리/폼/메서드 인자·반환값)

---

## 🔧 설치 (소비 프로젝트)

### 1) Nexus 저장소 등록

```groovy
repositories {
    maven {
        url "https://nexus.chuseok22.com/repository/maven-releases/"
    }
    maven {
        url "https://nexus.chuseok22.com/repository/maven-snapshots/"
    }
}
```

### 2) 의존성 추가

```groovy
dependencies {
    implementation "com.chuseok22:method-logging:<버전>"
    // 예: implementation "com.chuseok22:method-logging:0.1.0"
}
```

> 스냅샷을 쓰려면 `-SNAPSHOT` 버전을 사용하세요.

---

## ⚙️ 설정 (application.yml)

기본 prefix: **`chuseok22.logging`**

```yaml
chuseok22:
  logging:
    enabled: true                 # 전역 on/off
    multiline: true               # true: 멀티라인, false: 원라인
    pretty-json: true             # JSON 프리티 프린트
    indent-size: 2                # 프리티 들여쓰기 공백수
    log-headers: true             # 요청/응답 헤더 로깅
    log-request-body: true        # 요청 바디 로깅
    log-response-body: true       # 응답 바디 로깅
    pretty-query-params: true     # 쿼리 키=값 블록 표기
    pretty-form-body: true        # form 바디 키=값 블록 + 원문 억제
    max-body-length: 4000         # 바디 최대 길이(초과분은 …truncated)
    mask-sensitive: true          # 민감키 마스킹(헤더/바디/쿼리/폼/메서드)
    sensitive-keys:               # 대소문자 무시 매칭
      - authorization
      - cookie
      - set-cookie
      - password
      - accessToken
      - refreshToken
      - secret
    mask-replacement: "****"      # 마스킹 대체문자
    excluded-paths:               # 로깅 제외 경로(Ant 패턴)
      - /actuator/health
      - /actuator/prometheus
    correlation-header-name: X-Request-Id # 상호연관 헤더명
    mdc-key: requestId                      # MDC 보관 키
```

### 로깅 레벨 권장값

```yaml
logging:
  level:
    com.chuseok22.logging.filter: INFO
    com.chuseok22.logging.aspect: DEBUG
```

---

## 🧩 사용 방법

### 1) HTTP 요청/응답 자동 로깅

`OncePerRequestFilter` 기반의 필터가 자동으로 아래를 로깅합니다.

- 요청: 메서드/URL/쿼리/헤더(옵션)/바디(옵션)
- 응답: 상태코드/소요시간/헤더(옵션)/바디(옵션)
- `multipart/*` 또는 바이너리 바디는 자동 생략

> **쿼리·폼 바디**는 키=값 블록으로 가독성 좋게 출력됩니다.  
> **JSON 바디**는 프리티 프린트 + 민감키 마스킹 후 출력됩니다.

### 2) 메서드 실행 로깅 (@LogMonitoring)

```java
import com.chuseok22.logging.annotation.LogMonitoring;

@LogMonitoring                       // 클래스에 부여하면 모든 public 메서드 적용
public class UserService {

  @LogMonitoring(logParameters = true, logResult = true, logExecutionTime = true)
  public UserDto findUser(UserQuery query) {
      ...
  }
}
```

- `logParameters`: 메서드 인자 로깅 (JSON 프리티/마스킹 적용)
- `logResult`    : 반환값 로깅 (JSON 프리티/마스킹 적용)
- `logExecutionTime`: 실행시간(ms) 로깅

> 예외 발생 시 스택트레이스와 함께 `ERROR` 레벨로 출력합니다.

---

## 🧪 출력 예시

### 1) HTTP (멀티라인 모드)

```
[HTTP] [RequestId: 7c1f5f2ef3a24b89b8d43a]
-> Request: POST /api/login
  Headers-Request:
    - content-type: application/x-www-form-urlencoded
    - authorization: ****
  Query:
  (empty)
  Form:
    - username: alice
    - password: ****
  Body: (suppressed, see Form)
<- Response: 200 (18 ms)
  Headers-Response:
    - content-type: application/json
    - set-cookie: ****
  Body:
  {
    "token" : "****",
    "expiresIn" : 3600
  }
```

### 2) 메서드 (@LogMonitoring)

```
[METHOD] [RequestId: 7c1f5f2ef3a24b89b8d43a]
-> AuthService.login Args:
  [
    {
      "username" : "alice",
      "password" : "****"
    }
  ]
<- AuthService.login Result (12 ms):
  {
    "token" : "****",
    "expiresIn" : 3600
  }
```

### 3) 원라인 모드 예시

```
[HTTP] [RequestId: 5a8c...] POST /api/users?active=true => Status=200, DurationMs=7
```

---

## 🛡️ 보안/성능 가이드

- **민감 데이터**: `mask-sensitive`를 **true**로 두고 `sensitive-keys`를 서비스 규칙에 맞게 보강하세요.
  - 대상: 헤더/쿼리/폼/JSON 바디/메서드 인자·반환값
- **바디 크기 제한**: `max-body-length`를 환경에 맞게 조정(운영 축소 권장).
- **대용량/스트리밍 응답**: `ContentCachingResponseWrapper` 특성상 완전 캐싱이 필요합니다.
  - SSE/NDJSON 등 스트리밍은 로깅을 끄거나 **excluded-paths**로 제외하는 것을 권장.
- **멀티파트/바이너리**: 자동 미출력. 파일 내용은 로그에 남지 않습니다.
- **운영 레벨**: HTTP 로그는 `INFO`, 메서드 로그는 `DEBUG` 권장(노이즈 제어).

---

## 🧷 경로 제외(필터 무시)

`excluded-paths`에 Ant 패턴으로 등록하면 로깅을 완전히 건너뜁니다.

```yaml
excluded-paths:
  - /health
  - /actuator/**
  - /static/**
```

---

## 🧰 커스터마이징 체크리스트

| 키 | 타입 | 기본값 | 설명 |
|---|---|---|---|
| `enabled` | boolean | `true` | 라이브러리 전역 on/off |
| `multiline` | boolean | `true` | 로그를 멀티라인 블록으로 출력 |
| `pretty-json` | boolean | `true` | JSON 프리티 프린트 |
| `indent-size` | int | `2` | 프리티 들여쓰기 공백 수 |
| `log-headers` | boolean | `true` | 요청/응답 헤더 출력 |
| `log-request-body` | boolean | `true` | 요청 바디 출력 |
| `log-response-body` | boolean | `true` | 응답 바디 출력 |
| `pretty-query-params` | boolean | `true` | 쿼리 키=값 블록 |
| `pretty-form-body` | boolean | `true` | form 바디 블록 + 원문 억제 |
| `max-body-length` | int | `2000` | 바디 길이 상한(초과 시 `...(truncated)`) |
| `mask-sensitive` | boolean | `true` | 민감키 마스킹 활성화 |
| `sensitive-keys` | list | `[]` | 마스킹 대상 키 목록(대소문자 무시) |
| `mask-replacement` | string | `"****"` | 마스킹 치환 문자열 |
| `excluded-paths` | list | `[]` | 로깅 제외 경로(Ant 패턴) |
| `correlation-header-name` | string | `"X-Request-Id"` | 상호연관 헤더 이름 |
| `mdc-key` | string | `"requestId"` | MDC 저장 키명 |

---

## 🪪 상호연관 ID(CorrelationId)

- 요청 헤더에 `X-Request-Id`가 없으면 라이브러리가 자동으로 생성하여 **응답 헤더로도 반환**합니다.
- MDC에 `requestId` 키로 넣기 때문에, 로거 패턴에 `%X{requestId}`를 넣으면 모든 로그 라인에 같이 표시할 수 있습니다.

```yaml
logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger - %msg %X{requestId}%n"
```

---

## 🧭 트러블슈팅

- **로그가 안 찍힘**: 패키지 레벨을 확인하세요. (`com.chuseok22.logging.filter`, `com.chuseok22.logging.aspect`)
- **바디가 비어있음**: `multipart/*` 또는 바이너리일 수 있습니다. 혹은 `log-request-body`/`log-response-body` 설정을 확인하세요.
- **쿼리/폼이 안 예쁘게 보임**: `pretty-query-params`/`pretty-form-body`가 `true`인지 확인하세요.
- **민감정보 노출**: `mask-sensitive`가 `true`인지, `sensitive-keys`에 키가 등록되었는지 확인하세요.

---

## 🧱 제약/주의

- Servlet 스택 전용입니다(WebFlux 미지원).
- `ContentCaching*Wrapper` 특성상 **대용량 스트리밍** 응답은 성능에 영향을 줄 수 있습니다.
- 바이너리/멀티파트 바디는 기본적으로 로깅하지 않습니다.

---

## 📬 문의/기여

- 민감키 규칙 강화, WebClient/Feign 아웃바운드 로깅, 헤더 화이트리스트/블랙리스트 옵션 등 확장을 원하면 이슈를 남겨주세요.
- 내부 규칙에 맞춘 **사전/사후 마스킹** 훅 추가도 고려하고 있습니다.
