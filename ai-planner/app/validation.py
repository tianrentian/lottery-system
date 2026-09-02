from __future__ import annotations

from decimal import Decimal, InvalidOperation
from typing import Iterable, Optional

from .models import ALLOWED_TIERS, ActivityPlanDraft, PrizeOption, ValidationIssue


def validate_draft(
    draft: ActivityPlanDraft,
    available_prizes: Iterable[PrizeOption],
    hard_budget: Optional[float] = None,
) -> list[ValidationIssue]:
    """Validate model output without trusting the model for business rules."""
    issues: list[ValidationIssue] = []
    if not draft.activity_name.strip():
        issues.append(ValidationIssue(field="activity_name", code="NAME_REQUIRED", message="活动名称不能为空"))
    elif len(draft.activity_name) > 30:
        issues.append(ValidationIssue(field="activity_name", code="NAME_TOO_LONG", message="活动名称不能超过30字"))

    if not draft.description.strip():
        issues.append(ValidationIssue(field="description", code="DESCRIPTION_REQUIRED", message="活动描述不能为空"))
    elif len(draft.description) > 200:
        issues.append(ValidationIssue(field="description", code="DESCRIPTION_TOO_LONG", message="活动描述不能超过200字"))

    catalog = {prize.prize_id: prize for prize in available_prizes}
    seen_ids: set[int] = set()
    total_cost = Decimal("0")
    if not draft.prizes:
        issues.append(ValidationIssue(field="prizes", code="NO_PRIZE_SELECTED", message="方案没有可用奖品，请人工圈选"))

    for index, plan_prize in enumerate(draft.prizes):
        field_prefix = f"prizes[{index}]"
        if plan_prize.prize_id in seen_ids:
            issues.append(ValidationIssue(
                field=f"{field_prefix}.prize_id",
                code="DUPLICATE_PRIZE",
                message="同一奖品不能重复配置",
            ))
        seen_ids.add(plan_prize.prize_id)

        catalog_prize = catalog.get(plan_prize.prize_id)
        if catalog_prize is None:
            issues.append(ValidationIssue(
                field=f"{field_prefix}.prize_id",
                code="PRIZE_NOT_FOUND",
                message=f"奖品{plan_prize.prize_id}不在可选奖品目录中",
            ))

        if plan_prize.prize_amount <= 0:
            issues.append(ValidationIssue(
                field=f"{field_prefix}.prize_amount",
                code="AMOUNT_INVALID",
                message="奖品数量必须大于0",
            ))

        if plan_prize.prize_tiers not in ALLOWED_TIERS:
            issues.append(ValidationIssue(
                field=f"{field_prefix}.prize_tiers",
                code="TIER_INVALID",
                message="奖项等级只能是一、二、三等奖",
            ))

        if catalog_prize is not None and catalog_prize.price is not None and plan_prize.prize_amount > 0:
            try:
                total_cost += Decimal(str(catalog_prize.price)) * plan_prize.prize_amount
            except (InvalidOperation, ValueError):
                issues.append(ValidationIssue(
                    field=f"{field_prefix}.price",
                    code="PRICE_INVALID",
                    message="奖品价格不是有效数字",
                ))

    if hard_budget is not None and total_cost > Decimal(str(hard_budget)):
        issues.append(ValidationIssue(
            field="prizes",
            code="BUDGET_EXCEEDED",
            message=f"方案预计费用{total_cost}元，超过预算上限{hard_budget}元",
        ))
    return issues
