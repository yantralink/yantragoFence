import { Client, StompSubscription } from '@stomp/stompjs';

const WS_URL = process.env.NEXT_PUBLIC_WS_URL || 'ws://localhost:8080/ws';

/**
 * STOMP WebSocket client for real-time updates.
 *
 * Subscribes to topics:
 * - /topic/locations — device location updates
 * - /topic/telemetry — telemetry readings
 * - /topic/commands — command result updates
 * - /topic/devices — device event updates
 */
class WebSocketService {
  private client: Client | null = null;
  private subscriptions: Map<string, StompSubscription> = new Map();

  connect(token: string, onConnect?: () => void): void {
    if (this.client?.active) return;

    this.client = new Client({
      brokerURL: WS_URL,
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      onConnect: () => {
        onConnect?.();
      },
      onStompError: (frame) => {
        console.error('[WS] STOMP error:', frame.headers['message']);
      },
      onWebSocketError: (event) => {
        console.error('[WS] WebSocket error:', event);
      },
    });

    this.client.activate();
  }

  subscribe(destination: string, callback: (message: unknown) => void): void {
    if (!this.client?.active) return;
    const sub = this.client.subscribe(destination, (message) => {
      try {
        const data = JSON.parse(message.body);
        callback(data);
      } catch {
        callback(message.body);
      }
    });
    this.subscriptions.set(destination, sub);
  }

  unsubscribe(destination: string): void {
    this.subscriptions.get(destination)?.unsubscribe();
    this.subscriptions.delete(destination);
  }

  disconnect(): void {
    this.subscriptions.forEach((sub) => sub.unsubscribe());
    this.subscriptions.clear();
    this.client?.deactivate();
  }

  get isConnected(): boolean {
    return this.client?.active === true;
  }
}

export const wsService = new WebSocketService();
