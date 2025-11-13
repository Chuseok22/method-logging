# Chuseok22 Method Logging (Spring Boot)

HTTP 요청/응답 + 메서드 실행 정보를 **한 번에 보기 좋게** 로깅하는 경량 라이브러리입니다.  
`@LogMonitoring`가 붙은 메서드에서만 동작하며, 개발자가 콘솔 로그만 보고도 **무슨 요청이 들어왔고, 어떤 인자가 전달되었으며, 무엇이 응답(또는 오류)** 되었는지 빠르게 파악할 수 있도록 도와줍니다.

---

## 주요 특징

- **어노테이션 기반 동작**: `@LogMonitoring`가 붙은 메서드에서만 로깅
- **HTTP 요청 섹션**: 메서드/URL, 헤더(마스킹 적용), 쿼리 파라미터, 요청 바디(JSON/Form/Multi-part 구분)
- **메서드 인자 섹션**: 실제 메서드로 전달된 인자들을 보기 좋게(JSON Pretty) 출력
- **응답/오류 섹션**: 정상 응답 또는 예외 요약(예외 클래스/상태코드/간단 바디) 출력
- **가독성 높은 박스 레이아웃**: 시작/끝 라인을 고정 포맷으로 출력해 로그 검색/구분이 쉬움
- **민감정보 마스킹**: Authorization, password 등 키워드 기반 값 마스킹
- **MDC 연동**: `requestId`를 자동 주입해 트레이싱에 유리

> 참고: HTTP 섹션은 웹 요청 컨텍스트에서만 출력됩니다. 스케줄러/비동기 등 **웹 요청이 아닌 실행**에서는 HTTP 섹션이 생략되고, **메서드 인자/응답(또는 오류)** 섹션만 출력됩니다.

---

## 호환성

- **JDK 17+** 권장
- Spring Boot 3.x (AOP 사용)
- Gradle(Groovy) 기반 예시 제공

---

## 빠른 시작 (Quick Start)

### 1) 의존성 추가

> **사설 Nexus** 저장소를 사용합니다. (예시는 릴리즈 리포지토리 기준)

**`build.gradle` (루트 or 서브프로젝트)**

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri("https://nexus.chuseok22.com/repository/maven-releases/")
        allowInsecureProtocol = true
    }
}

dependencies {
    implementation "com.chuseok22:logging:0.0.1"
    implementation "org.springframework.boot:spring-boot-starter-aop"
}
```

### 2) 기본 설정

**`src/main/resources/application.yml`**

```yaml
chuseok22:
  logging:
    enabled: true                       # 라이브러리 전체 on/off
    log-request-headers: true           # 요청 헤더 출력 여부
    log-request-body: true              # 요청 바디 출력 여부
    log-response-headers: true          # 응답 헤더 출력 여부
    log-response-body: true             # 응답 바디 출력 여부
    max-body-length: 2000               # 바디 출력 길이 제한(초과 시 잘라서 출력)
    correlation-header-name: X-Request-Id
    mdc-key: requestId
    mask-sensitive: true                # 민감 키 값 마스킹
    sensitive-keys:                     # (대소문자 무시) 키 이름이 일치하면 값 마스킹
      - authorization
      - cookie
      - set-cookie
      - password
      - access-token
      - refresh-token
      - x-api-key
    mask-replacement: "****"
```

### 3) 어노테이션 달고 사용

**컨트롤러/서비스 메서드에 `@LogMonitoring` 추가**

```java
import com.chuseok22.logging.annotation.LogMonitoring;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/member")
public class MemberController {

    @LogMonitoring
    @PostMapping("/withdraw")
    public ResponseEntity<Void> withdraw(@RequestBody WithdrawRequest request) {
        // ... 중략 ...
        return ResponseEntity.ok().build();
    }
}
```

> `@LogMonitoring(logParameters = true, logResult = true, logExecutionTime = true)`  
> 세 속성은 각각 “메서드 인자 출력 / 결과 출력 / 실행시간 출력” 여부를 제어합니다.  
> (기본값: 모두 `true`)

---

## 출력 예시

### 1) 정상 요청/응답

```
==========================[메서드 로깅 시작]==========================

[HTTP REQUEST] [RequestId: 0a1ef4bd92e842e9ac0bc8489fe8389c]
-> POST /api/member/withdraw
  Headers:
  - host: localhost:8080
  - authorization: ****
  - content-type: application/json;charset=UTF-8
  - accept: application/json;charset=UTF-8
  Query:
  (empty)
  Body:
  {
    "withdrawalReasonType" : "NO_CONCERTS",
    "otherReason" : "string"
  }

[METHOD] MemberController.withdraw Args:
  [
    {
      "withdrawalReasonType" : "NO_CONCERTS",
      "otherReason" : "string"
    }
  ]

<- MemberController.withdraw Result (18 ms):
  {
    "_type" : "ResponseEntity",
    "status" : 200,
    "headers" : { },
    "body" : null
  }

==================================================================
```

### 2) 예외 발생(간략 출력)

```
==========================[메서드 로깅 시작]==========================

