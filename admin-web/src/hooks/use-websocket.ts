'use client';

import { useEffect, useRef, useState } from 'react';
import { wsService } from '@/lib/websocket';
import { authService } from '@/lib/auth';

/**
 * useWebSocket hook — connects to the backend WebSocket and subscribes to topics.
 *
 * Per AGENTS.md rule 5: real-time updates for command results and device events.
 */
export function useWebSocket(topic: string, onMessage?: (data: unknown) => void) {
  const [connected, setConnected] = useState(false);
  const callbackRef = useRef(onMessage);
  callbackRef.current = onMessage;

  useEffect(() => {
    const token = authService.isAuthenticated()
      ? localStorage.getItem('accessToken') || ''
      : '';

    if (!token) return;

    wsService.connect(token, () => {
      setConnected(true);
      if (topic) {
        wsService.subscribe(topic, (data) => {
          callbackRef.current?.(data);
        });
      }
    });

    return () => {
      if (topic) wsService.unsubscribe(topic);
      setConnected(false);
    };
  }, [topic]);

  return { connected };
}
