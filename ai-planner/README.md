# 抽奖系统 AI 服务

该 Python 服务同时承载两类 AI 能力：

- 活动策划使用 LangGraph 编排分析、生成、校验和修正流程。服务只生成活动草稿，不直接创建活动；Java 后端在管理员圈选人员并确认后负责最终创建。
- 中奖通知使用 LangChain 一次生成五组结构化候选：个人邮件与钉钉群通知都用可替换的虚拟姓名生成完整段落，Python 逐组校验、去重并保留最多三组合格文案，再把虚拟姓名转换为唯一占位符。即使只有一至两组合格也会继续使用；全部不合格时由 Java 使用本地兜底。Java 后端负责缓存、填入真实中奖者姓名并发送。

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
- `POST /notification-templates`：根据活动与奖品信息生成通知模板，不接收中奖者姓名、邮箱或手机号。

LangGraph 节点负责分析、生成、代码校验和最多两轮带错误反馈的修正；最终业务校验仍由 Java 后端执行。
