from __future__ import annotations

import logging
import os
from typing import Any

from fastapi import FastAPI, HTTPException

from .graph import build_planner_graph, invoke_planner
from .llm import DeepSeekActivityPlanner
from .mock import analyze as mock_analyze
from .mock import generate as mock_generate
from .models import NotificationTemplateRequest, NotificationTemplateResponse, PlannerRequest
from .notification import (
    DeepSeekNotificationGenerator,
    mock_notification_templates,
    validate_notification_templates,
)


app = FastAPI(title="抽奖系统 AI 服务", version="0.2.0")
logger = logging.getLogger(__name__)


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


@app.post("/notification-templates", response_model=NotificationTemplateResponse)
def notification_templates(request: NotificationTemplateRequest) -> NotificationTemplateResponse:
    try:
        if os.getenv("AI_PLANNER_MODE", "mock").lower() == "real":
            response = DeepSeekNotificationGenerator().generate(request)
        else:
            response = mock_notification_templates(request)
        return validate_notification_templates(request, response)
    except Exception as exc:
        logger.exception("AI通知模板生成失败")
        raise HTTPException(status_code=503, detail="AI通知模板生成失败") from exc
