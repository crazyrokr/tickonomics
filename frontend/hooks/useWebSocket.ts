"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import { WebSocketManager, type ConnectionState } from "@/lib/websocket";

export function useWebSocket(path: string, token?: string) {
  const managerRef = useRef<WebSocketManager | null>(null);
  const [connectionState, setConnectionState] =
    useState<ConnectionState>("disconnected");
  const [lastMessage, setLastMessage] = useState<unknown>(null);

  useEffect(() => {
    const manager = new WebSocketManager(path);
    managerRef.current = manager;

    const unsubConnection = manager.onConnectionChange(setConnectionState);
    const unsubMessage = manager.onMessage("*", setLastMessage);

    manager.connect(token);

    return () => {
      unsubConnection();
      unsubMessage();
      manager.disconnect();
      managerRef.current = null;
    };
  }, [path, token]);

  const send = useCallback((data: unknown) => {
    if (managerRef.current?.getState() === "connected") {
      const socket = (managerRef.current as unknown as { socket: WebSocket })
        .socket;
      socket?.send(JSON.stringify(data));
    }
  }, []);

  return { connectionState, lastMessage, send };
}
