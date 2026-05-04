import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { WebSocketManager, type ConnectionState } from "@/lib/websocket";

function createMockWebSocket(url: string) {
  const ws = {
    url,
    readyState: 0,
    onopen: null as (() => void) | null,
    onclose: null as (() => void) | null,
    onerror: null as ((event: { error: unknown }) => void) | null,
    onmessage: null as ((event: { data: string }) => void) | null,
    send: vi.fn(),
    close: vi.fn(() => {
      ws.readyState = 3;
      ws.onclose?.();
    }),
    simulateMessage: (data: unknown) => {
      ws.onmessage?.({ data: JSON.stringify(data) });
    },
  };

  setTimeout(() => {
    ws.readyState = 1;
    ws.onopen?.();
  }, 0);

  return ws;
}

const MockWebSocketStatic = {
  CONNECTING: 0,
  OPEN: 1,
  CLOSING: 2,
  CLOSED: 3,
};

describe("WebSocketManager", () => {
  let manager: WebSocketManager;
  let capturedWs: ReturnType<typeof createMockWebSocket>;

  beforeEach(() => {
    vi.useFakeTimers();
    capturedWs = undefined as unknown as ReturnType<typeof createMockWebSocket>;

    const create = createMockWebSocket;
    globalThis.WebSocket = function (url: string) {
      capturedWs = create(url);
      Object.assign(capturedWs, MockWebSocketStatic);
      return capturedWs;
    } as unknown as typeof WebSocket;

    Object.assign(globalThis.WebSocket, MockWebSocketStatic);
  });

  afterEach(() => {
    manager?.disconnect();
    vi.useRealTimers();
  });

  it("transitions to connected after open", () => {
    manager = new WebSocketManager("/ws/test");
    const states: ConnectionState[] = [];
    manager.onConnectionChange((s) => states.push(s));

    manager.connect();
    vi.runAllTimers();

    expect(states).toContain("connecting");
    expect(states).toContain("connected");
  });

  it("notifies listeners on disconnect", () => {
    manager = new WebSocketManager("/ws/test");
    const states: ConnectionState[] = [];
    manager.onConnectionChange((s) => states.push(s));

    manager.connect();
    vi.runAllTimers();
    capturedWs.close();
    vi.runAllTimers();

    expect(states).toContain("disconnected");
  });

  it("attempts reconnect with exponential backoff after disconnect", () => {
    manager = new WebSocketManager("/ws/test");
    const states: ConnectionState[] = [];
    manager.onConnectionChange((s) => states.push(s));

    manager.connect();
    vi.runAllTimers();

    capturedWs.close();
    states.length = 0;

    vi.advanceTimersByTime(2000);

    expect(states).toContain("connecting");
  });

  it("does not reconnect after explicit disconnect", () => {
    manager = new WebSocketManager("/ws/test");
    manager.connect();
    vi.runAllTimers();

    const closeSpy = capturedWs.close;
    manager.disconnect();

    vi.advanceTimersByTime(60000);

    expect(closeSpy).toHaveBeenCalled();
  });

  it("delivers parsed JSON messages to wildcard listeners", () => {
    manager = new WebSocketManager("/ws/test");
    manager.connect();
    vi.runAllTimers();

    const received: unknown[] = [];
    manager.onMessage("*", (data) => received.push(data));

    capturedWs.simulateMessage({ type: "price", value: 42 });

    expect(received).toHaveLength(1);
    expect(received[0]).toEqual({ type: "price", value: 42 });
  });

  it("unsubscribes connection listener", () => {
    manager = new WebSocketManager("/ws/test");
    const states: ConnectionState[] = [];
    const unsub = manager.onConnectionChange((s) => states.push(s));

    unsub();
    manager.connect();
    vi.runAllTimers();

    expect(states).not.toContain("connected");
  });

  it("unsubscribes message listener", () => {
    manager = new WebSocketManager("/ws/test");
    manager.connect();
    vi.runAllTimers();

    const received: unknown[] = [];
    const unsub = manager.onMessage("*", (data) => received.push(data));
    unsub();

    capturedWs.simulateMessage({ type: "price", value: 42 });
    expect(received).toHaveLength(0);
  });

  it("returns disconnected state when socket is null", () => {
    manager = new WebSocketManager("/ws/test");
    expect(manager.getState()).toBe("disconnected");
  });
});
