import { useRef, useState, useCallback, useEffect } from 'react';
import { StationDiagramEngine } from '../engine/StationDiagramEngine';
import { useWebSocket } from './useWebSocket';

const INFO_UPDATE_INTERVAL = 300;

function getWsUrl() {
  const loc = window.location;
  const proto = loc.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${proto}//${loc.host}/ws`;
}

function transformState(raw) {
  if (!raw) return null;

  const trackCircuits = {};
  if (raw.trackCircuits) {
    const entries = Object.entries(raw.trackCircuits);
    for (let i = 0; i < entries.length; i++) {
      const [id, val] = entries[i];
      trackCircuits[id] = {
        occupied: typeof val === 'object' ? val.occupied : Boolean(val),
        locked: typeof val === 'object' ? val.locked : false,
      };
    }
  }

  const signals = {};
  if (raw.signals) {
    const entries = Object.entries(raw.signals);
    for (let i = 0; i < entries.length; i++) {
      const [id, val] = entries[i];
      signals[id] = {
        aspect: typeof val === 'object' ? val.aspect : val,
        locked: typeof val === 'object' ? val.locked : false,
      };
    }
  }

  const switches = {};
  if (raw.switches) {
    const entries = Object.entries(raw.switches);
    for (let i = 0; i < entries.length; i++) {
      const [id, val] = entries[i];
      switches[id] = {
        position: typeof val === 'object'
          ? val.position
          : (val === 1 ? 'REVERSE' : 'NORMAL'),
        locked: typeof val === 'object' ? val.locked : false,
      };
    }
  }

  const routes = {};
  if (raw.routes) {
    const entries = Object.entries(raw.routes);
    for (let i = 0; i < entries.length; i++) {
      const [id, val] = entries[i];
      routes[id] = {
        active: typeof val === 'object' ? val.active : Boolean(val),
      };
    }
  }

  return {
    trackCircuits,
    signals,
    switches,
    routes,
    timestamp: raw.timestamp || Date.now(),
  };
}

export function useCtcEngine(svgContainerRef, stationXml) {
  const engineRef = useRef(null);
  const wsUrl = getWsUrl();
  const { lastMessage, connectionStatus, sendMessage } = useWebSocket(wsUrl);

  const rawStateRef = useRef(null);

  const [infoState, setInfoState] = useState(null);
  const lastInfoUpdateTime = useRef(0);

  useEffect(() => {
    if (!svgContainerRef.current) return;
    const engine = new StationDiagramEngine(svgContainerRef.current);
    engine.init();
    engine.parseStationXML(stationXml);
    engineRef.current = engine;
    return () => {
      engine.destroy();
      engineRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!lastMessage) return;

    if (lastMessage.type === 'interlocking_state' || lastMessage.type === 'state_update') {
      const raw = lastMessage.data || lastMessage;
      const transformed = transformState(raw);
      rawStateRef.current = transformed;

      if (engineRef.current) {
        engineRef.current.updateAll(transformed);
      }

      const now = Date.now();
      if (now - lastInfoUpdateTime.current >= INFO_UPDATE_INTERVAL) {
        lastInfoUpdateTime.current = now;
        setInfoState(transformed);
      }
    }
  }, [lastMessage]);

  useEffect(() => {
    if (!rawStateRef.current) return;
    const timer = setInterval(() => {
      if (rawStateRef.current) {
        const now = Date.now();
        if (now - lastInfoUpdateTime.current >= INFO_UPDATE_INTERVAL) {
          lastInfoUpdateTime.current = now;
          setInfoState(rawStateRef.current);
        }
      }
    }, INFO_UPDATE_INTERVAL);
    return () => clearInterval(timer);
  }, []);

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

  return { infoState, connectionStatus, requestRoute, cancelRoute };
}
