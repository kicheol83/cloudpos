# CloudPos

음식점용 멀티테넌트 POS SaaS. 첫 단계로 PostgreSQL RLS로 테넌트를 분리하는 뼈대를 만들었습니다.

`TenantIsolationTest`는 테넌트 두 개를 넣고, repository에 tenant 조건 없이 조회해도 각 테넌트가 자기 데이터만 보는지 확인합니다.

## 실행

JDK 21과 Docker가 필요합니다.

```powershell
.\make.ps1 up.core
.\make.ps1 run.identity
.\make.ps1 run.gateway
```

```powershell
$headers = @{ "X-Dev-Tenant-Id" = "00000000-0000-0000-0000-00000000000a" }
Invoke-RestMethod -Uri "http://localhost:8080/v1/stores" -Headers $headers
```

다른 프로젝트와 겹치지 않게 PostgreSQL은 5435, Redis는 6380 포트를 씁니다.

`bootRun`은 `local` 프로필로 실행됩니다.

## 구조

- `services/`: `gateway`, `identity`
- `libs/`: `tenancy`(RLS), `web`
- `contracts/`: OpenAPI, 이벤트 스키마
- `infra/compose/`: 로컬 환경
