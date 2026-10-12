import express from "express";
import cors from "cors";
import { rateLimit } from "express-rate-limit";

export function createApp({ apiKey = process.env.OPENAI_API_KEY, fetchImpl = fetch } = {}) {
  const app = express();
  const allowedOrigin = process.env.ALLOWED_ORIGIN || "https://appassets.androidplatform.net";
  app.use(cors({ origin: allowedOrigin }));
  app.use(rateLimit({ windowMs: 60_000, limit: 20, standardHeaders: "draft-8", legacyHeaders: false }));
  app.get("/health", (_req, res) => res.json({ status: "ok" }));
  app.post("/session", express.text({ type: ["application/sdp", "text/plain"], limit: "64kb" }), async (req, res) => {
    if (!apiKey) return res.status(503).json({ error: "OPENAI_API_KEY is not configured" });
    if (!req.body?.startsWith("v=")) return res.status(400).json({ error: "A valid SDP offer is required" });
    const session = JSON.stringify({
      type: "realtime",
      model: process.env.OPENAI_REALTIME_MODEL || "gpt-realtime-2.1",
      instructions: "You are a warm English speaking coach. Hold a natural conversation. After every learner turn, reply to its meaning first. Then briefly correct grammar, word choice, or pronunciation only when useful, say the improved sentence, and explain one key point. Do not interrupt. Keep each reply under 45 seconds and end with one relevant question.",
      output_modalities: ["audio"],
      audio: {
        input: { transcription: { model: "gpt-4o-mini-transcribe", language: "en" }, turn_detection: { type: "semantic_vad" } },
        output: { voice: process.env.OPENAI_REALTIME_VOICE || "marin" }
      }
    });
    const form = new FormData();
    form.set("sdp", req.body);
    form.set("session", session);
    try {
      const upstream = await fetchImpl("https://api.openai.com/v1/realtime/calls", {
        method: "POST",
        headers: { Authorization: `Bearer ${apiKey}`, "OpenAI-Safety-Identifier": req.get("X-User-ID") || "anonymous-english-learner" },
        body: form
      });
      const body = await upstream.text();
      res.status(upstream.status).type(upstream.headers.get("content-type") || "text/plain").send(body);
    } catch (error) {
      console.error("Realtime session error", error);
      res.status(502).json({ error: "Could not connect to the Realtime service" });
    }
  });
  return app;
}

if (process.argv[1]?.endsWith("server.mjs")) {
  const port = Number(process.env.PORT || 3000);
  createApp().listen(port, () => console.log(`Realtime server listening on ${port}`));
}
