from __future__ import annotations

from typing import Any, Optional, TypedDict

from pydantic import BaseModel, Field, field_validator


ALLOWED_TIERS = ("FIRST_PRIZE", "SECOND_PRIZE", "THIRD_PRIZE")


class PrizeOption(BaseModel):
    prize_id: int
    name: str
    price: Optional[float] = None
    description: Optional[str] = None


class ActivityPlanPrize(BaseModel):
    prize_id: int
    prize_amount: int = Field(gt=0)
    prize_tiers: str


class ActivityPlanDraft(BaseModel):
    activity_name: str = ""
    description: str = ""
    prizes: list[ActivityPlanPrize] = Field(default_factory=list)
    assumptions: list[str] = Field(default_factory=list)
    warnings: list[str] = Field(default_factory=list)
    participant_hint: str = ""


class RequestAnalysis(BaseModel):
    needs_clarification: bool = False
    clarification_question: Optional[str] = None
    clarification_options: list[str] = Field(default_factory=list)
    assumptions: list[str] = Field(default_factory=list)
    participant_hint: str = ""


class PlannerRequest(BaseModel):
    prompt: str = Field(min_length=1, max_length=300)
    available_prizes: list[PrizeOption] = Field(default_factory=list)
    clarification_answer: Optional[str] = None
    hard_budget: Optional[float] = Field(default=None, ge=0)


class NotificationTemplateRequest(BaseModel):
    activity_name: str = Field(min_length=1, max_length=100)
    activity_description: str = Field(default="", max_length=500)
    prize_name: str = Field(min_length=1, max_length=100)
    prize_description: str = Field(default="", max_length=500)
    prize_tier: str = Field(default="", max_length=30)


class NotificationVariantDraft(BaseModel):
    personal_text: str = Field(
        min_length=1,
        max_length=180,
        description="包含一次虚拟中奖者姓名、直接面向中奖者本人的完整连贯文案",
    )
    group_text: str = Field(
        min_length=1,
        max_length=180,
        description="包含一次虚拟中奖者姓名、面向群成员公布结果的完整连贯文案",
    )


class NotificationTemplateDraft(BaseModel):
    mail_subject: str = Field(min_length=1, max_length=80)
    variants: list[NotificationVariantDraft] = Field(min_length=5, max_length=5)


class NotificationVariant(BaseModel):
    personal_text: str = Field(
        min_length=1,
        max_length=200,
        description="直接写给中奖者本人的第二人称祝贺文案",
    )
    group_text: str = Field(
        min_length=1,
        max_length=200,
        description="面向钉钉群成员公布中奖结果的群体公告文案",
    )


class NotificationTemplateResponse(BaseModel):
    mail_subject: str = Field(min_length=1, max_length=80)
    variants: list[NotificationVariant] = Field(min_length=1, max_length=3)

    @field_validator("variants")
    @classmethod
    def variants_must_be_distinct(cls, variants: list[NotificationVariant]) -> list[NotificationVariant]:
        personal_texts = {item.personal_text for item in variants}
        group_texts = {item.group_text for item in variants}
        if len(personal_texts) != len(variants) or len(group_texts) != len(variants):
            raise ValueError("通知文案不能重复")
        return variants


class ValidationIssue(BaseModel):
    field: str
    code: str
    message: str


class PlannerState(TypedDict, total=False):
    prompt: str
    available_prizes: list[dict[str, Any]]
    hard_budget: Optional[float]
    clarification_answer: Optional[str]
    draft: Optional[dict[str, Any]]
    issues: list[dict[str, Any]]
    assumptions: list[str]
    warnings: list[str]
    participant_hint: str
    clarification_question: Optional[str]
    clarification_options: list[str]
    repair_attempts: int
    status: str
    error_message: Optional[str]
