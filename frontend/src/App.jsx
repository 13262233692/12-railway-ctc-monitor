import React, { memo, useRef, useCallback, useState, useEffect } from 'react';
import { useCtcEngine } from './hooks/useCtcEngine';
import stationXml from './assets/station.xml?raw';
import './App.css';

const ROUTE_IDS = ['Route1', 'Route2', 'Route3', 'Route4', 'Route5', 'Route6'];

const ROUTE_LABELS = {
  Route1: 'X→1G 正线接车',
  Route2: 'X→3G 侧线接车',
  Route3: '1G→S 正线发车',
  Route4: '3G→S3 侧线发车',
  Route5: 'X1→IAG 接近',
  Route6: 'X2→IIG 接近',
};

const SIGNAL_ASPECT_LABEL = {
  RED: '禁止',
  YELLOW: '注意',
  GREEN: '开放',
};

const SIGNAL_ASPECT_CLASS = {
  RED: 'signal-red',
  YELLOW: 'signal-yellow',
  GREEN: 'signal-green',
};

const Clock = memo(function Clock() {
  const [time, setTime] = useState(new Date());
  useEffect(() => {
    const timer = setInterval(() => setTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);
  const timeStr = time.toLocaleTimeString('zh-CN', { hour12: false });
  const dateStr = time.toLocaleDateString('zh-CN');
  return <span className="header-time">{dateStr} {timeStr}</span>;
});

const HeaderBar = memo(function HeaderBar({ connectionStatus }) {
  const statusClass =
    connectionStatus === 'connected'
      ? 'status-connected'
      : connectionStatus === 'connecting'
        ? 'status-connecting'
        : 'status-disconnected';

  const statusText =
    connectionStatus === 'connected'
      ? '已连接'
      : connectionStatus === 'connecting'
        ? '连接中...'
        : '已断开';

  return (
    <header className="ctc-header">
      <div className="header-left">
        <span className="header-logo">🚄</span>
        <h1 className="header-title">高铁CTC调度集中控制系统</h1>
      </div>
      <div className="header-center">
        <span className={`status-indicator ${statusClass}`} />
        <span className={`status-text ${statusClass}`}>{statusText}</span>
      </div>
      <div className="header-right">
        <Clock />
      </div>
    </header>
  );
});

const DiagramPanel = memo(function DiagramPanel({ svgRefCallback }) {
  return (
    <div className="diagram-panel">
      <div className="panel-header">
        <span className="panel-title">站场表示</span>
        <span className="panel-badge">实时</span>
      </div>
      <svg ref={svgRefCallback} className="station-svg" />
    </div>
  );
});

const SignalItem = memo(function SignalItem({ id, aspect }) {
  return (
    <div className="signal-item">
      <span className={`signal-dot ${SIGNAL_ASPECT_CLASS[aspect] || 'signal-red'}`} />
      <span className="signal-label">{id}信号机</span>
      <span className="signal-status">{SIGNAL_ASPECT_LABEL[aspect] || aspect}</span>
    </div>
  );
});

const TrackItem = memo(function TrackItem({ id, occupied }) {
  return (
    <div className={`track-status-item ${occupied ? 'track-occupied' : 'track-clear'}`}>
      <span className="track-id">{id}</span>
      <span className="track-state">{occupied ? '占用' : '空闲'}</span>
    </div>
  );
});

const SwitchItem = memo(function SwitchItem({ id, position }) {
  return (
    <div className="switch-status-item">
      <span className="switch-id">{id}</span>
      <span className={`switch-position ${position === 'REVERSE' ? 'switch-reverse' : 'switch-normal'}`}>
        {position === 'REVERSE' ? '反位' : '定位'}
      </span>
    </div>
  );
});

const RouteControlItem = memo(function RouteControlItem({ label, isActive, onAction, disabled }) {
  return (
    <div className="route-control-item">
      <span className="route-label">{label}</span>
      <button
        className={`route-btn ${isActive ? 'route-btn-cancel' : 'route-btn-request'}`}
        onClick={onAction}
        disabled={disabled}
      >
        {isActive ? '取消' : '排路'}
      </button>
    </div>
  );
});

const InfoPanel = memo(function InfoPanel({
  signals,
  trackCircuits,
  switches,
  routes,
  connectionStatus,
  requestRoute,
  cancelRoute,
}) {
  const signalEntries = signals ? Object.entries(signals) : [];
  const trackEntries = trackCircuits ? Object.entries(trackCircuits) : [];
  const switchEntries = switches ? Object.entries(switches) : [];
  const isConnected = connectionStatus === 'connected';

  return (
    <aside className="info-panel">
      <div className="info-scrollable">
        <div className="info-section">
          <h3 className="info-section-title">信号状态</h3>
          <div className="signal-list">
            {signalEntries.length === 0 && (
              <div className="info-empty">等待数据...</div>
            )}
            {signalEntries.map(([id, sig]) => (
              <SignalItem key={id} id={id} aspect={sig.aspect} />
            ))}
          </div>
        </div>

        <div className="info-section">
          <h3 className="info-section-title">轨道电路</h3>
          <div className="track-list">
            {trackEntries.length === 0 && (
              <div className="info-empty">等待数据...</div>
            )}
            {trackEntries.map(([id, tc]) => (
              <TrackItem key={id} id={id} occupied={tc.occupied} />
            ))}
          </div>
        </div>

        <div className="info-section">
          <h3 className="info-section-title">进路控制</h3>
          <div className="route-list">
            {ROUTE_IDS.map((routeId) => (
              <RouteControlItem
                key={routeId}
                label={ROUTE_LABELS[routeId] || routeId}
                isActive={routes?.[routeId]?.active}
                onAction={() =>
                  routes?.[routeId]?.active
                    ? cancelRoute(routeId)
                    : requestRoute(routeId)
                }
                disabled={!isConnected}
              />
            ))}
          </div>
        </div>

        <div className="info-section">
          <h3 className="info-section-title">道岔状态</h3>
          <div className="switch-list">
            {switchEntries.length === 0 && (
              <div className="info-empty">等待数据...</div>
            )}
            {switchEntries.map(([id, sw]) => (
              <SwitchItem key={id} id={id} position={sw.position} />
            ))}
          </div>
        </div>
      </div>
    </aside>
  );
});

const FooterBar = memo(function FooterBar({ tickCount, connectionStatus }) {
  const statusText =
    connectionStatus === 'connected'
      ? '已连接'
      : connectionStatus === 'connecting'
        ? '连接中...'
        : '已断开';

  return (
    <footer className="ctc-footer">
      <span>CTC调度集中监控系统 v1.0</span>
      <span>Tick: {tickCount}</span>
      <span>连接: {statusText}</span>
    </footer>
  );
});

function App() {
  const svgContainerRef = useRef(null);
  const svgRefCallback = useCallback((node) => {
    svgContainerRef.current = node;
  }, []);

  const { infoState, connectionStatus, requestRoute, cancelRoute } = useCtcEngine(
    svgContainerRef,
    stationXml
  );

  const signals = infoState?.signals || {};
  const trackCircuits = infoState?.trackCircuits || {};
  const switches = infoState?.switches || {};
  const routes = infoState?.routes || {};
  const tickCount = infoState?.timestamp ?? 0;

  return (
    <div className="ctc-app">
      <HeaderBar connectionStatus={connectionStatus} />
      <main className="ctc-main">
        <DiagramPanel svgRefCallback={svgRefCallback} />
        <InfoPanel
          signals={signals}
          trackCircuits={trackCircuits}
          switches={switches}
          routes={routes}
          connectionStatus={connectionStatus}
          requestRoute={requestRoute}
          cancelRoute={cancelRoute}
        />
      </main>
      <FooterBar tickCount={tickCount} connectionStatus={connectionStatus} />
    </div>
  );
}

export default App;
