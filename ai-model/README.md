# ai-model — 시퀀스 다이어그램 생성 AI 서버

자연어 요청을 **Mermaid 시퀀스 다이어그램 코드**로 바꿔주는 FastAPI 서버.
Spring Boot 백엔드가 다이어그램을 새로 만들거나 수정할 때 이 서버를 호출한다.

```
프론트(5001) → 백엔드(5000) → 이 서버(5002) → 사내 LLM API
```

## 사내 LLM API 호출 방식

사내 LLM API는 OpenAI 파이썬 라이브러리와 같은 규격이라 아래 호출과 동등하다.
다만 폐쇄망에 `openai` 패키지를 반입하지 않으려고 표준 라이브러리로 직접 보낸다.

```python
client = OpenAI(api_key=LLM_API_KEY, base_url=LLM_API_BASE)
client.chat.completions.create(model=LLM_API_MODEL, messages=[...])
```

실제 HTTP 요청은 `POST {LLM_API_BASE}/chat/completions`, OpenAI Chat Completions 규격 그대로다.

```json
{
  "model": "gpt-4",
  "messages": [
    {"role": "system", "content": "...작성 규칙..."},
    {"role": "user",   "content": "...요구사항 또는 수정 요청..."}
  ],
  "temperature": 0
}
```

응답은 `choices[0].message.content` 에서 읽는다. 모델이 설명 문장이나
` ```mermaid ` 코드 펜스를 붙여 답해도 `mermaid.py` 가 코드만 뽑아낸다.

> 요구사항 검토 서버(`semes-superrookie/ai-model`)와 같은 방식이다. 다만 그쪽은
> 규칙 기반 검출이라는 대체 수단이 있어 LLM 실패를 삼키지만, 여기는 다이어그램을
> 만들 다른 방법이 없다. 그래서 실패를 감추지 않고 **502 로 올린다** — 사내 LLM이
> 돌려준 오류 본문을 그대로 담아서 화면에서 이유를 볼 수 있게 한다.

## 준비 (최초 1회)

사내 LLM API 주소는 사내망 정보라 저장소에 두지 않는다. `.env` 는 커밋되지 않는다.

```bash
cp ai-model/.env.example ai-model/.env   # 값을 채운다
```

| 변수 | 설명 |
|------|------|
| `LLM_API_BASE` | 사내 LLM API 주소, `/v1` 까지 포함. 비면 생성 요청이 503 으로 실패한다. |
| `LLM_API_MODEL` | 서버가 서빙하는 모델명. 틀리면 사내 LLM이 400 을 준다. |
| `LLM_API_KEY` | 사내 서비스는 인증이 없어 `EMPTY` |
| `LLM_API_TIMEOUT` | 응답 대기 시간(초), 기본 120 |

## 실행

저장소 루트에서:

```bash
./run-ai.sh          # 5002 포트. 가상환경이 없으면 만들고 의존성까지 설치한다.
./run-ai.sh 5010     # 포트를 바꾸려면 인자로 준다.
```

수동으로 하려면:

```bash
cd ai-model
python -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python -m uvicorn main:app --host 0.0.0.0 --port 5002
```

API 문서는 <http://localhost:5002/docs> 에서 볼 수 있다.

## API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/health` | 사내 LLM 설정 여부 (`llmApiConfigured`, `llmApiModel`) |
| `POST` | `/generate` | `{"requirement": "..."}` → `{"mermaidCode": "...", "elapsedMs": n}` |
| `POST` | `/refine` | `{"mermaidCode": "...", "instruction": "..."}` → 같은 형태 |

실패하면 FastAPI 규격대로 `{"detail": "..."}` 가 내려가고, 백엔드가 그 문장을
그대로 화면에 보여준다.

## 프롬프트 수정

`main.py` 의 `SYSTEM_PROMPT` 상수가 작성 규칙이고, `generate()` / `refine()` 안의
user 메시지가 그때그때 채워지는 부분이다. 규칙을 바꾸려면 `SYSTEM_PROMPT` 만 고치면
된다 — 백엔드나 프론트는 건드릴 필요가 없다.
