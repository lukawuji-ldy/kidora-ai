/**
 * 舞台 payload 归一：前端不做语义判断，只负责把后端事件变成可渲染视图。
 * @author liudy
 */

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  goalStepFromTurnCount,
  PERSONA_FOCUS_STAGE,
  toPropStageView,
} from "./lessonProps.ts";

describe("toPropStageView", () => {
  it("keeps backend order and defaults activeLemma to first asset", () => {
    const view = toPropStageView({
      layout: "propFocus",
      activeLemma: null,
      assets: [
        { lemma: "dog", theme: "pets", url: "/api/cet/props/dog" },
        { lemma: "cat", theme: "pets", url: "/api/cet/props/cat" },
      ],
    });

    assert.equal(view.layout, "propFocus");
    assert.equal(view.activeLemma, "dog");
    assert.deepEqual(view.assets.map((a) => a.lemma), ["dog", "cat"]);
  });

  it("honours explicit activeLemma", () => {
    const view = toPropStageView({
      layout: "propFocus",
      activeLemma: "cat",
      assets: [
        { lemma: "dog", theme: "pets", url: "/api/cet/props/dog" },
        { lemma: "cat", theme: "pets", url: "/api/cet/props/cat" },
      ],
    });

    assert.equal(view.activeLemma, "cat");
  });

  it("collapses personaFocus, empty assets and malformed payloads", () => {
    assert.deepEqual(toPropStageView({ layout: "personaFocus", assets: [] }), PERSONA_FOCUS_STAGE);
    assert.deepEqual(toPropStageView({ layout: "propFocus", assets: [] }), PERSONA_FOCUS_STAGE);
    assert.deepEqual(toPropStageView({}), PERSONA_FOCUS_STAGE);
    assert.deepEqual(toPropStageView(null), PERSONA_FOCUS_STAGE);
    assert.deepEqual(toPropStageView("nope"), PERSONA_FOCUS_STAGE);
  });

  it("drops assets missing lemma or url and defaults theme", () => {
    const view = toPropStageView({
      layout: "propFocus",
      assets: [
        { lemma: "dog" },
        { url: "/api/cet/props/cat" },
        { lemma: "fish", url: "/api/cet/props/fish" },
      ],
    });

    assert.deepEqual(view.assets, [
      { lemma: "fish", theme: "default", url: "/api/cet/props/fish" },
    ]);
  });
});

describe("goalStepFromTurnCount", () => {
  it("maps rounds to three progressive steps", () => {
    assert.equal(goalStepFromTurnCount(1), 1);
    assert.equal(goalStepFromTurnCount(5), 2);
    assert.equal(goalStepFromTurnCount(12), 3);
  });
});