[HTTP REQUEST] [RequestId: 694a71ef50a64c9aa2a1b24b4c9951b2]
-> POST /mock/login
  Headers:
  - authorization: ****
  - content-type: multipart/form-data; boundary=----WebKitFormBoundary...
  Query:
  (empty)
  Body: [multipart] (files/parts omitted)

[METHOD] MockController.socialLogin Args:
  [
    {
      "username" : "string@naver.com",
      "role" : "ROLE_ADMIN",
      "socialPlatform" : "NORMAL"
    }
  ]

<- MockController.socialLogin ERROR (7 ms):
  Exception: com.ticketmate.backend.common.application.exception.CustomException
  Status: 400
  Body:
  {
    "message" : "잘못된 회원 권한 요청입니다."
  }

==================================================================
```

> 오류 시에는 **예외 클래스 이름, 상태코드(가능할 때), 간단한 바디/메시지**만 출력됩니다.  
> 글로벌 예외 처리기가 별도 응답을 만든 경우에도, 가능한 범위에서 간략 바디를 베스트에포트로 출력합니다.

---

## 구성 옵션(요약표)

| 프로퍼티 | 타입 | 기본값 | 설명 |
|---|---|---:|---|
| `chuseok22.logging.enabled` | boolean | `true` | 라이브러리 전체 On/Off |
| `chuseok22.logging.log-request-headers` | boolean | `true` | 요청 헤더 출력 |
| `chuseok22.logging.log-request-body` | boolean | `true` | 요청 바디 출력(JSON/Form/Multi-part 구분) |
| `chuseok22.logging.log-response-headers` | boolean | `true` | 응답 헤더 출력 |
| `chuseok22.logging.log-response-body` | boolean | `true` | 응답 바디 출력 |
| `chuseok22.logging.max-body-length` | int | `2000` | 바디 출력 길이 제한(초과 시 생략 표시) |
| `chuseok22.logging.correlation-header-name` | string | `X-Request-Id` | 응답 헤더로도 반환되는 상관관계 ID 헤더명 |
| `chuseok22.logging.mdc-key` | string | `requestId` | MDC 키 이름 |
| `chuseok22.logging.mask-sensitive` | boolean | `true` | 민감 키 값 마스킹 사용 여부 |
| `chuseok22.logging.sensitive-keys` | list | `[]` | 마스킹 대상 키 목록(대소문자 무시) |
| `chuseok22.logging.mask-replacement` | string | `****` | 마스킹 대체 문자열 |

> **민감 키 마스킹 팁**: `authorization`, `cookie`, `set-cookie`, `password`, `access-token`, `refresh-token`, `x-api-key` 등을 등록하는 것을 권장합니다.

---

## 동작 규칙

- `@LogMonitoring`가 붙은 메서드에 한해서만 로깅합니다.
- 웹 요청 컨텍스트가 있으면 **HTTP REQUEST** 섹션을 먼저 출력합니다.
- 이후 **METHOD Args** 섹션을 출력합니다(`logParameters=true`일 때).
- 정상 종료 시 **Result** 섹션을, 예외 시 **ERROR** 섹션(간략)을 출력합니다.
- 모든 출력은 **한 번의 `INFO` 로그 호출**로 묶여 박스 형태로 기록됩니다.
- 멀티파트 요청은 바디 내용을 실제로 읽지 않으며, `"[multipart] (files/parts omitted)"`로 표기합니다.
- JSON/Form은 보기 좋게 포맷팅되어 출력되며, 길이가 너무 길면 `max-body-length` 기준으로 생략됩니다.

---

## 자주 묻는 질문 (FAQ)

**Q. 로그가 안 보여요.**  
A. 다음을 확인하세요.
1) `chuseok22.logging.enabled=true` 인지,
2) `@LogMonitoring`를 메서드에 달았는지,
3) `spring-boot-starter-aop`가 포함되어 있는지,
4) 로깅 레벨/패턴이 정상인지.

**Q. 예외 응답이 상세하게 안 나와요.**  
A. AOP는 컨트롤러 **메서드 경계**를 감쌀 뿐, 글로벌 예외 처리기에서 최종 바디를 만들기 전에 제어가 종료될 수 있습니다. 라이브러리는 **가능한 범위**에서 예외 정보를 간단히 요약합니다. 더 자세한 바디를 원하면, 커스텀 예외에 `getPayload()`/`getBody()` 등의 접근자를 제공하는 방식을 권장합니다.

**Q. 운영 환경에서 써도 되나요?**  
A. 가능합니다. 다만 PII/토큰 등 민감정보 노출을 막기 위해 **마스킹 키 목록**을 충분히 구성하고, 필요 시 `log-request-body`/`log-response-body`를 비활성화하는 것을 권장합니다.

---

## 릴리즈/버전

- 그룹: `com.chuseok22`
- 아티팩트: `logging`
- 버전: 예) `0.0.1`

팀 Nexus 정책에 맞춰 SNAPSHOT/RELEASE를 구분해 사용하세요.

---

## 라이선스

사내용/프로젝트 정책에 따릅니다.
