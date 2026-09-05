from __future__ import annotations

import json
import os
import re

from .models import (
    NotificationTemplateDraft,
    NotificationTemplateRequest,
    NotificationTemplateResponse,
    NotificationVariant,
)


WINNER_PLACEHOLDER = "{{winnerName}}"
PLACEHOLDER_PATTERN = re.compile(r"{{[^{}]+}}")
FORBIDDEN_INTERNAL_WORDS = ("预算", "圈选人员", "奖品配置", "后台操作", "管理员")
VIRTUAL_WINNER_CANDIDATES = ("李明", "周宁", "陈安", "林嘉", "沈悦")
GENDERED_REFERENCE_PATTERN = re.compile(
    r"[他她](?:的|将|已|也|会|能|可|正|来|去|享受|度过|收获|拥有|获得|抽中)"
)


class DeepSeekNotificationGenerator:
    """使用 LangChain 一次生成个人邮件与群公告两种受众的结构化通知模板。"""

    def __init__(self) -> None:
        try:
            from langchain_openai import ChatOpenAI
        except ImportError as exc:  # 仅真实模式会走到这里
            raise RuntimeError("真实模式需要安装 langchain-openai") from exc

        api_key = os.getenv("DEEPSEEK_API_KEY")
        if not api_key:
            raise RuntimeError("未配置 DEEPSEEK_API_KEY")
        self._chat = ChatOpenAI(
            model=os.getenv("DEEPSEEK_MODEL_NAME", "deepseek-v4-flash"),
            api_key=api_key,
            base_url=os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
            temperature=0.5,
            extra_body={"thinking": {"type": "disabled"}},
        )

    def generate(self, request: NotificationTemplateRequest) -> NotificationTemplateResponse:
        virtual_winner = _select_virtual_winner(request)
        structured = self._chat.with_structured_output(
            NotificationTemplateDraft,
            method="function_calling",
        )
        payload = request.model_dump()
        payload["virtual_winner_name"] = virtual_winner
        draft = structured.invoke([
            ("system", """你是抽奖活动通知文案策划师。请根据真实活动与奖品信息，一次生成5组风格不同的中奖通知候选，系统会校验并选取其中最多3组。
请求中的 virtual_winner_name 是虚拟中奖者姓名，仅用于帮助你确定姓名在中文句子中的自然位置，系统随后会把它替换为任意真实姓名。
每组必须同时包含两种不同受众的完整、连贯文案：
1. personal_text：直接写给中奖者本人，使用第二人称“你”，语气亲切、有惊喜感；必须把 virtual_winner_name 自然地写在完整段落中，可以放在开头、中间或结尾；不要使用“大家”“掌声送给”等面向群体的表达。
2. group_text：由活动组织者在钉钉群向群成员公布结果，使用公告视角介绍中奖者；必须包含“恭喜”，可以使用“幸运名单揭晓”“掌声送给”等群体表达。
共同要求：
- 必须结合本次活动主题、奖品特点和奖项等级创作，不能生成任何活动和奖品都能使用的万能文案；
- 每条 personal_text 和 group_text 都必须原样、完整且只出现一次 virtual_winner_name；两种文案都不要输出花括号或其他程序占位符；
- 虚拟姓名只是可替换的语法定位符，不是创作素材；不得围绕姓名设计谐音梗、拆字、昵称、押韵或双关，不得根据姓名推测性别、性格或身份；
- 除 virtual_winner_name 外，不得使用“他”“她”“TA”“先生”“女士”“小伙”“姑娘”等带性别或身份推测的称呼；需要再次指代中奖者时使用“这位幸运儿”；
- 文案替换成任意其他姓名后仍必须自然通顺，创意只来自活动主题、奖品特点和奖项等级；
- mail_subject 必须完整包含 activity_name，每条 personal_text 和 group_text 必须完整包含 prize_name；
- 不得编造领奖时间、地点、奖品参数或输入中没有的信息；
- 三组文案的开头和句式不能完全相同，个人文案和群文案不能只是简单替换人称；
- 文案自然、简短、有祝贺氛围，不得出现预算、人员圈选、奖品配置、后台操作等内部语言；
- 只返回 NotificationTemplateDraft 规定的数据结构。"""),
            ("human", json.dumps(payload, ensure_ascii=False)),
        ])
        return NotificationTemplateResponse(
            mail_subject=draft.mail_subject,
            variants=_filter_valid_variants(request, draft, virtual_winner),
        )


def _select_virtual_winner(request: NotificationTemplateRequest) -> str:
    source_text = json.dumps(request.model_dump(), ensure_ascii=False)
    for candidate in VIRTUAL_WINNER_CANDIDATES:
        if candidate not in source_text:
            return candidate
    raise ValueError("无法选择不与活动内容冲突的虚拟中奖者姓名")


