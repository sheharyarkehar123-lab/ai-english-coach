import test from "node:test";
import assert from "node:assert/strict";
import { createApp } from "./server.mjs";

async function listen(app) {
  return new Promise(resolve => { const server = app.listen(0, () => resolve(server)); });
}

test("health endpoint and missing-key protection", async () => {
  const server = await listen(createApp({ apiKey: "" }));
  try {
    const base = `http://127.0.0.1:${server.address().port}`;
    assert.equal((await fetch(`${base}/health`)).status, 200);
    const response = await fetch(`${base}/session`, { method: "POST", headers: { "content-type": "application/sdp" }, body: "v=0\r\n" });
    assert.equal(response.status, 503);
  } finally { server.close(); }
});

test("proxies a valid SDP offer without exposing the API key", async () => {
  let auth;
  const app = createApp({ apiKey: "server-secret", fetchImpl: async (_url, options) => {
    auth = options.headers.Authorization;
    return new Response(JSON.stringify({ transport: { type: "webrtc", sdp: "answer" } }), { status: 201, headers: { "content-type": "application/json" } });
  }});
  const server = await listen(app);
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/session`, { method: "POST", headers: { "content-type": "application/sdp" }, body: "v=0\r\n" });
    assert.equal(response.status, 201);
    assert.equal(auth, "Bearer server-secret");
    assert.equal((await response.json()).transport.sdp, "answer");
  } finally { server.close(); }
});
