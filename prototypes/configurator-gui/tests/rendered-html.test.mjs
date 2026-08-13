import assert from "node:assert/strict";
import test from "node:test";

async function render() {
  const workerUrl = new URL("../dist/server/index.js", import.meta.url);
  workerUrl.searchParams.set("test", `${process.pid}-${Date.now()}`);
  const { default: worker } = await import(workerUrl.href);

  return worker.fetch(
    new Request("http://localhost/", { headers: { accept: "text/html" } }),
    { ASSETS: { fetch: async () => new Response("Not found", { status: 404 }) } },
    { waitUntil() {}, passThroughOnException() {} },
  );
}

test("server-renders the configurator prototype shell", async () => {
  const response = await render();
  assert.equal(response.status, 200);
  assert.match(response.headers.get("content-type") ?? "", /^text\/html\b/i);

  const html = await response.text();
  assert.match(html, /<title>天穹配置器 GUI 交互原型<\/title>/i);
  assert.match(html, /天穹配置器/);
  assert.match(html, /AmicBeam-0/);
  assert.doesNotMatch(html, /线路连接面/);
  assert.match(html, /红石/);
  assert.match(html, /260 × 250/);
  assert.doesNotMatch(html, /Your site is taking shape|react-loading-skeleton/);
});
