# JWT DenyList 마이그레이션 계획

## 📋 전체 일정

| Phase | 작업 내용 | 담당 |
|-------|----------|------|
| **Phase 0** | 사전 준비 및 환경 설정 | Backend |
| **Phase 1** | DenyList 서비스 구현 | Backend |
| **Phase 2** | 인증 플로우 수정 | Backend |
| **Phase 3** | HTTP-Only Cookie 전환 | Backend + Frontend |
| **Phase 4** | 테스트 및 검증 | QA |
| **Phase 5** | 배포 및 모니터링 | DevOps |
| **Phase 6** | 기존 코드 정리 | Backend |

---

## Phase 0: 사전 준비

### 0.1 Git 브랜치 전략

```bash
# Feature 브랜치 생성
git checkout -b feature/jwt-denylist

# 또는 세부 브랜치들
git checkout -b feature/jwt-denylist-phase1
git checkout -b feature/jwt-denylist-phase2
git checkout -b feature/jwt-denylist-phase3
```

### 0.2 백업 및 롤백 계획

**체크리스트:**

- [X] 현재 코드 백업
  ```bash
  git tag backup-before-jwt-denylist
  git push origin backup-before-jwt-denylist
  ```

- [X] MongoDB Session 데이터 백업 (필요시)
  ```bash
  mongodump --db chatapp --collection sessions --out ./backup
  ```

- [X] 롤백 시나리오 문서화
  - Feature Flag 사용 고려
  - 점진적 배포 계획

### 0.3 의존성 확인

**필요한 의존성:**

```xml
<!-- pom.xml 확인 -->
<!-- 1. Redisson (이미 있음) -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson</artifactId>
    <version>3.38.1</version>
</dependency>

<!-- 2. Spring Data Redis (추가 필요 시) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

---

## Phase 1: DenyList 서비스 구현

### 1.1 Redis Configuration 작성

**작업:**

- [X] `RedisConfig.java` 생성 또는 수정
  - 위치: `src/main/java/com/ktb/chatapp/config/RedisConfig.java`
  - RedisTemplate Bean 등록
  - String Serializer 설정

**검증:**
```bash
# 컴파일 확인
./mvnw compile

