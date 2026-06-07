import { useState, useCallback, useEffect } from 'react';
import { useWebSocket } from './useWebSocket';

function getWsUrl() {
  const loc = window.location;
  const proto = loc.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${proto}//${loc.host}/ws`;
}

function transformState(raw) {
  if (!raw) return null;

  const trackCircuits = {};
  if (raw.trackCircuits) {
    Object.entries(raw.trackCircuits).forEach(([id, val]) => {
      trackCircuits[id] = {
        occupied: typeof val === 'object' ? val.occupied : Boolean(val),
        locked: typeof val === 'object' ? val.locked : false,
      };
    });
  }

  const signals = {};
  if (raw.signals) {
    Object.entries(raw.signals).forEach(([id, val]) => {
      signals[id] = {
        aspect: typeof val === 'object' ? val.aspect : val,
        locked: typeof val === 'object' ? val.locked : false,
      };
    });
  }

  const switches = {};
  if (raw.switches) {
    Object.entries(raw.switches).forEach(([id, val]) => {
      switches[id] = {
        position: typeof val === 'object'
          ? val.position
          : (val === 1 ? 'REVERSE' : 'NORMAL'),
        locked: typeof val === 'object' ? val.locked : false,
      };
    });
  }

  const routes = {};
  if (raw.routes) {
    Object.entries(raw.routes).forEach(([id, val]) => {
      routes[id] = {
        active: typeof val === 'object' ? val.active : Boolean(val),
      };
    });
  }

  return {
    trackCircuits,
    signals,
    switches,
    routes,
    timestamp: raw.timestamp || Date.now(),
  };
}

export function useCtcState() {
  const wsUrl = getWsUrl();
  const { lastMessage, connectionStatus, sendMessage } = useWebSocket(wsUrl);
  const [state, setState] = useState(null);

  useEffect(() => {
    if (!lastMessage) return;

    if (lastMessage.type === 'interlocking_state' || lastMessage.type === 'state_update') {
      const raw = lastMessage.data || lastMessage;
      setState(transformState(raw));
    }
  }, [lastMessage]);

  const requestRoute = useCallback((routeId) => {
    sendMessage({
      type: 'request_route',
      route_id: routeId,
      timestamp: Date.now(),
    });
  }, [sendMessage]);

  const cancelRoute = useCallback((routeId) => {
    sendMessage({
      type: 'cancel_route',
      route_id: routeId,
      timestamp: Date.now(),
    });
  }, [sendMessage]);

  return { state, connectionStatus, requestRoute, cancelRoute };
}
