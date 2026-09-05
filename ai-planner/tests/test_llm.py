from __future__ import annotations

from app.llm import DeepSeekActivityPlanner
from app.models import ActivityPlanDraft, PrizeOption, RequestAnalysis


class _StructuredOutput:
    invocations = []

    def __init__(self, schema):
        self.schema = schema

    def invoke(self, messages):
        self.invocations.append((self.schema, messages))
        return self.schema()


class _FakeChatOpenAI:
    init_calls = []
    structured_output_calls = []

    def __init__(self, **kwargs):
        self.init_calls.append(kwargs)

    def with_structured_output(self, schema, **kwargs):
        self.structured_output_calls.append((schema, kwargs))
        return _StructuredOutput(schema)


def test_deepseek_uses_function_calling_for_structured_output(monkeypatch):
    import langchain_openai

    _FakeChatOpenAI.init_calls = []
    _FakeChatOpenAI.structured_output_calls = []
    _StructuredOutput.invocations = []
    monkeypatch.setattr(langchain_openai, "ChatOpenAI", _FakeChatOpenAI)
    monkeypatch.setenv("DEEPSEEK_API_KEY", "test-key")

    planner = DeepSeekActivityPlanner()
    catalog = [PrizeOption(prize_id=1, name="保温杯", price=49)]
    planner.analyze("年会抽奖", catalog)
    planner.generate("年会抽奖", catalog, previous=None, issues=[])

    assert _FakeChatOpenAI.init_calls[0]["extra_body"] == {
        "thinking": {"type": "disabled"},
    }
    assert _FakeChatOpenAI.structured_output_calls == [
        (RequestAnalysis, {"method": "function_calling"}),
        (ActivityPlanDraft, {"method": "function_calling"}),
    ]

    generation_system_prompt = _StructuredOutput.invocations[1][1][0][1]
    assert "直接展示给参与抽奖人员" in generation_system_prompt
    assert "不得包含预算" in generation_system_prompt
    assert "策划过程" in generation_system_prompt
