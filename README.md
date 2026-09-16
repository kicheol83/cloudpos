# CloudPos

음식점용 멀티테넌트 POS SaaS 백엔드입니다. 테넌트 분리는 애플리케이션 코드가 아니라 PostgreSQL RLS로 보장합니다.

현재 인증과 권한(identity) 단계까지 구현했습니다. 메뉴, 주문, 재고, 결제는 다음 단계입니다.

## 구현된 기능

- PostgreSQL RLS 기반 멀티테넌시
- 테넌트, 매장 관리
- 디바이스 등록, 페어링, 폐기
- 직원 PIN 로그인 (Argon2id, 5회 실패 시 잠금)
- RS256 JWT, 리프레시 토큰 rotation과 재사용 감지
- 역할 기반 권한, 매니저 승인 토큰
- 근무(shift) 시작, 종료
- API gateway: 토큰 검증, 테넌트별 rate limit

## 기술 스택

Java 21, Spring Boot 3.5, PostgreSQL 17, Redis, Testcontainers, Gradle

## 실행

JDK 21과 Docker가 필요합니다.

```powershell
.\make.ps1 up.core
.\make.ps1 run.identity
.\make.ps1 run.gateway
```

다른 프로젝트와 겹치지 않게 PostgreSQL은 5435, Redis는 6380 포트를 씁니다.

`bootRun`은 `local` 프로필로 실행됩니다.

## 사용 예시

```powershell
$body = '{"tenant_name":"Seoul Kitchen","store_name":"Gangnam Branch"}'
Invoke-RestMethod -Method Post -Uri http://localhost:8081/v1/platform/tenants -Body $body -ContentType "application/json"
```

디바이스 등록, 페어링, 직원 등록 후 `http://localhost:8080/v1/auth/login`으로 로그인합니다. API는 `contracts/openapi/identity.yaml`에 있습니다.

## 테스트

```powershell
.\make.ps1 test
```

모든 테스트는 Testcontainers로 실제 PostgreSQL에서 실행합니다. RLS는 H2에서 검증할 수 없기 때문입니다.

## 구조

- `services/`: `gateway`, `identity`
- `libs/`: `tenancy`(RLS), `security`, `web`, `ids`
- `contracts/`: OpenAPI, 이벤트 스키마
- `infra/compose/`: 로컬 환경
- `docs/adr/`: 설계 결정

## 디자인

UI 디자인은 AJP Design의 CloudPos Point of Sale UI Kit을 참고했습니다.