def _replace_virtual_winner(text: str, virtual_winner: str) -> str:
    """把自然语句中的虚拟姓名替换为唯一占位符，不接受模型自行输出的标记。"""
    if PLACEHOLDER_PATTERN.search(text):
        raise ValueError("AI原始文案不得包含程序占位符")
    if text.count(virtual_winner) != 1:
        raise ValueError("虚拟中奖者姓名必须且只能完整出现一次")
    return text.replace(virtual_winner, WINNER_PLACEHOLDER, 1)


def _filter_valid_variants(
    request: NotificationTemplateRequest,
    draft: NotificationTemplateDraft,
    virtual_winner: str,
) -> list[NotificationVariant]:
    """逐组过滤模型候选，最多保留三组；单组失败不拖垮整次生成。"""
    valid: list[NotificationVariant] = []
    personal_texts: set[str] = set()
    group_texts: set[str] = set()
    for item in draft.variants:
        try:
            variant = NotificationVariant(
                personal_text=_replace_virtual_winner(item.personal_text, virtual_winner),
                group_text=_replace_virtual_winner(item.group_text, virtual_winner),
            )
            _validate_text(request, variant.personal_text, require_congratulations=False)
            _validate_text(request, variant.group_text, require_congratulations=True)
            if variant.personal_text == variant.group_text:
                raise ValueError("个人通知与群通知不能相同")
            if (variant.personal_text in personal_texts
                    or variant.group_text in group_texts):
                continue
        except ValueError:
            continue

        valid.append(variant)
        personal_texts.add(variant.personal_text)
        group_texts.add(variant.group_text)
        if len(valid) == 3:
            break

    if not valid:
        raise ValueError("AI生成的通知候选均未通过校验")
    return valid


def validate_notification_templates(
    request: NotificationTemplateRequest,
    response: NotificationTemplateResponse,
) -> NotificationTemplateResponse:
    if request.activity_name not in response.mail_subject:
        raise ValueError("邮件标题必须包含活动名称")

    for variant in response.variants:
        _validate_text(request, variant.personal_text, require_congratulations=False)
        _validate_text(request, variant.group_text, require_congratulations=True)
        if variant.personal_text == variant.group_text:
            raise ValueError("个人通知与群通知不能相同")
    return response


def _validate_text(
    request: NotificationTemplateRequest,
    text: str,
    require_congratulations: bool,
) -> None:
    placeholders = PLACEHOLDER_PATTERN.findall(text)
    if placeholders != [WINNER_PLACEHOLDER]:
        raise ValueError("通知文案必须且只能包含一次 {{winnerName}} 占位符")
    if request.prize_name not in text:
        raise ValueError("通知文案必须直接包含奖品名称")
    if not require_congratulations and "你" not in text:
        raise ValueError("个人通知必须直接面向中奖者本人")
    if require_congratulations and "恭喜" not in text:
        raise ValueError("钉钉群通知必须包含“恭喜”")
    if (GENDERED_REFERENCE_PATTERN.search(text)
            or any(word in text for word in ("TA", "先生", "女士", "小伙", "姑娘"))):
        raise ValueError("通知文案不得推测中奖者性别或身份")
    if any(word in text for word in FORBIDDEN_INTERNAL_WORDS):
        raise ValueError("通知文案不得包含后台配置语言")


def mock_notification_templates(
    request: NotificationTemplateRequest,
) -> NotificationTemplateResponse:
    activity = request.activity_name
    prize = request.prize_name
    tier = f"{request.prize_tier}——" if request.prize_tier else ""
    return NotificationTemplateResponse.model_validate({
        "mail_subject": f"恭喜中奖｜{activity}",
        "variants": [
            {
                "personal_text": f"惊喜已揭晓！{{{{winnerName}}}}，恭喜你在「{activity}」中抽中{tier}{prize}，快来接住这份好运吧！",
                "group_text": f"幸运名单揭晓！恭喜{{{{winnerName}}}}在「{activity}」中抽中{tier}{prize}，掌声送给本轮幸运儿！",
            },
            {
                "personal_text": f"{{{{winnerName}}}}，属于你的好运来啦！你在「{activity}」中收获了{tier}{prize}，愿这份惊喜点亮今天！",
                "group_text": f"好运有了新主人！恭喜{{{{winnerName}}}}在「{activity}」中获得{tier}{prize}！",
            },
            {
                "personal_text": f"幸运降临！{{{{winnerName}}}}，恭喜你从「{activity}」中把{tier}{prize}带回家，今天的惊喜属于你！",
                "group_text": f"本轮幸运时刻到来：恭喜{{{{winnerName}}}}在「{activity}」中喜提{tier}{prize}！",
            },
        ],
    })
