import assert from "node:assert/strict";
import { afterEach, before, describe, it } from "node:test";
import { apiJson, clearToken, setToken, TOKEN_KEY } from "./api.ts";

function installMemoryLocalStorage() {
  const store = new Map<string, string>();
  const memory = {
    getItem(key: string) {
      return store.has(key) ? store.get(key)! : null;
    },
    setItem(key: string, value: string) {
      store.set(key, String(value));
    },
    removeItem(key: string) {
      store.delete(key);
    },
    clear() {
      store.clear();
    },
  };
  Object.defineProperty(globalThis, "localStorage", {
    value: memory,
    configurable: true,
  });
}

describe("apiJson", () => {
  const originalFetch = globalThis.fetch;

  before(() => {
    installMemoryLocalStorage();
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    clearToken();
  });

  it("maps empty 401 body to 未登录 message and clears token", async () => {
    setToken("stale-token");
    globalThis.fetch = async () =>
      new Response(null, { status: 401, statusText: "Unauthorized" });

    await assert.rejects(
      () => apiJson("/api/learners"),
      (err: unknown) => {
        assert.ok(err instanceof Error);
        assert.match(err.message, /未登录|令牌/);
        assert.doesNotMatch(err.message, /Unexpected end of JSON|json/i);
        return true;
      },
    );
    assert.equal(localStorage.getItem(TOKEN_KEY), null);
  });

  it("surfaces business message from JSON error body", async () => {
    globalThis.fetch = async () =>
      new Response(
        JSON.stringify({ code: "BAD_REQUEST", message: "topic 不能为空", data: null }),
        { status: 400, headers: { "Content-Type": "application/json" } },
      );

    await assert.rejects(
      () => apiJson("/api/cet/sessions", { method: "POST", body: "{}" }),
      (err: unknown) => {
        assert.ok(err instanceof Error);
        assert.equal(err.message, "topic 不能为空");
        return true;
      },
    );
  });
});
