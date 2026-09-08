"""시퀀스 다이어그램 생성 AI 서버 (FastAPI).

Spring Boot 백엔드가 다이어그램을 새로 만들거나 수정할 때 이 서버를 호출한다.
이 서버는 사내 LLM API(OpenAI 호환 Chat Completions)에 요청해서 Mermaid 코드를
받아 돌려준다.

요구사항 검토 서버(semes-superrookie/ai-model)와 같은 방식이다 — 사내 LLM API는
OpenAI 파이썬 라이브러리와 같은 규격이라 아래 호출과 동등하지만, 폐쇄망에 openai
패키지를 반입하지 않으려고 표준 라이브러리로 직접 보낸다.

    client = OpenAI(api_key=LLM_API_KEY, base_url=LLM_API_BASE)
    client.chat.completions.create(model=LLM_API_MODEL, messages=[...])

여기서는 요구사항 검토와 달리 규칙 기반 대체 수단이 없다. LLM이 응답하지 못하면
다이어그램을 만들 방법이 없으므로, 실패를 감추지 않고 502 로 올려보낸다 —
사내 LLM이 돌려준 오류 본문을 그대로 담아서 화면에서 이유를 볼 수 있게 한다.

실행:
    uvicorn main:app --host 0.0.0.0 --port 5002
    (또는 저장소 루트에서 ./run-ai.sh)
"""

from __future__ import annotations

import json
import logging
import os
import time
import urllib.error
import urllib.request

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

import mermaid

log = logging.getLogger("ai-model")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")


def _load_dotenv() -> None:
    """ai-model/.env 를 읽어 환경 변수로 올린다 (.env 는 커밋되지 않는다).

    python-dotenv 를 쓰지 않는 이유: 폐쇄망에 반입할 패키지를 하나라도 줄이려고.
    이미 셸에 설정된 값이 우선이라, 실행할 때 준 값이 .env 에 덮이지 않는다.
    """
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
    if not os.path.exists(path):
        return
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


_load_dotenv()

# 사내 LLM API 주소는 소스에 두지 않는다 — 저장소가 사외로 나가도 사내망 정보가
# 같이 나가면 안 되므로. 커밋되지 않는 ai-model/.env 나 환경 변수로 넘긴다.
LLM_API_BASE = os.getenv("LLM_API_BASE", "").strip()
LLM_API_MODEL = os.getenv("LLM_API_MODEL", "gpt-4")  # 서버가 서빙하는 모델명에 맞춘다
LLM_API_KEY = os.getenv("LLM_API_KEY", "EMPTY")      # 사내 서비스는 인증 없이 EMPTY
LLM_API_TIMEOUT = float(os.getenv("LLM_API_TIMEOUT", "120"))

app = FastAPI(title="시퀀스 다이어그램 생성 AI", version="1.0")


# ── 요청/응답 스키마 ──────────────────────────────────────────────

class GenerateRequest(BaseModel):
    requirement: str = Field(..., description="그리고 싶은 흐름에 대한 자연어 설명")


class RefineRequest(BaseModel):
    mermaidCode: str = Field(..., description="현재 Mermaid 코드")
    instruction: str = Field(..., description="반영할 수정사항")


class MermaidResponse(BaseModel):
    mermaidCode: str
    elapsedMs: int


@app.get("/health")
def health() -> dict:
    """사내 LLM API 설정 여부 — 배포 후 첫 확인용. 백엔드가 그대로 화면에 전달한다."""
    return {
        "status": "ok",
        "llmApiConfigured": bool(LLM_API_BASE),
        "llmApiModel": LLM_API_MODEL,
    }


@app.post("/generate", response_model=MermaidResponse)
def generate(req: GenerateRequest) -> MermaidResponse:
    """자연어 설명 하나로 새 다이어그램을 만든다."""
    user = f"""다음 요구사항을 Mermaid 시퀀스 다이어그램으로 그려라.

요구사항:
{req.requirement.strip()}"""

    return _complete(user)


@app.post("/refine", response_model=MermaidResponse)
def refine(req: RefineRequest) -> MermaidResponse:
    """현재 코드에 수정 요청을 반영해 전체를 다시 만든다."""
    user = f"""아래는 기존 Mermaid 시퀀스 다이어그램과 수정 요청이다.
수정 요청을 반영해서 전체 다이어그램을 다시 작성하라.
수정 요청과 무관한 부분은 원본 그대로 유지하라.

현재 다이어그램:
{req.mermaidCode.strip()}

수정 요청:
{req.instruction.strip()}"""

    return _complete(user)


