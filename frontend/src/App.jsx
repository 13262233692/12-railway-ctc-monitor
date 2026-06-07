import { useEffect, useRef, useState } from 'react';
import { StationDiagramEngine } from './engine/StationDiagramEngine';
import { useCtcState } from './hooks/useCtcState';
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

function App() {
  const svgContainerRef = useRef(null);
  const engineRef = useRef(null);

  const { state, connectionStatus, requestRoute, cancelRoute } = useCtcState();

  const [currentTime, setCurrentTime] = useState(new Date());

  useEffect(() => {
    const timer = setInterval(() => setCurrentTime(new Date()), 1000);
    return () => clearInterval(timer);
  }, []);

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
    if (state && engineRef.current) {
      engineRef.current.updateAll(state);
    }
  }, [state]);

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

  const timeStr = currentTime.toLocaleTimeString('zh-CN', { hour12: false });
  const dateStr = currentTime.toLocaleDateString('zh-CN');

  const signals = state?.signals || {};
  const trackCircuits = state?.trackCircuits || {};
  const switches = state?.switches || {};
  const routes = state?.routes || {};
  const tickCount = state?.timestamp ?? 0;

  return (
    <div className="ctc-app">
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
          <span className="header-time">{dateStr} {timeStr}</span>
        </div>
      </header>

      <main className="ctc-main">
        <div className="diagram-panel">
          <div className="panel-header">
            <span className="panel-title">站场表示</span>
            <span className="panel-badge">实时</span>
          </div>
          <svg ref={svgContainerRef} className="station-svg" />
        </div>

        <aside className="info-panel">
          <div className="info-scrollable">
            <div className="info-section">
              <h3 className="info-section-title">信号状态</h3>
              <div className="signal-list">
                {Object.keys(signals).length === 0 && (
                  <div className="info-empty">等待数据...</div>
                )}
                {Object.entries(signals).map(([id, sig]) => (
                  <div className="signal-item" key={id}>
                    <span className={`signal-dot ${SIGNAL_ASPECT_CLASS[sig.aspect] || 'signal-red'}`} />
                    <span className="signal-label">{id}信号机</span>
                    <span className="signal-status">{SIGNAL_ASPECT_LABEL[sig.aspect] || sig.aspect}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="info-section">
              <h3 className="info-section-title">轨道电路</h3>
              <div className="track-list">
                {Object.keys(trackCircuits).length === 0 && (
                  <div className="info-empty">等待数据...</div>
                )}
                {Object.entries(trackCircuits).map(([id, tc]) => (
                  <div className={`track-status-item ${tc.occupied ? 'track-occupied' : 'track-clear'}`} key={id}>
                    <span className="track-id">{id}</span>
                    <span className="track-state">{tc.occupied ? '占用' : '空闲'}</span>
                  </div>
                ))}
              </div>
            </div>

            <div className="info-section">
              <h3 className="info-section-title">进路控制</h3>
              <div className="route-list">
                {ROUTE_IDS.map((routeId) => {
                  const route = routes[routeId];
                  const isActive = route?.active;
                  return (
                    <div className="route-control-item" key={routeId}>
                      <span className="route-label">{ROUTE_LABELS[routeId] || routeId}</span>
                      <button
                        className={`route-btn ${isActive ? 'route-btn-cancel' : 'route-btn-request'}`}
                        onClick={() => isActive ? cancelRoute(routeId) : requestRoute(routeId)}
                        disabled={connectionStatus !== 'connected'}
                      >
                        {isActive ? '取消' : '排路'}
                      </button>
                    </div>
                  );
                })}
              </div>
            </div>

            <div className="info-section">
              <h3 className="info-section-title">道岔状态</h3>
              <div className="switch-list">
                {Object.keys(switches).length === 0 && (
                  <div className="info-empty">等待数据...</div>
                )}
                {Object.entries(switches).map(([id, sw]) => (
                  <div className="switch-status-item" key={id}>
                    <span className="switch-id">{id}</span>
                    <span className={`switch-position ${sw.position === 'REVERSE' ? 'switch-reverse' : 'switch-normal'}`}>
                      {sw.position === 'REVERSE' ? '反位' : '定位'}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </aside>
      </main>

      <footer className="ctc-footer">
        <span>CTC调度集中监控系统 v1.0</span>
        <span>Tick: {tickCount}</span>
        <span>连接: {statusText}</span>
      </footer>
    </div>
  );
}

export default App;
