# 抽奖系统 AI 策划服务

基于 LangGraph 的抽奖活动策划服务。服务只生成活动草稿，不直接创建活动；Java 后端在管理员圈选人员并确认后负责最终创建。

## 本地运行

```bash
python3 -m venv .venv
.venv/bin/pip install -e '.[test]'
AI_PLANNER_MODE=mock .venv/bin/uvicorn app.main:app --port 8090
```

默认使用 Mock 模式，不会产生 DeepSeek 费用。演示或生产环境设置 `AI_PLANNER_MODE=real`，并通过环境变量提供 `DEEPSEEK_API_KEY`；默认模型为 `deepseek-v4-flash`，也可以通过 `DEEPSEEK_MODEL_NAME` 覆盖。密钥不会返回前端。

## 接口

- `GET /health`：查看服务状态和当前模式。
- `POST /plan`：输入不超过 300 字的策划描述、现有奖品目录和可选预算，返回 `READY`、`WAITING_USER` 或 `NEEDS_MANUAL` 状态及活动草稿。

LangGraph 节点负责分析、生成、代码校验和最多两轮带错误反馈的修正；最终业务校验仍由 Java 后端执行。
