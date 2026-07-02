import express from "express";
import { router } from "./routes.ts";

const PORT = Number(process.env.PORT ?? 9091);

const app = express();
app.use(express.json());
app.use("/api", router);

app.get("/api/ping", (_req, res) => res.json({ ok: true }));

app.listen(PORT, "127.0.0.1", () => {
  console.log(`[console-server] listening on http://127.0.0.1:${PORT}`);
});