# Redis 연결 테스트 실행
./mvnw test -Dtest=RedisConfigTest
```

### 1.2 JwtDenyListService 구현

**작업:**

- [X] `JwtDenyListService.java` 생성
  - 위치: `src/main/java/com/ktb/chatapp/service/JwtDenyListService.java`
  - `denyToken()` 메서드 구현
  - `isTokenDenied()` 메서드 구현
  - `incrementSessionVersion()` 메서드 구현
  - `getCurrentSessionVersion()` 메서드 구현
  - `isSessionVersionValid()` 메서드 구현

**검증:**
```bash
# 단위 테스트 작성 및 실행
./mvnw test -Dtest=JwtDenyListServiceTest
```

### 1.3 단위 테스트 작성

**작업:**

- [X] `JwtDenyListServiceTest.java` 작성
  - 위치: `src/test/java/com/ktb/chatapp/service/JwtDenyListServiceTest.java`
  - DenyToken 테스트
  - SessionVersion 테스트
  - TTL 만료 테스트
  - Redis 연결 실패 시 동작 테스트

**검증:**
```bash
# 전체 테스트 실행
./mvnw test
```

---

## Phase 2: 인증 플로우 수정

### 2.1 JwtService 수정

**작업:**

- [] `JwtService.java` 수정
  - JTI (JWT ID) 생성 및 추가
  - SessionVersion claim 추가
  - `extractJti()` 메서드 추가
  - `validateToken()` 메서드에 DenyList 확인 로직 추가
  - `validateToken()` 메서드에 SessionVersion 확인 로직 추가

**주의사항:**
- 기존 토큰과 호환성 유지 (sessionVersion이 없는 토큰도 허용)
- JTI는 UUID.randomUUID() 사용

**검증:**
```bash
# JwtService 테스트 실행
./mvnw test -Dtest=JwtServiceTest
```

### 2.2 SessionAwareJwtAuthenticationConverter 수정

**작업:**

- [X] `SessionAwareJwtAuthenticationConverter.java` 수정
  - SessionService 의존성 제거
  - JwtDenyListService 의존성 추가
  - DenyList 확인 로직 추가
  - SessionVersion 확인 로직 추가
  - sessionId 제거 (JWT details에서)

**주의사항:**
- 예외 처리 명확히 (SessionExpiredException)
- 로깅 추가로 디버깅 용이하게

**검증:**
```bash
# 인증 테스트 실행
./mvnw test -Dtest=SessionAwareJwtAuthenticationConverterTest
```

### 2.3 AuthController 수정

**작업:**

- [X] `AuthController.java` 수정
  - SessionService 제거, JwtDenyListService 추가
  - `login()` 메서드:
    - SessionVersion 증가 로직 추가
    - SessionService 제거
    - sessionId 응답 제거
  - `logout()` 메서드:
    - DenyList 추가 로직 구현
    - x-session-id 헤더 검증 제거
    - JWT에서 JTI 추출
  - `verify-token` 메서드:
    - Session 검증 제거
    - JWT 검증만 수행
  - `refresh-token` 메서드:
    - 기존 토큰 DenyList 추가
    - Session 관련 로직 제거

**주의사항:**
- 로그인 시 단일 세션 정책 유지 (SessionVersion 증가)
- 로그아웃 시 토큰 TTL 계산 정확히
- 에러 메시지 일관성 유지

**검증:**
```bash
# AuthController 테스트 실행
./mvnw test -Dtest=AuthControllerTest
```

### 2.4 통합 테스트

**작업:**

- [X] 로그인 플로우 테스트
  ```bash
  # 로그인 요청
  curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"test@example.com","password":"password123"}'

  # JWT 토큰 확인
  # SessionVersion이 Redis에 저장되었는지 확인
  redis-cli GET "jwt:session:version:USER_ID"
  ```

- [X] 로그아웃 플로우 테스트
  ```bash
  # 로그아웃 요청
  curl -X POST http://localhost:8080/api/auth/logout \
    -H "Authorization: Bearer YOUR_JWT_TOKEN"

  # DenyList 확인
  redis-cli KEYS "jwt:denylist:*"
  ```

- [X] 단일 세션 정책 테스트
  ```bash
  # 1. 첫 로그인 → JWT1
  # 2. 두 번째 로그인 → JWT2
  # 3. JWT1으로 API 요청 → 401 (SessionVersion 불일치)
  # 4. JWT2로 API 요청 → 200 (성공)
  ```

---

## Phase 3: HTTP-Only Cookie 전환

### 3.1 CookieUtil 구현

**작업:**

- [X] `CookieUtil.java` 생성
  - 위치: `src/main/java/com/ktb/chatapp/util/CookieUtil.java`
  - `addJwtCookie()` 메서드 구현
  - `deleteJwtCookie()` 메서드 구현
  - `getJwtFromCookie()` 메서드 구현

**Cookie 설정:**
- HttpOnly: true
- Secure: true (프로덕션)
- SameSite: Strict
- Path: /
- MaxAge: JWT 만료시간과 동일

### 3.2 CustomBearerTokenResolver 수정

**작업:**

- [X] `CustomBearerTokenResolver.java` 수정
  - Cookie에서 JWT 추출 로직 추가 (1순위)
  - Authorization 헤더 지원 (2순위, 하위 호환)
  - x-auth-token 헤더 지원 (3순위, 하위 호환)

**검증:**
```bash
# Cookie 우선순위 테스트
# 1. Cookie만 있을 때
# 2. Cookie + Header 둘 다 있을 때 (Cookie 우선)
# 3. Header만 있을 때
```

### 3.3 AuthController Cookie 응답 추가

**작업:**

- [X] `AuthController.java` 수정
  - `login()` 메서드에 HttpServletResponse 파라미터 추가
  - JWT를 Cookie에 설정
  - `logout()` 메서드에 Cookie 삭제 로직 추가
  - `refresh-token()` 메서드에 Cookie 갱신 로직 추가

### 3.4 SecurityConfig CORS 수정

**작업:**

- [X] `SecurityConfig.java` 수정
  - `allowCredentials: true` 설정
  - `allowedOrigins: "*"` 제거
  - 구체적인 도메인 명시 (예: http://localhost:3000)

**주의사항:**
- `allowCredentials: true`는 와일드카드(`*`)와 함께 사용 불가
- 개발/스테이징/프로덕션 환경별 도메인 설정 필요

### 3.5 application.yml 설정

**작업:**

- [X] `application.yml` 수정
  ```yaml
  app:
    jwt:
      expiration-ms: 3600000  # 1시간
      cookie-name: jwt
      cookie-max-age: 3600  # 1시간
      cookie-domain: ""  # 프로덕션: .yourdomain.com
      cookie-secure: true  # 프로덕션: true
  ```

### 3.6 Frontend 수정 (필요 시)

**작업:**

- [ ] API 호출 시 `credentials: 'include'` 추가
  ```javascript
  // Axios 예시
  axios.defaults.withCredentials = true;

  // Fetch 예시
  fetch('/api/auth/login', {
    method: 'POST',
    credentials: 'include',
    // ...
  });
  ```

- [ ] Authorization 헤더 제거 (Cookie로 자동 전송)
  ```javascript
  // 기존 (제거)
  headers: {
    'Authorization': `Bearer ${token}`
  }

  // 새로운 방식 (자동)
  // Cookie가 자동으로 전송됨
  ```

---

## Phase 4: 테스트 및 검증

### 4.1 단위 테스트

**체크리스트:**

- [ ] JwtDenyListServiceTest 통과
- [ ] JwtServiceTest 통과
- [ ] SessionAwareJwtAuthenticationConverterTest 통과
- [ ] AuthControllerTest 통과
- [ ] CookieUtilTest 작성 및 통과

```bash
# 전체 단위 테스트 실행
./mvnw test
```

### 4.2 통합 테스트

**시나리오 1: 정상 로그인 플로우**

- [ ] 로그인 요청
- [ ] JWT 발급 확인
- [ ] Cookie 설정 확인 (HttpOnly, Secure, SameSite)
- [ ] SessionVersion Redis 저장 확인
- [ ] 인증 필요한 API 호출 성공

**시나리오 2: 로그아웃 플로우**

- [ ] 로그인 → JWT 발급
- [ ] 로그아웃 요청
- [ ] DenyList Redis 저장 확인
- [ ] Cookie 삭제 확인
- [ ] 동일 JWT로 API 호출 → 401 실패

**시나리오 3: 단일 세션 정책**

- [ ] 첫 로그인 → JWT1 발급
- [ ] 두 번째 로그인 (동일 사용자) → JWT2 발급
- [ ] SessionVersion 증가 확인
- [ ] JWT1로 API 호출 → 401 실패 (SessionVersion 불일치)
- [ ] JWT2로 API 호출 → 200 성공

**시나리오 4: 토큰 갱신**

- [ ] 만료된 JWT로 refresh 요청
- [ ] 새 JWT 발급 확인
- [ ] 기존 JWT DenyList 추가 확인
- [ ] 기존 JWT로 API 호출 → 401 실패
- [ ] 새 JWT로 API 호출 → 200 성공

**시나리오 5: 강제 로그아웃 (관리자)**

- [ ] 사용자 로그인 → JWT 발급
- [ ] 관리자가 SessionVersion 증가
- [ ] 기존 JWT로 API 호출 → 401 실패

### 4.3 성능 테스트

**부하 테스트:**

```bash
# Apache Bench를 사용한 간단한 부하 테스트
ab -n 10000 -c 100 -H "Authorization: Bearer YOUR_JWT" \
   http://localhost:8080/api/rooms