# ── 내부 구현 ────────────────────────────────────────────────────

SYSTEM_PROMPT = """당신은 Mermaid 시퀀스 다이어그램을 작성하는 소프트웨어 아키텍트다.

반드시 지킬 규칙:
1. Mermaid 코드만 답한다. 설명 문장도, 마크다운 코드 펜스도 붙이지 않는다.
2. 첫 줄은 반드시 `sequenceDiagram` 이다.
3. 등장하는 모든 주체를 `participant`(또는 `actor`)로 선언한다. 흐름에 처음
   등장하는 순서대로 선언하고, 짧고 읽기 쉬운 별칭을 준다.
4. 요청은 `->>`, 응답은 `-->>`, 실패는 `-x` 를 쓴다.
5. 조건 분기와 반복은 `alt` / `else` / `opt` / `loop` / `par` 로 표현하고,
   모든 블록은 `end` 로 닫는다.
6. `Note over ...` 는 흐름 이해에 정말 도움이 될 때만 쓴다.
7. 참여자 이름과 메시지는 요청에 쓰인 언어를 그대로 따른다.
8. 오류 없이 렌더링되는 Mermaid v11 문법이어야 한다."""


def _complete(user_message: str) -> MermaidResponse:
    started = time.monotonic()

    if not LLM_API_BASE:
        raise HTTPException(
            status_code=503,
            detail="사내 LLM API 주소가 설정되지 않았습니다. ai-model/.env 의 LLM_API_BASE 를 채우세요.",
        )

    raw = _ask_llm_api(user_message)
    code = mermaid.extract(raw)

    if not mermaid.looks_like_diagram(code):
        log.warning("다이어그램이 아닌 응답: %s", raw[:500])
        raise HTTPException(
            status_code=502,
            detail="사내 LLM 이 Mermaid 코드를 돌려주지 않았습니다. 요청 문장을 바꿔서 다시 시도하세요.",
        )

    elapsed = int((time.monotonic() - started) * 1000)
    log.info("생성 완료 %d자 %dms", len(code), elapsed)
    return MermaidResponse(mermaidCode=code, elapsedMs=elapsed)


def _ask_llm_api(user_message: str) -> str:
    """사내 LLM API 서비스(OpenAI 호환)에 Chat Completions 로 요청한다.

    OpenAI 라이브러리의 client.chat.completions.create(...) 와 같은 HTTP 요청을
    표준 라이브러리로 보낸다 — 폐쇄망에 openai 패키지를 반입하지 않으려고.
    """
    url = f"{LLM_API_BASE}/chat/completions"
    payload = {
        "model": LLM_API_MODEL,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_message},
        ],
        "temperature": 0,
    }

    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {LLM_API_KEY}",
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=LLM_API_TIMEOUT) as res:
            body = json.loads(res.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        # 400 등의 이유는 서버가 응답 본문에 적어 보낸다. 감추지 않고 그대로 올린다 —
        # 모델명이 틀렸는지, 컨텍스트가 넘쳤는지 화면에서 바로 확인할 수 있어야 한다.
        detail = e.read().decode("utf-8", errors="replace")
        log.error("사내 LLM %s — POST %s\n  요청: %s\n  응답: %s",
                  e.code, url, json.dumps(payload, ensure_ascii=False)[:600], detail)
        raise HTTPException(
            status_code=502,
            detail=f"사내 LLM 이 {e.code} 를 반환했습니다: {detail}",
        ) from e
    except Exception as e:
        log.error("사내 LLM 호출 실패 — POST %s: %s", url, e)
        raise HTTPException(
            status_code=502,
            detail=f"사내 LLM({LLM_API_BASE})에 연결하지 못했습니다: {e}",
        ) from e

    try:
        return body["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as e:
        log.error("사내 LLM 응답 형식이 예상과 다릅니다: %s", json.dumps(body)[:600])
        raise HTTPException(
            status_code=502,
            detail=f"사내 LLM 응답에서 본문을 찾지 못했습니다: {json.dumps(body)[:300]}",
        ) from e
