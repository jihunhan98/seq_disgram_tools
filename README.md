# Sequence Diagram Tool

자연어로 시퀀스 다이어그램을 만들고, Mermaid 코드로 다듬고, Oracle DB에 저장·조회·수정하는 사내용 도구입니다.
Design Review나 프로젝트 관리 과정에서 필요한 시퀀스 다이어그램을 빠르게 만들고 보관하는 것을 목적으로 합니다.

```
┌──────────────────────────┐        ┌───────────────────────────┐        ┌──────────────────┐
│  Frontend (정적 파일)     │  HTTP  │  Backend (Spring Boot 3)  │  JDBC  │   Oracle DB      │
│  http://localhost:5001   │ ─────▶ │  http://localhost:5000    │ ─────▶ │   SEQ_DIAGRAM    │
│  HTML + CSS + JS         │        │  JDK 21                   │        └──────────────────┘
│  mermaid (vendored)      │        └───────────┬───────────────┘
└──────────────────────────┘                    │ HTTP (OpenAI 호환)
                                                ▼
                                     사내 AI API (Playground)
```

## 주요 기능

| 기능 | 설명 |
|------|------|
| 자연어 → 다이어그램 | 그리고 싶은 흐름을 문장으로 적으면 사내 AI API가 Mermaid 코드로 변환합니다. |
| 실시간 미리보기 | Mermaid 코드를 수정하면 미리보기가 자동으로 다시 그려집니다. (디바운스 350ms) |
| 코드 편집 · 복사 · 붙여넣기 | 하단 편집창에서 직접 수정할 수 있고, 복사/붙여넣기 버튼을 제공합니다. Tab 키로 들여쓰기가 됩니다. |
| 자연어 수정 | 현재 Mermaid 코드 + 수정 요청 문장을 함께 보내 기존 흐름을 유지한 채 재생성합니다. |
| 저장 · 조회 | Oracle DB의 `SEQ_DIAGRAM` 테이블에 저장하고 언제든 다시 불러옵니다. |
| 덮어쓰기 수정 | 수정 저장은 기존 행을 덮어씁니다. 이전 버전은 보관하지 않습니다. |
| 문법 오류 표시 | Mermaid 문법이 깨지면 오류 메시지를 보여주고, 마지막으로 정상 렌더된 그림은 그대로 유지합니다. |
| SVG 저장 | 현재 미리보기를 SVG 파일로 내려받습니다. |

## 구성

| | |
|---|---|
| 백엔드 | Spring Boot 3.5 / **JDK 21** / Spring JDBC / Oracle JDBC (ojdbc11) / 포트 **5000** |
| 프론트엔드 | 빌드 도구 없는 순수 HTML·CSS·JavaScript / 포트 **5001** |
| 다이어그램 렌더링 | mermaid v11 (`frontend/vendor/mermaid.min.js`에 포함 — 인터넷 없이 동작) |
| AI | 사내 Playground API (OpenAI `/v1/chat/completions` 호환), 모델 `gpt-4` |

## 디렉터리 구조

```
.
├── backend/                     Spring Boot API
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/company/seqdiagram/
│       │   ├── config/          AI 설정, CORS, HTTP 클라이언트, 스키마 초기화
│       │   ├── controller/      /api/diagrams, /api/ai, /api/health
│       │   ├── repository/      SEQ_DIAGRAM JDBC 접근
│       │   └── service/         AI 호출, Mermaid 코드 추출
│       └── resources/
│           ├── application.yml           비밀정보 없음 (커밋 대상)
│           └── db/oracle-schema.sql      Oracle DDL
├── frontend/                    빌드 없이 그대로 서비스되는 정적 파일
│   ├── index.html
│   ├── css/styles.css
│   ├── js/{config,api,app}.js
│   ├── vendor/mermaid.min.js
│   └── server/StaticServer.java JDK만으로 동작하는 정적 파일 서버
├── config/
│   └── application-local.yml.example    ← 복사해서 실제 값을 채우는 템플릿
├── run-backend.sh
└── run-frontend.sh
```

## 준비 (최초 1회)

DB 접속 정보와 사내 AI 엔드포인트는 **저장소에 올리지 않습니다.**
`.gitignore`가 `config/application-local.yml`을 제외하고 있으니, 템플릿을 복사해 실제 값을 채워 넣으세요.

```bash
cp config/application-local.yml.example config/application-local.yml
```

`config/application-local.yml`:

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@<HOST>:<PORT>:<SID>     # 서비스명 방식은 jdbc:oracle:thin:@//<HOST>:<PORT>/<SERVICE>
    username: <USERNAME>
    password: <PASSWORD>

app:
  ai:
    base-url: http://<HOST>:<PORT>/v1              # 사내 Playground API, /v1 까지 포함
    api-key: EMPTY
    model: gpt-4
```

파일 대신 환경변수(`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `AI_BASE_URL`, `AI_API_KEY`, `AI_MODEL`)로 넘겨도 됩니다.

> 이 파일이 실수로 커밋되지 않는지는 `git check-ignore -v config/application-local.yml`로 확인할 수 있습니다.

## 실행