# 또는 K6 사용
k6 run load-test.js
```

**측정 항목:**

- [ ] 평균 응답 시간 (목표: < 2ms)
- [ ] 처리량 (목표: 현재 대비 2-3배)
- [ ] Redis 메모리 사용량
- [ ] Redis 연결 수

### 4.4 보안 테스트

**체크리스트:**

- [ ] XSS 방어 확인 (JavaScript로 Cookie 접근 불가)
  ```javascript
  // 브라우저 콘솔에서 실행
  document.cookie  // JWT 쿠키가 보이지 않아야 함
  ```

- [ ] CSRF 방어 확인 (SameSite=Strict)
- [ ] 만료된 토큰 거부 확인
- [ ] DenyList에 있는 토큰 거부 확인
- [ ] SessionVersion 불일치 토큰 거부 확인

### 4.5 Redis 장애 시나리오

**체크리스트:**

- [ ] Redis 다운 시 인증 동작 확인
  ```bash
  # Redis 중지
  redis-cli shutdown

  # API 호출 테스트
  # 예상: 가용성 우선으로 허용 (또는 에러)
  ```

- [ ] Redis 복구 시 자동 재연결 확인
- [ ] 에러 로깅 확인

---

## Phase 5: 배포 및 모니터링

### 5.1 배포 준비

**체크리스트:**

- [ ] 환경별 설정 확인
  - 개발: `application-dev.yml`
  - 스테이징: `application-staging.yml`
  - 프로덕션: `application-prod.yml`

```yaml
# application-prod.yml 예시
app:
  jwt:
    cookie-domain: .yourdomain.com
    cookie-secure: true

