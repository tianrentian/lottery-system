from __future__ import annotations

from collections.abc import Sequence
from typing import Optional

from .models import ActivityPlanDraft, ActivityPlanPrize, PrizeOption, RequestAnalysis, ValidationIssue


def analyze(_prompt: str, _catalog: Sequence[PrizeOption]) -> RequestAnalysis:
    return RequestAnalysis(assumptions=["未指定预算，按基础规模抽奖生成"])


def generate(
    _prompt: str,
    catalog: Sequence[PrizeOption],
    _previous: Optional[ActivityPlanDraft],
    _issues: Sequence[ValidationIssue],
) -> ActivityPlanDraft:
    """供演示和测试使用的确定性本地模式，不会调用 DeepSeek。"""
    selected = list(catalog)[:3]
    prizes = [
        ActivityPlanPrize(
            prize_id=prize.prize_id,
            prize_amount=1 if index == 0 else 2,
            prize_tiers=("FIRST_PRIZE", "SECOND_PRIZE", "THIRD_PRIZE")[index],
        )
        for index, prize in enumerate(selected)
    ]
    return ActivityPlanDraft(
        activity_name="智能抽奖活动",
        description="根据管理员输入自动生成的基础抽奖方案。",
        prizes=prizes,
        assumptions=["未指定预算，按基础规模抽奖生成"],
        participant_hint=f"建议至少选择{sum(item.prize_amount for item in prizes)}名参与人员",
    )
