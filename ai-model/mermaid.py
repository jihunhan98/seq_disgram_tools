"""LLM 응답에서 Mermaid 코드만 뽑아낸다.

모델은 설명 문장이나 ```mermaid 코드 펜스를 붙여서 답하는 경우가 많다.
편집창에는 코드만 들어가야 하므로 여기서 걷어낸다.
"""

from __future__ import annotations

# 지원하는 다이어그램 선언 키워드. 이 중 하나로 시작하는 줄부터가 코드다.
DIAGRAM_KEYWORDS = (
    "sequenceDiagram", "flowchart", "graph ", "classDiagram", "stateDiagram",
    "erDiagram", "journey", "gantt", "pie", "mindmap", "timeline",
)


def extract(raw: str | None) -> str:
    """응답 텍스트에서 Mermaid 코드만 남긴다."""
    if not raw:
        return ""

    content = raw.strip()
    fenced = _first_fenced_block(content)
    if fenced is not None:
        content = fenced

    return _drop_leading_prose(content).strip()


def looks_like_diagram(code: str | None) -> bool:
    """다이어그램 선언이 들어 있는지 — 모델이 거절 문구를 보낸 경우를 걸러낸다."""
    if not code or not code.strip():
        return False
    return any(keyword in code for keyword in DIAGRAM_KEYWORDS)


def _first_fenced_block(content: str) -> str | None:
    """첫 ``` 코드 펜스의 내용. 펜스가 없으면 None."""
    open_at = content.find("```")
    if open_at < 0:
        return None

    body_start = content.find("\n", open_at)
    if body_start < 0:
        return None

    close_at = content.find("```", body_start)
    if close_at < 0:
        # 닫는 펜스가 없으면 여는 펜스 뒤 전부를 코드로 본다.
        return content[body_start + 1:]
    return content[body_start + 1:close_at]


def _drop_leading_prose(content: str) -> str:
    """펜스 없이 답한 경우, 다이어그램 선언 앞의 설명 문장을 버린다."""
    lines = content.split("\n")
    for i, line in enumerate(lines):
        if any(line.strip().startswith(k) for k in DIAGRAM_KEYWORDS):
            return "\n".join(lines[i:]) if i > 0 else content
    return content