터미널 두 개에서 각각 실행합니다.

```bash
./run-backend.sh     # http://localhost:5000  (Spring Boot)
./run-frontend.sh    # http://localhost:5001  (정적 파일)
```

브라우저에서 <http://localhost:5001> 로 접속합니다.

프론트엔드는 빌드 과정이 없습니다. `run-frontend.sh`는 JDK만 사용하는 단일 파일 서버를 실행할 뿐이므로,
사내 웹서버(Nginx, Apache, IIS 등)에 `frontend/` 폴더를 그대로 올려서 서비스해도 동일하게 동작합니다.
그 경우 백엔드 주소가 `<현재 호스트>:5000`과 다르면 `frontend/js/config.js`의 `apiBaseUrl`만 바꾸면 됩니다.

배포용 jar가 필요하면:

```bash
cd backend && mvn clean package        # target/seq-diagram-backend.jar
java -jar target/seq-diagram-backend.jar
```

> jar로 실행할 때는 실행 위치 기준으로 `./config/application-local.yml` 또는 `../config/application-local.yml`을 읽습니다.

## 데이터베이스

`app.db.auto-init: true`(기본값)이면 기동 시 `SEQ_DIAGRAM` 테이블과 시퀀스가 없을 때 자동으로 만듭니다.
계정에 DDL 권한이 없으면 경고만 남기고 계속 실행되므로, 아래 DDL을 DBA에게 요청해 직접 적용하면 됩니다.
(전체 내용은 `backend/src/main/resources/db/oracle-schema.sql`)

```sql
CREATE TABLE SEQ_DIAGRAM (
    ID           NUMBER(19)          NOT NULL,
    TITLE        VARCHAR2(200 CHAR)  NOT NULL,
    DESCRIPTION  VARCHAR2(2000 CHAR),
    MERMAID_CODE CLOB                NOT NULL,
    LAST_PROMPT  VARCHAR2(4000 CHAR),
    CREATED_AT   TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATED_AT   TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_SEQ_DIAGRAM PRIMARY KEY (ID)
);
CREATE SEQUENCE SEQ_DIAGRAM_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;
CREATE INDEX IX_SEQ_DIAGRAM_UPDATED ON SEQ_DIAGRAM (UPDATED_AT DESC);
```

Mermaid 코드는 `CLOB`이라 길이 제한이 사실상 없습니다. 수정 저장은 같은 행을 덮어쓰며 이전 버전은 남기지 않습니다.

## API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/health` | 상태 및 AI 설정 여부 (`aiConfigured`, `aiModel`) |
| `GET` | `/api/diagrams` | 목록 (Mermaid 코드는 제외한 요약) |
| `GET` | `/api/diagrams/{id}` | 단건 조회 (Mermaid 코드 포함) |
| `POST` | `/api/diagrams` | 신규 저장 |
| `PUT` | `/api/diagrams/{id}` | 덮어쓰기 저장 |
| `DELETE` | `/api/diagrams/{id}` | 삭제 |
| `POST` | `/api/ai/generate` | `{"requirement": "..."}` → `{"mermaidCode": "..."}` |
| `POST` | `/api/ai/refine` | `{"mermaidCode": "...", "instruction": "..."}` → `{"mermaidCode": "..."}` |

오류는 `{"status", "error", "message", "timestamp"}` 형태로 내려옵니다.
AI 호출 실패는 `502`, DB 접속 실패는 `503`, 없는 다이어그램은 `404`입니다.

```bash
# 예시
curl -X POST http://localhost:5000/api/ai/generate \
     -H 'Content-Type: application/json' \
     -d '{"requirement":"사용자가 로그인하면 게이트웨이가 인증 서버에 토큰을 요청한다"}'
```

## 테스트

```bash
cd backend && mvn test
```

Oracle 호환 모드의 H2로 실제 운영 SQL을 그대로 검증하고, AI 클라이언트는 실제 소켓에 요청을 보내
`Content-Length` 전송과 응답 파싱까지 확인합니다.

## 참고 사항

- **AI 응답 정리** — 모델이 설명 문장이나 ```` ```mermaid ```` 코드 펜스를 붙여서 답해도 백엔드가 Mermaid 코드만 추출해 전달합니다.
  다이어그램이 아닌 응답이면 `502`로 처리합니다.
- **AI 요청 본문** — 요청은 `Content-Length`를 붙여 한 번에 전송합니다. 스트리밍(chunked)으로 보내면
  OpenAI 호환 서버나 앞단 프록시가 거부하는 경우가 있어 의도적으로 버퍼링합니다.
- **CORS** — 프론트엔드(5001)와 백엔드(5000)의 출처가 다르므로 `app.cors.allowed-origins`에 등록된 출처만 허용합니다.
  다른 호스트에서 접속한다면 이 값에 추가하세요.
- **오프라인 동작** — mermaid를 저장소에 포함했기 때문에 인터넷이 차단된 사내망에서도 그대로 동작합니다.
- **저장하지 않은 변경** — 다른 다이어그램을 열거나 페이지를 벗어나려 하면 경고합니다. `Ctrl/Cmd + S`로 저장됩니다.
