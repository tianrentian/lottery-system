from __future__ import annotations

import pytest

from app.main import notification_templates
from app.models import NotificationTemplateRequest, NotificationTemplateResponse
from app.notification import (
    DeepSeekNotificationGenerator,
    _replace_virtual_winner,
    validate_notification_templates,
)


def request() -> NotificationTemplateRequest:
    return NotificationTemplateRequest(
        activity_name="夏日清凉抽奖",
        activity_description="为公司员工准备的夏日福利活动",
        prize_name="无线耳机",
        prize_description="适合通勤时听音乐",
        prize_tier="二等奖",
    )


def test_mock_endpoint_generates_two_audiences_without_winner_data(monkeypatch):
    monkeypatch.setenv("AI_PLANNER_MODE", "mock")

    response = notification_templates(request())

    assert response.mail_subject == "恭喜中奖｜夏日清凉抽奖"
    assert len(response.variants) == 3
    assert all("{{winnerName}}" in item.personal_text for item in response.variants)
    assert all("恭喜" in item.group_text for item in response.variants)
    assert all("无线耳机" in item.personal_text for item in response.variants)


def test_validation_rejects_unknown_placeholder():
    response = NotificationTemplateResponse.model_validate({
        "mail_subject": "恭喜中奖｜夏日清凉抽奖",
        "variants": [
            {"personal_text": "{{user}}获得无线耳机", "group_text": "恭喜{{winnerName}}获得无线耳机"},
            {"personal_text": "{{winnerName}}获得无线耳机A", "group_text": "恭喜{{winnerName}}获得无线耳机A"},
            {"personal_text": "{{winnerName}}获得无线耳机B", "group_text": "恭喜{{winnerName}}获得无线耳机B"},
        ],
    })

    with pytest.raises(ValueError, match="占位符"):
        validate_notification_templates(request(), response)


def test_validation_rejects_gender_inference_from_virtual_name():
    response = NotificationTemplateResponse.model_validate({
        "mail_subject": "恭喜中奖｜夏日清凉抽奖",
        "variants": [{
            "personal_text": "好运落在{{winnerName}}身边，恭喜你获得无线耳机",
            "group_text": "恭喜{{winnerName}}获得无线耳机，愿音乐陪伴他度过通勤时光",
        }],
    })

    with pytest.raises(ValueError, match="性别或身份"):
        validate_notification_templates(request(), response)


class _StructuredOutput:
    def __init__(self, schema):
        self.schema = schema
        self.messages = None

    def invoke(self, messages):
        self.messages = messages
        return self.schema.model_validate({
            "mail_subject": "恭喜中奖｜夏日清凉抽奖",
            "variants": [
                {
                    "personal_text": "夏日好运绕了一圈，终于停在李明身边！恭喜你抽中无线耳机1！",
                    "group_text": "幸运名单揭晓！恭喜李明获得无线耳机1！",
                },
                {
                    "personal_text": "清凉惊喜已经送达，无线耳机2属于你！",
                    "group_text": "第二份好运有了主人，恭喜李明收获无线耳机2！",
                },
                {
                    "personal_text": "李明，今天的幸运主角就是你，恭喜收获无线耳机3！",
                    "group_text": "掌声送给李明，恭喜李明抽中无线耳机3！",
                },
                {
                    "personal_text": "清凉福利准时抵达，恭喜李明把无线耳机4带回家，愿音乐陪伴你的通勤时光！",
                    "group_text": "清凉福利找到主人啦，恭喜李明获得无线耳机4！",
                },
                {
                    "personal_text": "无线耳机5为这个夏日添了惊喜，李明，愿音乐常伴你左右！",
                    "group_text": "夏日好礼揭晓，恭喜李明将无线耳机5收入囊中！",
                },
            ],
        })


class _FakeChatOpenAI:
    instance = None

    def __init__(self, **kwargs):
        self.init_kwargs = kwargs
        self.structured = None
        _FakeChatOpenAI.instance = self

    def with_structured_output(self, schema, **kwargs):
        self.structured_kwargs = kwargs
        self.structured = _StructuredOutput(schema)
        return self.structured


def test_real_generator_tells_model_about_personal_and_group_audiences(monkeypatch):
    import langchain_openai

    monkeypatch.setattr(langchain_openai, "ChatOpenAI", _FakeChatOpenAI)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")

    generator = DeepSeekNotificationGenerator()
    response = generator.generate(request())

    chat = _FakeChatOpenAI.instance
    system_prompt = chat.structured.messages[0][1]
    assert chat.structured_kwargs == {"method": "function_calling"}
    assert "直接写给中奖者本人" in system_prompt
    assert "钉钉群" in system_prompt
    assert "虚拟中奖者姓名" in system_prompt
    assert "谐音梗" in system_prompt
    assert "不得使用“他”“她”" in system_prompt
    assert "一次生成5组" in system_prompt
    assert len(response.variants) == 3
    assert all(item.personal_text.count("{{winnerName}}") == 1 for item in response.variants)
    assert all(item.group_text.count("{{winnerName}}") == 1 for item in response.variants)
    assert all("李明" not in item.personal_text for item in response.variants)
    assert all("李明" not in item.group_text for item in response.variants)
    assert response.variants[0].personal_text.startswith("夏日好运绕了一圈")
    assert all("无线耳机2" not in item.personal_text for item in response.variants)
    assert all("无线耳机3" not in item.personal_text for item in response.variants)


def test_virtual_winner_must_appear_exactly_once():
    with pytest.raises(ValueError, match="必须且只能完整出现一次"):
        _replace_virtual_winner("恭喜你抽中无线耳机", "李明")

    with pytest.raises(ValueError, match="必须且只能完整出现一次"):
        _replace_virtual_winner("李明，恭喜李明抽中无线耳机", "李明")

    assert _replace_virtual_winner("好运最终停在李明身边", "李明") == "好运最终停在{{winnerName}}身边"


def test_generator_keeps_one_or_two_valid_candidates_instead_of_failing(monkeypatch):
    import langchain_openai

    monkeypatch.setattr(langchain_openai, "ChatOpenAI", _FakeChatOpenAI)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    original_invoke = _StructuredOutput.invoke

    def invoke_with_two_valid(self, messages):
        result = original_invoke(self, messages)
        result.variants[3].personal_text = "这条没有虚拟姓名，但包含无线耳机4"
        return result

    monkeypatch.setattr(_StructuredOutput, "invoke", invoke_with_two_valid)

    response = DeepSeekNotificationGenerator().generate(request())

    assert len(response.variants) == 2
    assert all(item.personal_text.count("{{winnerName}}") == 1 for item in response.variants)


def test_generator_fails_only_when_all_candidates_are_invalid(monkeypatch):
    import langchain_openai

    monkeypatch.setattr(langchain_openai, "ChatOpenAI", _FakeChatOpenAI)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")
    original_invoke = _StructuredOutput.invoke

    def invoke_with_no_valid_candidate(self, messages):
        result = original_invoke(self, messages)
        for item in result.variants:
            item.personal_text = item.personal_text.replace("李明", "中奖者")
        return result

    monkeypatch.setattr(_StructuredOutput, "invoke", invoke_with_no_valid_candidate)

    with pytest.raises(ValueError, match="候选均未通过校验"):
        DeepSeekNotificationGenerator().generate(request())