spring:
  redis:
    host: redis.production.internal
    port: 6379
    password: ${REDIS_PASSWORD}
```

- [ ] 환경 변수 설정
  ```bash
  export REDIS_PASSWORD="your-secure-password"
  export JWT_SECRET="your-jwt-secret"
  ```

- [ ] Dockerfile 확인 (Redis 연결 설정)

### 5.2 배포 전략

**Option A: Blue-Green 배포 (권장)**

1. [ ] Green 환경에 새 버전 배포
2. [ ] Green 환경 테스트 (smoke test)
3. [ ] 트래픽 일부 전환 (10% → 50% → 100%)
4. [ ] 모니터링 (에러율, 응답 시간)
5. [ ] 문제 없으면 Blue 환경 제거

**Option B: Canary 배포**

1. [ ] 소수 사용자에게만 새 버전 적용 (1%)
2. [ ] 모니터링 (24시간)
3. [ ] 점진적 확대 (5% → 25% → 50% → 100%)

**Option C: Feature Flag**

```java
// Feature Flag 예시
@Service
public class JwtDenyListService {

    @Value("${feature.jwt-denylist.enabled:false}")
    private boolean denyListEnabled;

    public boolean isTokenDenied(String jti) {
        if (!denyListEnabled) {
            return false;  // Feature Flag OFF
        }
        // DenyList 확인 로직
    }
}
```

### 5.3 롤백 계획

**롤백 트리거:**

- 에러율 > 1%
- 평균 응답 시간 > 100ms
- Redis 연결 실패율 > 5%
- 사용자 로그인 실패율 > 5%

**롤백 절차:**

```bash
# 1. 트래픽 이전 버전으로 전환
kubectl set image deployment/backend backend=backend:v1.0.0

# 2. Feature Flag OFF
kubectl set env deployment/backend FEATURE_JWT_DENYLIST_ENABLED=false

# 3. Pod 재시작
kubectl rollout restart deployment/backend

# 4. 모니터링
kubectl logs -f deployment/backend
```

### 5.4 모니터링 설정

**Prometheus 메트릭:**

```java
// JwtDenyListService에 메트릭 추가
@Service
public class JwtDenyListService {

    private final MeterRegistry meterRegistry;

    @Counted(value = "jwt.denylist.check", description = "DenyList 확인 횟수")
    public boolean isTokenDenied(String jti) {
        // ...
    }

