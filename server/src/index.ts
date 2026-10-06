import { DurableObject } from "cloudflare:workers";
import { Chess } from "chess.js";

interface Env {
  ROOM: DurableObjectNamespace<ChessRoom>;
}

type Side = "w" | "b" | "s";
type Session = { id: string; name: string; side: Side };
type RoomState = { ply: number; turn: "w" | "b"; result: string | null; fen: string };
type RoomMessage = {
  t?: string;
  room?: string;
  from?: string;
  name?: string;
  seq?: number;
  p?: Record<string, unknown>;
};

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

function roomCode(): string {
  const bytes = new Uint8Array(6);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => CODE_ALPHABET[b % CODE_ALPHABET.length]).join("");
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

const INITIAL_STATE: RoomState = { ply: 0, turn: "w", result: null, fen: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1" };

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const parts = url.pathname.split("/").filter(Boolean);

    if (request.method === "GET" && parts[0] === "health") {
      return json({ ok: true, service: "chess-multiplayer" });
    }

    if (parts[0] === "ws" && parts[1]) {
      const code = parts[1].toUpperCase();
      if (!/^[A-Z0-9]{6}$/.test(code)) return json({ error: "Invalid room code" }, 400);
      if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
        return new Response("WebSocket upgrade required", { status: 426 });
      }
      return env.ROOM.getByName(code).fetch(request);
    }

    if (request.method === "POST" && parts[0] === "rooms") {
      const code = roomCode();
      return json({ code, websocketPath: "/ws/" + code }, 201);
    }

    return json({
      service: "Chess Multiplayer",
      endpoints: { health: "/health", createRoom: "POST /rooms", websocket: "/ws/{6-character-code}" },
    });
  },
} satisfies ExportedHandler<Env>;

