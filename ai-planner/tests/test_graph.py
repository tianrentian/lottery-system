from __future__ import annotations

from collections.abc import Sequence
from typing import Optional

from app.graph import build_planner_graph, invoke_planner
from app.models import ActivityPlanDraft, ActivityPlanPrize, PlannerRequest, PrizeOption, RequestAnalysis, ValidationIssue


CATALOG = [
    PrizeOption(prize_id=1, name="耳机", price=99),
    PrizeOption(prize_id=2, name="保温杯", price=49),
    PrizeOption(prize_id=3, name="咖啡券", price=20),
]


def valid_draft() -> ActivityPlanDraft:
    return ActivityPlanDraft(
        activity_name="公司年会抽奖",
        description="面向全体员工的年会抽奖活动。",
        prizes=[
            ActivityPlanPrize(prize_id=1, prize_amount=1, prize_tiers="FIRST_PRIZE"),
            ActivityPlanPrize(prize_id=2, prize_amount=2, prize_tiers="SECOND_PRIZE"),
        ],
    )


def run(request: PlannerRequest, outputs: Sequence[ActivityPlanDraft], analysis: Optional[RequestAnalysis] = None):
    remaining = list(outputs)

    def generator(_prompt, _catalog, _previous, _issues):
        return remaining.pop(0)

    def analyzer(_prompt, _catalog):
        return analysis or RequestAnalysis()

    graph = build_planner_graph(analyzer=analyzer, generator=generator)
    return invoke_planner(graph, request)


def test_valid_plan_finishes_ready():
    state = run(PlannerRequest(prompt="年会抽奖", available_prizes=CATALOG), [valid_draft()])

    assert state["status"] == "READY"
    assert state["repair_attempts"] == 0
    assert state["issues"] == []


def test_invalid_first_plan_is_repaired_with_error_feedback():
    invalid = valid_draft()
    invalid.prizes[0].prize_id = 999

    state = run(
        PlannerRequest(prompt="年会抽奖", available_prizes=CATALOG),
        [invalid, valid_draft()],
    )

    assert state["status"] == "READY"
    assert state["repair_attempts"] == 1
    assert any(issue["code"] == "PRIZE_NOT_FOUND" for issue in state["issues"]) is False


def test_plan_after_two_repairs_is_transferred_to_manual_review():
    invalid = valid_draft()
    invalid.description = ""

    state = run(
        PlannerRequest(prompt="年会抽奖", available_prizes=CATALOG),
        [invalid, invalid, invalid],
    )

    assert state["status"] == "NEEDS_MANUAL"
    assert state["repair_attempts"] == 2
    assert state["warnings"] == ["AI方案已生成，部分配置需要人工确认。"]


def test_hard_conflict_pauses_for_one_clarification():
    state = run(
        PlannerRequest(prompt="预算不超过100元，但一等奖必须是高价奖品", available_prizes=CATALOG),
        [],
        RequestAnalysis(
            needs_clarification=True,
            clarification_question="优先控制预算还是保留指定奖品？",
            clarification_options=["优先控制预算", "保留指定奖品"],
        ),
    )

    assert state["status"] == "WAITING_USER"
    assert state["clarification_question"] == "优先控制预算还是保留指定奖品？"
    assert state["clarification_options"] == ["优先控制预算", "保留指定奖品"]


def test_no_available_prize_requires_manual_selection():
    state = run(PlannerRequest(prompt="年会抽奖", available_prizes=[]), [
        ActivityPlanDraft(
            activity_name="公司年会抽奖",
            description="面向全体员工的抽奖活动。",
        )
    ])

    assert state["status"] == "NEEDS_MANUAL"
    assert any(issue["code"] == "NO_PRIZE_SELECTED" for issue in state["issues"])


def test_hard_budget_is_checked_by_code():
    state = run(
        PlannerRequest(prompt="预算不超过100元", available_prizes=CATALOG, hard_budget=100),
        [valid_draft(), valid_draft(), valid_draft()],
    )

    assert state["status"] == "NEEDS_MANUAL"
    assert any(issue["code"] == "BUDGET_EXCEEDED" for issue in state["issues"])
