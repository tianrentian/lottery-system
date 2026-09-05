from __future__ import annotations

from fastapi import Response, status

from app.main import readiness


def test_mock_mode_is_ready_without_api_key(monkeypatch):
    monkeypatch.setenv("AI_PLANNER_MODE", "mock")
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    response = Response()

    result = readiness(response)

    assert response.status_code == status.HTTP_200_OK
    assert result == {"status": "UP", "mode": "mock"}


def test_real_mode_requires_api_key(monkeypatch):
    monkeypatch.setenv("AI_PLANNER_MODE", "real")
    monkeypatch.delenv("DEEPSEEK_API_KEY", raising=False)
    response = Response()

    result = readiness(response)

    assert response.status_code == status.HTTP_503_SERVICE_UNAVAILABLE
    assert result["status"] == "DOWN"


def test_real_mode_is_ready_with_api_key(monkeypatch):
    monkeypatch.setenv("AI_PLANNER_MODE", "real")
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    response = Response()

    result = readiness(response)

    assert response.status_code == status.HTTP_200_OK
    assert result == {"status": "UP", "mode": "real"}


def test_unknown_mode_is_not_ready(monkeypatch):
    monkeypatch.setenv("AI_PLANNER_MODE", "unknown")
    response = Response()

    result = readiness(response)

    assert response.status_code == status.HTTP_503_SERVICE_UNAVAILABLE
    assert result["status"] == "DOWN"
