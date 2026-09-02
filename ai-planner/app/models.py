from __future__ import annotations

from typing import Any, Optional, TypedDict

from pydantic import BaseModel, Field


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
