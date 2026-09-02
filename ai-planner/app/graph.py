from __future__ import annotations

from collections.abc import Callable, Sequence
from typing import Any, Optional

from langgraph.graph import END, START, StateGraph

from .models import (
    ActivityPlanDraft,
    PlannerRequest,
    PlannerState,
    PrizeOption,
    RequestAnalysis,
    ValidationIssue,
)
from .validation import validate_draft


Analyzer = Callable[[str, Sequence[PrizeOption]], RequestAnalysis]
Generator = Callable[[str, Sequence[PrizeOption], Optional[ActivityPlanDraft], Sequence[ValidationIssue]], ActivityPlanDraft]


def default_analyzer(prompt: str, _available_prizes: Sequence[PrizeOption]) -> RequestAnalysis:
    """测试和 Mock 模式使用的保守分析器；真实模式注入 LangChain 分析器。"""
    return RequestAnalysis(
        assumptions=["未指定预算，按基础规模抽奖生成"],
        participant_hint="",
    )


def build_planner_graph(analyzer: Analyzer = default_analyzer, generator: Optional[Generator] = None):
    if generator is None:
        raise ValueError("必须注入生成器，真实DeepSeek生成器在app.llm中提供")

    def analyze_node(state: PlannerState) -> dict[str, Any]:
        if state.get("clarification_answer"):
            return {"status": "GENERATING"}
        analysis = analyzer(
            state["prompt"],
            [PrizeOption.model_validate(item) for item in state.get("available_prizes", [])],
        )
        if analysis.needs_clarification:
            return {
                "status": "WAITING_USER",
                "clarification_question": analysis.clarification_question,
                "clarification_options": analysis.clarification_options,
                "assumptions": analysis.assumptions,
                "participant_hint": analysis.participant_hint,
            }
        return {
            "status": "GENERATING",
            "assumptions": analysis.assumptions,
            "participant_hint": analysis.participant_hint,
        }

    def generate_node(state: PlannerState) -> dict[str, Any]:
        available_prizes = [PrizeOption.model_validate(item) for item in state.get("available_prizes", [])]
        previous_draft = (
            ActivityPlanDraft.model_validate(state["draft"])
            if state.get("draft")
            else None
        )
        issues = [ValidationIssue.model_validate(item) for item in state.get("issues", [])]
        draft = generator(
            state["prompt"],
            available_prizes,
            previous_draft,
            issues,
        )
        return {
            "draft": draft.model_dump(),
            "status": "VALIDATING",
            "error_message": None,
        }

    def validate_node(state: PlannerState) -> dict[str, Any]:
        draft = ActivityPlanDraft.model_validate(state.get("draft") or {})
        available_prizes = [PrizeOption.model_validate(item) for item in state.get("available_prizes", [])]
        issues = validate_draft(draft, available_prizes, state.get("hard_budget"))
        return {
            "issues": [issue.model_dump() for issue in issues],
            "status": "READY" if not issues else "INVALID",
        }

    def prepare_repair_node(state: PlannerState) -> dict[str, Any]:
        return {
            "repair_attempts": state.get("repair_attempts", 0) + 1,
            "status": "REPAIRING",
        }

    def route_after_analysis(state: PlannerState) -> str:
        return "wait" if state.get("status") == "WAITING_USER" else "generate"

    def route_after_validation(state: PlannerState) -> str:
        if state.get("status") == "READY":
            return "ready"
        # 没有可用奖品时，模型无法凭空创建奖品；直接交给管理员在原页面圈选。
        if any(issue.get("code") == "NO_PRIZE_SELECTED" for issue in state.get("issues", [])):
            return "manual"
        if state.get("repair_attempts", 0) < 2:
            return "repair"
        return "manual"

    def mark_manual_node(state: PlannerState) -> dict[str, Any]:
        return {
            "status": "NEEDS_MANUAL",
            "warnings": ["AI方案已生成，部分配置需要人工确认。"],
        }

    graph = StateGraph(PlannerState)
    graph.add_node("analyze", analyze_node)
    graph.add_node("generate", generate_node)
    graph.add_node("validate", validate_node)
    graph.add_node("prepare_repair", prepare_repair_node)
    graph.add_node("mark_manual", mark_manual_node)
    graph.add_edge(START, "analyze")
    graph.add_conditional_edges("analyze", route_after_analysis, {"wait": END, "generate": "generate"})
    graph.add_edge("generate", "validate")
    graph.add_conditional_edges(
        "validate",
        route_after_validation,
        {"ready": END, "repair": "prepare_repair", "manual": "mark_manual"},
    )
    graph.add_edge("prepare_repair", "generate")
    graph.add_edge("mark_manual", END)
    return graph.compile()


def invoke_planner(graph, request: PlannerRequest) -> PlannerState:
    initial_state: PlannerState = {
        "prompt": request.prompt,
        "available_prizes": [prize.model_dump() for prize in request.available_prizes],
        "hard_budget": request.hard_budget,
        "clarification_answer": request.clarification_answer,
        "repair_attempts": 0,
        "issues": [],
        "assumptions": [],
        "warnings": [],
        "status": "RUNNING",
    }
    return graph.invoke(initial_state)