export class ChessRoom extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.ctx.setWebSocketAutoResponse(new WebSocketRequestResponsePair("ping", "pong"));
  }

  private async state(): Promise<RoomState> {
    return (await this.ctx.storage.get<RoomState>("state")) ?? { ...INITIAL_STATE };
  }

  private async saveState(state: RoomState): Promise<void> {
    await this.ctx.storage.put("state", state);
  }

  private session(ws: WebSocket): Session | null {
    return ws.deserializeAttachment() as Session | null;
  }

  private send(ws: WebSocket, message: Record<string, unknown>): void {
    try { ws.send(JSON.stringify(message)); } catch { /* disconnected */ }
  }

  async fetch(request: Request): Promise<Response> {
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return new Response("WebSocket upgrade required", { status: 426 });
    }

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);
    this.ctx.acceptWebSocket(server);

    const id = crypto.randomUUID();
    const sessions = this.ctx.getWebSockets();
    const playerCount = sessions.filter((ws) => {
      const s = this.session(ws);
      return s?.side === "w" || s?.side === "b";
    }).length;
    const side: Side = playerCount === 0 ? "w" : playerCount === 1 ? "b" : "s";
    server.serializeAttachment({ id, name: "Player", side } satisfies Session);

    this.send(server, {
      t: "connected",
      room: new URL(request.url).pathname.split("/").pop() ?? "",
      from: "server",
      p: { side, role: side === "s" ? "spectator" : "player" },
    });

    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
    const raw = typeof message === "string" ? message : new TextDecoder().decode(message);
    let parsed: RoomMessage;
    try { parsed = JSON.parse(raw) as RoomMessage; } catch { return; }

    const attachment = this.session(ws);
    if (!attachment) return;
    const payload = parsed.p ?? {};
    const requestedName = typeof parsed.name === "string" ? parsed.name.trim() : "";
    const payloadName = typeof payload.username === "string" ? payload.username.trim() : "";
    const name = (requestedName || payloadName || attachment.name || "Player").slice(0, 32);
    const room = this.ctx.id.name;

    if (parsed.t === "roomCreate" || parsed.t === "roomJoin" || parsed.t === "hello") {
      if (attachment.side === "s") {
        const state = await this.state();
        this.send(ws, {
          t: "roomJoined", room, from: "server",
          p: {
            side: "s",
            role: "spectator",
            player: { id: attachment.id, name, side: "s" },
            ply: state.ply,
            turn: state.turn,
            result: state.result,
            fen: state.fen,
          },
        });
        return;
      }
      const session = { ...attachment, name };
      ws.serializeAttachment(session);
      const opponent = this.ctx.getWebSockets()
        .filter((peer) => peer !== ws)
        .map((peer) => this.session(peer))
        .find((peer) => peer?.side === "w" || peer?.side === "b") ?? null;
      const state = await this.state();
      this.send(ws, {
        t: "roomJoined", room, from: "server",
        p: {
          side: session.side,
          player: { id: session.id, name: session.name, side: session.side },
          opponent: opponent ? { id: opponent.id, name: opponent.name, side: opponent.side } : null,
          ply: state.ply, turn: state.turn, result: state.result, fen: state.fen,
          serverTime: Date.now(),
        },
      });
      const joined = {
        t: "playerJoined", room, from: session.id,
        name: session.name,
        p: { player: { id: session.id, name: session.name, side: session.side } },
      };
      for (const peer of this.ctx.getWebSockets()) if (peer !== ws) this.send(peer, joined);
      return;
    }

    if (attachment.side === "s") {
      if (parsed.t === "chat" || parsed.t === "reaction" || parsed.t === "ping") {
        for (const peer of this.ctx.getWebSockets()) if (peer !== ws) this.send(peer, { ...parsed, room, from: attachment.id, name: attachment.name });
      }
      return;
    }

    if (parsed.t === "move") {
      const state = await this.state();
      const uci = typeof payload.uci === "string" ? payload.uci : "";
      const ply = typeof payload.ply === "number" ? payload.ply : -1;
      if (state.result || attachment.side !== state.turn || ply !== state.ply || !/^[a-h][1-8][a-h][1-8][qrbn]?$/.test(uci)) {
        this.send(ws, {
          t: "error",
          room,
          from: "server",
          p: {
            error: "Move rejected: invalid turn, sequence, or move format.",
            ply: state.ply,
            fen: state.fen,
            result: state.result,
          },
        });
        return;
      }
      const chess = new Chess(state.fen);
      try {
        const from = uci.slice(0, 2);
        const to = uci.slice(2, 4);
        const promotion = uci.length === 5 ? (uci[4] as "q" | "r" | "b" | "n") : undefined;
        chess.move({ from, to, ...(promotion ? { promotion } : {}) });
      } catch {
        this.send(ws, { t: "error", room, from: "server", p: { error: "Illegal chess move rejected by server." } });
        return;
      }

      state.ply += 1;
      state.turn = state.turn === "w" ? "b" : "w";
      state.fen = chess.fen();
      if (chess.isGameOver()) {
        state.result = chess.isCheckmate()
          ? (state.turn === "w" ? "blackWin" : "whiteWin")
          : "draw";
      }
      await this.saveState(state);
      const outgoing = {
        ...parsed,
        room,
        from: attachment.id,
        name: attachment.name,
        p: {
          ...payload,
          ply: state.ply,
          fen: state.fen,
          turn: state.turn,
          result: state.result,
          serverTime: Date.now(),
        },
      };
      for (const peer of this.ctx.getWebSockets()) if (peer !== ws) this.send(peer, outgoing);
      const snapshot = {
        t: "roomSnapshot",
        room,
        from: "server",
        p: {
          ply: state.ply,
          turn: state.turn,
          result: state.result,
          fen: state.fen,
          serverTime: Date.now(),
        },
      };
      for (const peer of this.ctx.getWebSockets()) this.send(peer, snapshot);
      return;
    }

    if (parsed.t === "resign") {
      const state = await this.state();
      if (!state.result) {
        state.result = attachment.side === "w" ? "blackWin" : "whiteWin";
        await this.saveState(state);
      }
    } else if (parsed.t === "drawAccept") {
      const state = await this.state();
      if (!state.result) { state.result = "draw"; await this.saveState(state); }
    } else if (parsed.t === "rematchAccept") {
      await this.saveState({ ...INITIAL_STATE });
    } else if (parsed.t === "chat") {
      const msg = typeof payload.message === "string" ? payload.message.trim().slice(0, 500) : "";
      if (!msg) return;
      parsed = { ...parsed, p: { ...payload, message: msg } };
    } else if (parsed.t === "reaction") {
      const emoji = typeof payload.emoji === "string" ? payload.emoji : "";
      if (!["👍","👏","😂","😮","😢","🔥","❤️"].includes(emoji)) return;
    }

    const outgoing = { ...parsed, room, from: attachment.id, name: attachment.name };
    for (const peer of this.ctx.getWebSockets()) if (peer !== ws) this.send(peer, outgoing);
  }

  async webSocketClose(ws: WebSocket): Promise<void> {
    const session = this.session(ws);
    if (session) {
      const message = { t: "playerLeft", room: this.ctx.id.name, from: session.id, name: session.name, p: { side: session.side } };
      for (const peer of this.ctx.getWebSockets()) if (peer !== ws) this.send(peer, message);
    }
    try { ws.close(1000, "closed"); } catch { /* already closed */ }
  }
}
