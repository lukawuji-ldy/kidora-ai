# -*- coding: utf-8 -*-
"""Render high-res CET Agent architecture PNG for README."""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

W, H = 3200, 2000
OUT = Path(__file__).with_name("kidora-ai-cet-agent-architecture.png")

# Palette — clean tech, avoid purple/cream stereotypes
BG = (248, 250, 252)
NAVY = (15, 40, 70)
INK = (30, 41, 59)
MUTED = (71, 85, 105)
LINE = (148, 163, 184)
WHITE = (255, 255, 255)
PANEL = (255, 255, 255)
ACCENT = (14, 116, 144)  # teal
ACCENT_SOFT = (207, 250, 254)
PLAN = (6, 95, 70)
PLAN_SOFT = (209, 250, 229)
WARN = (180, 83, 9)
WARN_SOFT = (254, 243, 199)
SAFE = (185, 28, 28)
SAFE_SOFT = (254, 226, 226)
MCP = (30, 64, 175)
MCP_SOFT = (219, 234, 254)
CHAT = (55, 65, 81)
CHAT_SOFT = (241, 245, 249)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = [
        (r"C:\Windows\Fonts\msyhbd.ttc", True),
        (r"C:\Windows\Fonts\msyh.ttc", False),
        (r"C:\Windows\Fonts\simhei.ttf", False),
        (r"C:\Windows\Fonts\arialbd.ttf", True),
        (r"C:\Windows\Fonts\arial.ttf", False),
    ]
    for path, is_bold in candidates:
        if bold and not is_bold and "bd" not in path.lower() and "hei" not in path.lower():
            continue
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def round_rect(draw: ImageDraw.ImageDraw, xy, fill, outline=None, width=2, radius=24):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


def text_center(draw, cx, cy, text, fnt, fill=INK):
    bbox = draw.textbbox((0, 0), text, font=fnt)
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    draw.text((cx - tw / 2, cy - th / 2), text, font=fnt, fill=fill)


def text_left(draw, x, y, text, fnt, fill=INK):
    draw.text((x, y), text, font=fnt, fill=fill)


def arrow(draw, x1, y1, x2, y2, fill=ACCENT, width=5):
    draw.line((x1, y1, x2, y2), fill=fill, width=width)
    # arrow head
    if abs(x2 - x1) >= abs(y2 - y1):
        # horizontal
        direction = 1 if x2 > x1 else -1
        draw.polygon(
            [
                (x2, y2),
                (x2 - 16 * direction, y2 - 10),
                (x2 - 16 * direction, y2 + 10),
            ],
            fill=fill,
        )
    else:
        direction = 1 if y2 > y1 else -1
        draw.polygon(
            [
                (x2, y2),
                (x2 - 10, y2 - 16 * direction),
                (x2 + 10, y2 - 16 * direction),
            ],
            fill=fill,
        )