    @Timed(value = "jwt.denylist.check.time", description = "DenyList 확인 시간")
    public boolean isTokenDenied(String jti) {
        // ...
    }
}
```

**모니터링 대시보드 항목:**

- [ ] JWT 발급 속도 (tokens/sec)
- [ ] DenyList 조회 속도 (queries/sec)
- [ ] DenyList 조회 시간 (ms)
- [ ] SessionVersion 증가 횟수
- [ ] Redis 연결 실패율
- [ ] 인증 실패율 (by reason)

**알림 설정:**

- [ ] DenyList 조회 실패율 > 1% → Slack 알림
- [ ] 평균 응답 시간 > 50ms → Warning
- [ ] 평균 응답 시간 > 100ms → Critical
- [ ] Redis 메모리 사용량 > 80% → Warning

### 5.5 로깅

**로그 레벨 설정:**

```yaml
# application-prod.yml
logging:
  level:
    com.ktb.chatapp.service.JwtDenyListService: INFO
    com.ktb.chatapp.security: INFO
```

**중요 로그 포인트:**

- [ ] 토큰 DenyList 추가 시
- [ ] SessionVersion 증가 시
- [ ] 인증 실패 시 (이유 포함)
- [ ] Redis 연결 실패 시

---

## Phase 6: 기존 코드 정리

### 6.1 파일 삭제

**삭제할 파일:**

```bash
# Session 관련 서비스
rm src/main/java/com/ktb/chatapp/service/SessionService.java
rm src/main/java/com/ktb/chatapp/service/SessionData.java
rm src/main/java/com/ktb/chatapp/service/SessionMetadata.java
rm src/main/java/com/ktb/chatapp/service/SessionCreationResult.java
rm src/main/java/com/ktb/chatapp/service/SessionValidationResult.java

# Session Store
rm src/main/java/com/ktb/chatapp/service/session/SessionStore.java
rm src/main/java/com/ktb/chatapp/service/session/SessionMongoStore.java

# Session Model & Repository
rm src/main/java/com/ktb/chatapp/model/Session.java
rm src/main/java/com/ktb/chatapp/repository/SessionRepository.java

# 테스트 파일
rm src/test/java/com/ktb/chatapp/service/SessionServiceTest.java

# 빈 디렉토리 정리
rmdir src/main/java/com/ktb/chatapp/service/session
```

### 6.2 DTO 수정

**LoginResponse.java:**

```java
// sessionId 필드 제거
@Data
@Builder
public class LoginResponse {
    private boolean success;
    private String message;
    private String token;
    // private String sessionId;  // 삭제
    private AuthUserDto user;
}
```

**TokenRefreshResponse.java:**

```java
// sessionId 필드 제거
@Data
@AllArgsConstructor
public class TokenRefreshResponse {
    private boolean success;
    private String message;
    private String token;
    // private String sessionId;  // 삭제
}
```

### 6.3 불필요한 Import 정리

```bash
# IntelliJ IDEA: Optimize Imports
Ctrl + Alt + O (Windows/Linux)
Cmd + Option + O (Mac)

