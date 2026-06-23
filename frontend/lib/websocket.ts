export type ConnectionState = "connecting" | "connected" | "disconnected";

export type ConnectionListener = (state: ConnectionState) => void;
export type MessageListener = (data: unknown) => void;

const WS_BASE_URL =
  process.env.NEXT_PUBLIC_WS_BASE_URL ?? "ws://localhost:8080";

const BACKOFF_BASE_MS = 1000;
const BACKOFF_MAX_MS = 30000;
const BACKOFF_FACTOR = 2;

export class WebSocketManager {
  private socket: WebSocket | null = null;
  private listeners: Set<ConnectionListener> = new Set();
  private messageListeners: Map<string, Set<MessageListener>> = new Map();
  private reconnectAttempts = 0;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private disposed = false;

  constructor(private readonly path: string) {}

  connect(token?: string): void {
    if (this.disposed) return;
    this.notifyListeners("connecting");

    const url = token
      ? `${WS_BASE_URL}${this.path}?token=${encodeURIComponent(token)}`
      : `${WS_BASE_URL}${this.path}`;

    this.socket = new WebSocket(url);

    this.socket.onopen = () => {
      this.reconnectAttempts = 0;
      this.notifyListeners("connected");
    };

    this.socket.onclose = () => {
      this.notifyListeners("disconnected");
      this.scheduleReconnect(token);
    };

    this.socket.onerror = () => {
      this.socket?.close();
    };

    this.socket.onmessage = (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data as string);
        const listeners = this.messageListeners.get("*");
        if (listeners) {
          listeners.forEach((fn) => fn(data));
        }
      } catch {
        // ignore non-JSON messages
      }
    };
  }

  disconnect(): void {
    this.disposed = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.socket?.close();
    this.socket = null;
  }

  /** Sends a JSON-serialized payload when the socket is open. No-ops otherwise. */
  send(data: unknown): void {
    if (this.socket && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(data));
    }
  }

  onConnectionChange(listener: ConnectionListener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  onMessage(topic: string, listener: MessageListener): () => void {
    const set = this.messageListeners.get(topic) ?? new Set();
    set.add(listener);
    this.messageListeners.set(topic, set);
    return () => {
      set.delete(listener);
      if (set.size === 0) {
        this.messageListeners.delete(topic);
      }
    };
  }

  getState(): ConnectionState {
    if (!this.socket) return "disconnected";
    switch (this.socket.readyState) {
      case WebSocket.CONNECTING:
        return "connecting";
      case WebSocket.OPEN:
        return "connected";
      default:
        return "disconnected";
    }
  }

  private scheduleReconnect(token?: string): void {
    if (this.disposed) return;
    const delay = Math.min(
      BACKOFF_BASE_MS * Math.pow(BACKOFF_FACTOR, this.reconnectAttempts),
      BACKOFF_MAX_MS,
    );
    this.reconnectAttempts++;
    this.reconnectTimer = setTimeout(() => this.connect(token), delay);
  }

  private notifyListeners(state: ConnectionState): void {
    this.listeners.forEach((fn) => fn(state));
  }
}
