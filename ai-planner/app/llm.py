from __future__ import annotations

import json
import os
from collections.abc import Sequence
from typing import Optional

from .models import ActivityPlanDraft, ActivityPlanPrize, PrizeOption, RequestAnalysis, ValidationIssue


class DeepSeekActivityPlanner:
    """基于 LangChain 的结构化输出适配器；业务校验仍由 Python 代码负责。"""

    def __init__(self) -> None:
        try:
            from langchain_openai import ChatOpenAI
        except ImportError as exc:  # 仅真实模式会走到这里
            raise RuntimeError("真实模式需要安装 langchain-openai") from exc

        api_key = os.getenv("DEEPSEEK_API_KEY")
        if not api_key:
            raise RuntimeError("未配置 DEEPSEEK_API_KEY")
        self._chat = ChatOpenAI(
            # 使用 DeepSeek-V4-Flash，通过 OpenAI 兼容接口调用
            model=os.getenv("DEEPSEEK_MODEL_NAME", "deepseek-v4-flash"),
            api_key=api_key,
            base_url=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
            temperature=0.2,
            # DeepSeek 思考模式不支持 LangChain 结构化输出使用的强制 tool_choice。
            extra_body={"thinking": {"type": "disabled"}},
        )

    def analyze(self, prompt: str, catalog: Sequence[PrizeOption]) -> RequestAnalysis:
        structured = self._chat.with_structured_output(
            RequestAnalysis,
            method="function_calling",
        )
        return structured.invoke([
            ("system", """你是抽奖活动需求分析器。只判断管理员输入是否存在必须确认的硬冲突。
如果信息不完整但可以采用默认值，不要追问；只在预算与明确奖品要求等条件无法同时满足时追问。
输出必须符合 RequestAnalysis 结构，不要输出人员姓名或其他个人信息。"""),
            ("human", json.dumps({"prompt": prompt, "available_prizes": [item.model_dump() for item in catalog]}, ensure_ascii=False)),
        ])

    def generate(
        self,
        prompt: str,
        catalog: Sequence[PrizeOption],
        previous: Optional[ActivityPlanDraft],
        issues: Sequence[ValidationIssue],
    ) -> ActivityPlanDraft:
        structured = self._chat.with_structured_output(
            ActivityPlanDraft,
            method="function_calling",
        )
        payload = {
            "prompt": prompt,
            "available_prizes": [item.model_dump() for item in catalog],
            "previous_draft": previous.model_dump() if previous else None,
            "validation_issues": [item.model_dump() for item in issues],
        }
        return structured.invoke([
            ("system", """你是抽奖活动策划器。只能从 available_prizes 选择奖品，不能创建新奖品，奖项只能是 FIRST_PRIZE、SECOND_PRIZE、THIRD_PRIZE。
生成活动名称（不超过30字）、描述（不超过200字）和奖品配置；不要选择或输出具体参与人员。
活动描述将直接展示给参与抽奖人员，必须使用自然、有吸引力的活动宣传或邀请口吻，让参与者理解活动主题并感受到参与氛围。
活动描述不得包含预算、成本、奖品选择依据、后台配置说明、策划过程或面向管理员的操作建议，也不要复述“设置一、二、三等奖”等后台配置；相关依据只用于选择奖品，必要时写入 assumptions 或 warnings。
未指定预算或规模时采用基础方案（1到3种奖品、总份数3到6份）。如果有 validation_issues，保留正确字段并针对错误修正。
输出必须符合 ActivityPlanDraft 结构，所有奖品 ID 必须来自目录。"""),
            ("human", json.dumps(payload, ensure_ascii=False)),
        ])