# 또는 Maven으로 자동 정리
./mvnw spotless:apply
```

### 6.4 문서 업데이트

**체크리스트:**

- [ ] README.md 업데이트
  - 인증 방식 설명 수정
  - 환경 변수 추가 (Redis 관련)
  - 의존성 정보 업데이트

- [ ] API 문서 업데이트 (Swagger)
  - x-session-id 헤더 제거
  - Cookie 설명 추가
  - 응답 예시 업데이트 (sessionId 제거)

- [ ] 아키텍처 다이어그램 업데이트
  - MongoDB Session → Redis DenyList 변경 반영

---

## 최종 체크리스트

### 기능 테스트

- [ ] 로그인 성공
- [ ] 로그아웃 성공
- [ ] 토큰 검증 성공
- [ ] 토큰 갱신 성공
- [ ] 단일 세션 정책 작동
- [ ] 강제 로그아웃 작동
- [ ] 만료된 토큰 거부
- [ ] DenyList 토큰 거부

### 성능 테스트

- [ ] 응답 시간 < 2ms
- [ ] 처리량 2-3배 증가
- [ ] MongoDB 부하 100% 감소
- [ ] Redis 메모리 < 100MB (10만 사용자 기준)

### 보안 테스트

- [ ] XSS 방어 (HttpOnly Cookie)
- [ ] CSRF 방어 (SameSite)
- [ ] JWT 서명 검증
- [ ] 만료 시간 검증
- [ ] DenyList 검증
- [ ] SessionVersion 검증

### 배포

- [ ] 환경 변수 설정
- [ ] Redis 연결 확인
- [ ] CORS 설정 확인
- [ ] Cookie 도메인 설정 확인
- [ ] 모니터링 대시보드 설정
- [ ] 알림 설정
- [ ] 롤백 계획 준비

### 문서

- [ ] README.md 업데이트
- [ ] API 문서 업데이트
- [ ] 아키텍처 다이어그램 업데이트
- [ ] 마이그레이션 가이드 작성

---

## 트러블슈팅 가이드

### 문제 1: Redis 연결 실패

**증상:**
```
org.springframework.data.redis.RedisConnectionFailureException:
Unable to connect to Redis
```

**해결:**
```bash
# Redis 서버 상태 확인
redis-cli ping

# 연결 정보 확인
redis-cli -h localhost -p 6379 ping

# application.yml 설정 확인
spring:
  redis:
    host: localhost
    port: 6379
```

### 문제 2: CORS 에러

**증상:**
```
Access to fetch at 'http://localhost:8080/api/auth/login' from origin
'http://localhost:3000' has been blocked by CORS policy
```

**해결:**
```java
// SecurityConfig.java
config.setAllowedOriginPatterns(List.of("http://localhost:3000"));
config.setAllowCredentials(true);
```

### 문제 3: Cookie가 전송되지 않음

**증상:**
- 로그인 성공하지만 후속 요청에서 인증 실패

**해결:**
```javascript
// Frontend - credentials 설정 확인
axios.defaults.withCredentials = true;

// 또는
fetch('/api/...', {
  credentials: 'include'
});
```

### 문제 4: SessionVersion 불일치

**증상:**
- 로그인 후 즉시 401 에러

**해결:**
```bash
# Redis에서 SessionVersion 확인
redis-cli GET "jwt:session:version:USER_ID"

# SessionVersion 초기화
redis-cli DEL "jwt:session:version:USER_ID"
```

### 문제 5: DenyList에 토큰이 추가되지 않음

**증상:**
- 로그아웃 후에도 토큰이 계속 유효

**해결:**
```bash
# Redis에서 DenyList 확인
redis-cli KEYS "jwt:denylist:*"

# 특정 JTI 확인
redis-cli GET "jwt:denylist:YOUR_JTI"

# TTL 확인
redis-cli TTL "jwt:denylist:YOUR_JTI"
```

---

## 성공 기준

### 필수 (Must Have)

✅ 모든 기능 테스트 통과
✅ 응답 시간 70% 이상 개선
✅ MongoDB 부하 50% 이상 감소
✅ 보안 테스트 통과 (XSS, CSRF)
✅ 단일 세션 정책 작동
✅ 강제 로그아웃 작동

### 권장 (Should Have)

🎯 처리량 2배 이상 증가
🎯 Redis 메모리 사용량 < 100MB
🎯 에러율 < 0.1%
🎯 모니터링 대시보드 구축

### 선택 (Nice to Have)

💡 Feature Flag 구현
💡 A/B 테스트 진행
💡 성능 비교 리포트 작성
💡 사용자 피드백 수집

---

## 참고 자료

- [JWT Best Practices](https://tools.ietf.org/html/rfc8725)
- [OWASP JWT Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
- [Redis Best Practices](https://redis.io/topics/best-practices)
- [Spring Security OAuth2 Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [HTTP Cookie Security](https://developer.mozilla.org/en-US/docs/Web/HTTP/Cookies)
