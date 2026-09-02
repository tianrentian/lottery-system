from __future__ import annotations

import os
from typing import Any

from fastapi import FastAPI, HTTPException

from .graph import build_planner_graph, invoke_planner
from .llm import DeepSeekActivityPlanner
from .mock import analyze as mock_analyze
from .mock import generate as mock_generate
from .models import PlannerRequest


app = FastAPI(title="抽奖系统 AI 策划服务", version="0.1.0")


def _graph():
    if os.getenv("AI_PLANNER_MODE", "mock").lower() == "real":
        planner = DeepSeekActivityPlanner()
        return build_planner_graph(analyzer=planner.analyze, generator=planner.generate)
    return build_planner_graph(analyzer=mock_analyze, generator=mock_generate)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "mode": os.getenv("AI_PLANNER_MODE", "mock").lower()}


@app.post("/plan")
def plan(request: PlannerRequest) -> dict[str, Any]:
    try:
        return invoke_planner(_graph(), request)
    except (RuntimeError, ValueError) as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
