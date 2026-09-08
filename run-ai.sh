#!/usr/bin/env bash
# AI 서버(FastAPI)를 5002 포트로 띄운다.
#
# 가상환경이 없으면 만들고 의존성을 설치한 뒤 실행한다. 두 번째 실행부터는
# 이미 있는 가상환경을 그대로 쓰므로 바로 뜬다.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AI_DIR="$ROOT/ai-model"
PORT="${1:-5002}"

cd "$AI_DIR"

if [[ ! -f .env ]]; then
    echo "ai-model/.env 가 없습니다."
    echo "템플릿을 복사해서 사내 LLM API 주소를 채우세요:"
    echo "    cp ai-model/.env.example ai-model/.env"
    exit 1
fi

# venv 의 python 을 직접 부른다 — activate 를 거치지 않아도 되고,
# 어느 가상환경으로 도는지도 명확해진다.
if [[ ! -x .venv/bin/python ]]; then
    echo "가상환경을 만듭니다 (최초 1회)..."
    python3 -m venv .venv
    .venv/bin/pip install --quiet --upgrade pip
    .venv/bin/pip install --quiet -r requirements.txt
    echo "완료."
fi

echo "AI 서버 실행    http://localhost:${PORT}/docs"
exec .venv/bin/python -m uvicorn main:app --host 0.0.0.0 --port "$PORT"