def main() -> None:
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    f_title = font(64, bold=True)
    f_sub = font(34)
    f_h = font(36, bold=True)
    f_box = font(28, bold=True)
    f_sm = font(24)
    f_xs = font(20)

    # Title
    text_center(d, W // 2, 70, "Kidora AI · CET Agent 架构", f_title, NAVY)
    text_center(
        d,
        W // 2,
        130,
        "核心模式：Plan-and-Execute（大循环） + Conversational Tutor（小循环）",
        f_sub,
        MUTED,
    )

    # ===== Clients =====
    round_rect(d, (80, 190, 3120, 380), WHITE, LINE, 3, 28)
    text_left(d, 110, 210, "客户端", f_h, ACCENT)

    round_rect(d, (140, 270, 980, 350), ACCENT_SOFT, ACCENT, 3, 18)
    text_center(d, 560, 295, "kidora-web :3000", f_box, NAVY)
    text_center(d, 560, 330, "家长 / 儿童 · User JWT · CET / Chat UI", f_xs, MUTED)

    round_rect(d, (1100, 270, 1940, 350), CHAT_SOFT, CHAT, 3, 18)
    text_center(d, 1520, 295, "kidora-admin-web :5173（旁路）", f_box, NAVY)
    text_center(d, 1520, 330, "运营配置 · Admin JWT · 不执行陪练循环", f_xs, MUTED)

    round_rect(d, (2100, 270, 3040, 350), WARN_SOFT, WARN, 3, 18)
    text_center(d, 2570, 295, "分工提示", f_box, WARN)
    text_center(d, 2570, 330, "管理台只配 LLM / Prompt / MCP，运行时执行 Agent", f_xs, MUTED)

    # arrows down from clients
    arrow(d, 560, 350, 560, 430, ACCENT)
    arrow(d, 1520, 350, 1520, 430, CHAT)

    # ===== Runtime band =====
    round_rect(d, (80, 440, 3120, 1480), WHITE, LINE, 3, 28)
    text_left(d, 110, 460, "运行时分工（本仓库 kidora-ai）", f_h, ACCENT)

    # Left: agent-server
    round_rect(d, (120, 530, 620, 1420), CHAT_SOFT, CHAT, 3, 22)
    text_center(d, 370, 575, "kidora-agent-server", f_box, NAVY)
    text_center(d, 370, 615, ":8080", f_sm, MUTED)
    for i, line in enumerate(
        [
            "· Auth / JWT",
            "· 通用 Chat SSE",
            "· 学习者 CRUD",
            "· Flyway 迁移",
            "",
            "Agent 模式",
            "有界 ReactAgent",
            "（与 CET 大循环分离）",
            "",
            "依赖",
            "kidora-agent-core",
            "kidora-memory",
        ]
    ):
        text_center(d, 370, 680 + i * 48, line, f_sm if line.startswith("·") or line.startswith("（") or line in ("依赖",) or "kidora" in line else f_box if line in ("Agent 模式", "有界 ReactAgent") else f_sm, INK if line else MUTED)

    # Safety strip
    round_rect(d, (660, 530, 820, 1420), SAFE_SOFT, SAFE, 3, 18)
    # vertical label
    safe_lines = ["S", "a", "f", "e", "t", "y", "", "独", "立", "闸", "门"]
    for i, ch in enumerate(safe_lines):
        text_center(d, 740, 600 + i * 58, ch, f_box, SAFE)

    # Center: CET Plan-and-Execute
    round_rect(d, (860, 530, 2360, 1420), PLAN_SOFT, PLAN, 4, 22)
    text_center(d, 1610, 575, "CET Plan-and-Execute · cet-tutor-core", f_h, PLAN)
    text_center(d, 1610, 625, "cet-tutor-server :8082 装配 · Plan → Practice → Evaluate → Re-plan", f_sm, MUTED)

    # 5 stage boxes
    stages = [
        (900, 700, 1160, 920, "1. Planner", "制定 TrainingPlan", "仅开课 / 再规划"),
        (1220, 700, 1580, 920, "2. Tutor Practice", "小循环执行阶段", "禁止每轮全量 Planner"),
        (1640, 700, 1900, 920, "3. Evaluator", "阶段 / 结课评测", "达标判定"),
        (1960, 700, 2220, 920, "4. RePlanner", "修订计划", "仅评测触发"),
    ]
    for x1, y1, x2, y2, t, s1, s2 in stages:
        round_rect(d, (x1, y1, x2, y2), WHITE, PLAN, 3, 16)
        text_center(d, (x1 + x2) / 2, y1 + 45, t, f_box, PLAN)
        text_center(d, (x1 + x2) / 2, y1 + 105, s1, f_sm, INK)
        text_center(d, (x1 + x2) / 2, y1 + 150, s2, f_xs, MUTED)

    # arrows between stages
    arrow(d, 1160, 810, 1220, 810, PLAN)
    arrow(d, 1580, 810, 1640, 810, PLAN)
    arrow(d, 1900, 810, 1960, 810, PLAN)

    # Replan feedback dashed back to Tutor
    d.line((2090, 920, 2090, 980, 1400, 980, 1400, 920), fill=WARN, width=4)
    text_center(d, 1745, 1005, "未达标 → Re-plan 回 Practice（非每轮）", f_xs, WARN)

    # Report box
    round_rect(d, (1220, 1040, 1900, 1180), WHITE, PLAN, 3, 16)
    text_center(d, 1560, 1085, "5. Report + Memory", f_box, PLAN)
    text_center(d, 1560, 1135, "家长报告 · 结构化 Memory Action（禁止全文对话落长期记忆）", f_xs, MUTED)
    arrow(d, 1770, 920, 1770, 1040, PLAN)

    # Tutor small loop detail
    round_rect(d, (900, 1220, 1900, 1380), WHITE, ACCENT, 3, 16)
    text_center(d, 1400, 1255, "Tutor 小循环（轮次级）", f_box, ACCENT)
    text_center(d, 1400, 1305, "外教提问  ↔  孩子回答  ↔  轻量分析 / 鼓励反馈  ↔  下一问", f_sm, INK)
    text_center(d, 1400, 1345, "可选 MCP：ASR / TTS / 发音评测 · 开场 opening 亦不调用 Planner", f_xs, MUTED)

    # link Safety note
    text_center(d, 1610, 660, "输入 / 输出均经 Safety，不单靠系统 Prompt", f_xs, SAFE)

    # Right: MCP
    round_rect(d, (2420, 530, 3040, 1420), MCP_SOFT, MCP, 3, 22)
    text_center(d, 2730, 575, "kidora-mcp-server", f_box, MCP)
    text_center(d, 2730, 615, ":8081 · 独立进程", f_sm, MUTED)
    for i, line in enumerate(
        [
            "MCP Tools",
            "· echo_ping",
            "· asr_transcribe",
            "· tts_synthesize",
            "· pronunciation_score",
            "",
            "职责",
            "工具执行，不规划",
            "",
            "依赖",
            "仅 kidora-common",
            "",
            "← Tutor MCP Client",
        ]
    ):
        weight = f_box if line in ("MCP Tools", "职责", "工具执行，不规划", "依赖", "← Tutor MCP Client") else f_sm
        text_center(d, 2730, 690 + i * 48, line, weight, INK)

    # arrow Tutor -> MCP
    arrow(d, 1900, 1300, 2420, 1300, MCP)
    text_center(d, 2160, 1270, "按需调用", f_xs, MCP)

    # ===== Infrastructure =====
    round_rect(d, (80, 1520, 3120, 1920), WHITE, LINE, 3, 28)
    text_left(d, 110, 1545, "共享基础设施与库模块", f_h, ACCENT)

    boxes = [
        (140, 1620, 720, 1860, "PostgreSQL", "库 kidora_ai\n表空间 ts_kidora\n配置 / 会话 / 审计"),
        (780, 1620, 1360, 1860, "LLM API", "OpenAI Compatible\nllm_config 入库\n禁止硬编码 Key"),
        (1420, 1620, 2000, 1860, "kidora-agent-core", "ModelRouter · Prompt\n审计 · ChatFacade\nAgent 工厂"),
        (2060, 1620, 2540, 1860, "kidora-memory", "Learner Profile\n短事实记忆\n结课 Action"),
        (2600, 1620, 3040, 1860, "kidora-common", "公共 DTO\n错误码\n无 Agent 逻辑"),
    ]
    for x1, y1, x2, y2, title, body in boxes:
        round_rect(d, (x1, y1, x2, y2), ACCENT_SOFT, ACCENT, 3, 16)
        text_center(d, (x1 + x2) / 2, y1 + 45, title, f_box, NAVY)
        for j, line in enumerate(body.split("\n")):
            text_center(d, (x1 + x2) / 2, y1 + 110 + j * 36, line, f_xs, MUTED)

    img.save(OUT, "PNG", optimize=True)
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
