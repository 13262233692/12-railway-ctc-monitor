import * as d3 from 'd3';
import { StationXMLParser } from './StationXMLParser';

function lerpPath(a, b) {
  const parsePoints = (d) => {
    const nums = d.match(/-?\d+\.?\d*/g);
    return nums ? nums.map(Number) : [];
  };
  const pa = parsePoints(a);
  const pb = parsePoints(b);
  return (t) => {
    const result = pa.map((v, i) => v + (pb[i] - v) * t);
    const nums = b.match(/-?\d+\.?\d*/g);
    if (!nums) return b;
    let idx = 0;
    return b.replace(/-?\d+\.?\d*/g, () => {
      const v = result[idx++];
      return Math.round(v * 100) / 100;
    });
  };
}

const TRANSITION_DURATION = 200;
const CLEAR_COLOR = '#4488ff';
const OCCUPIED_COLOR = '#ff3333';
const CLEAR_WIDTH = 3;
const OCCUPIED_WIDTH = 5;
const SIGNAL_COLORS = {
  RED: '#ff3333',
  YELLOW: '#ffcc00',
  GREEN: '#00cc66',
};
const SIGNAL_FILTERS = {
  RED: 'glow-red',
  YELLOW: 'glow-yellow',
  GREEN: 'glow-green',
};

export class StationDiagramEngine {
  constructor(container) {
    this.container = container;
    this.svg = null;
    this.g = null;
    this.zoomBehavior = null;
    this.trackCircuits = new Map();
    this.signals = new Map();
    this.switches = new Map();
    this.routes = new Map();
    this.xmlParser = new StationXMLParser();

    this._prevTrackState = new Map();
    this._prevSignalState = new Map();
    this._prevSwitchState = new Map();

    this._pendingMutations = [];
    this._rafId = null;

    this._currentTransform = d3.zoomIdentity;
    this._cullingTimer = null;

    this._trackGroup = null;
    this._signalGroup = null;
    this._switchGroup = null;
    this._labelGroup = null;
  }

  init() {
    this.svg = d3.select(this.container);
    this.svg.selectAll('*').remove();

    this.svg.attr('class', 'station-svg');

    const defs = this.svg.append('defs');

    const styleEl = defs.append('style').text(
      `.tc-clear { stroke: ${CLEAR_COLOR}; stroke-width: ${CLEAR_WIDTH}; }
       .tc-occupied { stroke: ${OCCUPIED_COLOR}; stroke-width: ${OCCUPIED_WIDTH}; }`
    );

    this._createGlowFilter(defs, 'glow-red', '#ff3333');
    this._createGlowFilter(defs, 'glow-yellow', '#ffcc00');
    this._createGlowFilter(defs, 'glow-green', '#00cc66');
    this._createGlowFilter(defs, 'glow-purple', '#cc33ff');

    this.g = this.svg.append('g').attr('class', 'station-group');

    this._trackGroup = this.g.append('g').attr('class', 'tracks');
    this._switchGroup = this.g.append('g').attr('class', 'switches');
    this._conflictGroup = this.g.append('g').attr('class', 'conflicts');
    this._signalGroup = this.g.append('g').attr('class', 'signals');
    this._labelGroup = this.g.append('g').attr('class', 'labels');

    this.zoomBehavior = d3.zoom()
      .scaleExtent([0.3, 5])
      .on('zoom', (event) => {
        this._currentTransform = event.transform;
        this.g.attr('transform', event.transform);
        this._scheduleViewportCulling();
      });

    this.svg.call(this.zoomBehavior);
  }

  _createGlowFilter(defs, id, color) {
    const filter = defs.append('filter')
      .attr('id', id)
      .attr('x', '-50%')
      .attr('y', '-50%')
      .attr('width', '200%')
      .attr('height', '200%');

    filter.append('feGaussianBlur')
      .attr('stdDeviation', '3')
      .attr('result', 'coloredBlur');

    const feMerge = filter.append('feMerge');
    feMerge.append('feMergeNode').attr('in', 'coloredBlur');
    feMerge.append('feMergeNode').attr('in', 'SourceGraphic');
  }

  parseStationXML(xmlString) {
    const data = this.xmlParser.parse(xmlString);

    this.trackCircuits.clear();
    this.signals.clear();
    this.switches.clear();
    this._prevTrackState.clear();
    this._prevSignalState.clear();
    this._prevSwitchState.clear();

    this._renderTrackCircuits(data.trackCircuits);
    this._renderSwitches(data.switches);
    this._renderSignals(data.signals);

    this._fitView();
  }

  _renderTrackCircuits(circuits) {
    const sel = this._trackGroup.selectAll('path.track-segment')
      .data(
        circuits.flatMap((c) =>
          c.segments.map((seg, i) => ({ ...seg, trackId: c.id, type: c.type, segIdx: i }))
        ),
        (d) => `${d.trackId}-${d.segIdx}`
      );

    sel.join(
      (enter) => enter.append('path')
        .attr('d', (d) => `M${d.x1},${d.y1} L${d.x2},${d.y2}`)
        .attr('class', 'track-segment tc-clear')
        .attr('fill', 'none')
        .attr('stroke-linecap', 'round')
        .attr('data-track-id', (d) => d.trackId)
        .attr('data-type', (d) => d.type),
      (update) => update,
      (exit) => exit.remove()
    );

    circuits.forEach((circuit) => {
      const domPaths = this._trackGroup.selectAll(`path[data-track-id="${circuit.id}"]`);
      this.trackCircuits.set(circuit.id, { ...circuit, domPaths });

      const lastSeg = circuit.segments[circuit.segments.length - 1];
      if (lastSeg) {
        const labelX = (circuit.segments[0].x1 + lastSeg.x2) / 2;
        const labelY = lastSeg.y2 - 15;
        this._labelGroup.append('text')
          .attr('x', labelX)
          .attr('y', labelY)
          .attr('text-anchor', 'middle')
          .attr('fill', '#8899bb')
          .attr('font-size', '11px')
          .attr('font-family', 'monospace')
          .attr('data-label-for', circuit.id)
          .text(circuit.id);
      }
    });
  }

  _renderSignals(signals) {
    const sel = this._signalGroup.selectAll('g.signal-unit')
      .data(signals, (d) => d.id);

    sel.join(
      (enter) => {
        const g = enter.append('g')
          .attr('class', 'signal-unit')
          .attr('data-signal-id', (d) => d.id);

        g.append('line')
          .attr('class', 'signal-post')
          .attr('x1', (d) => d.x)
          .attr('y1', (d) => d.y)
          .attr('x2', (d) => d.x)
          .attr('y2', (d) => d.y - 25 + 7)
          .attr('stroke', '#5a6a8b')
          .attr('stroke-width', 2);

        g.append('circle')
          .attr('class', 'signal-light')
          .attr('cx', (d) => d.x)
          .attr('cy', (d) => d.y - 25)
          .attr('r', 7)
          .attr('fill', SIGNAL_COLORS.RED)
          .style('filter', 'url(#glow-red)');

        return g;
      },
      (update) => update,
      (exit) => exit.remove()
    );

    signals.forEach((sig) => {
      const g = this._signalGroup.select(`g[data-signal-id="${sig.id}"]`);
      const circle = g.select('circle.signal-light');
      this.signals.set(sig.id, { ...sig, circle, circleY: sig.y - 25, radius: 7 });

      this._labelGroup.append('text')
        .attr('x', sig.x)
        .attr('y', sig.y - 25 - 7 - 6)
        .attr('text-anchor', 'middle')
        .attr('fill', '#e0e6f0')
        .attr('font-size', '12px')
        .attr('font-weight', 'bold')
        .attr('font-family', 'monospace')
        .attr('data-label-for', sig.id)
        .text(sig.id);
    });
  }

  _renderSwitches(switches) {
    const sel = this._switchGroup.selectAll('path.switch-frog')
      .data(switches, (d) => d.id);

    sel.join(
      (enter) => enter.append('path')
        .attr('class', 'switch-frog')
        .attr('d', (d) => this._buildSwitchPath(d, 'NORMAL'))
        .attr('stroke', '#6b8ab8')
        .attr('stroke-width', CLEAR_WIDTH)
        .attr('fill', 'none')
        .attr('stroke-linecap', 'round')
        .attr('data-switch-id', (d) => d.id),
      (update) => update,
      (exit) => exit.remove()
    );

    switches.forEach((sw) => {
      const pathEl = this._switchGroup.select(`path[data-switch-id="${sw.id}"]`);
      this.switches.set(sw.id, { ...sw, pathEl });

      this._labelGroup.append('text')
        .attr('x', sw.x + 10)
        .attr('y', sw.normalY - 10)
        .attr('text-anchor', 'start')
        .attr('fill', '#8899bb')
        .attr('font-size', '10px')
        .attr('font-family', 'monospace')
        .attr('data-label-for', sw.id)
        .text(sw.id);
    });
  }

  showConflictWarning(conflictPoints) {
    if (!this._conflictGroup) return;

    for (let i = 0; i < conflictPoints.length; i++) {
      const cp = conflictPoints[i];
      let cx = cp.x;
      let cy = cp.y;

      if (cx == null || cy == null) {
        const trackEl = this._trackGroup.select(`path[data-track-id="${cp.trackId}"]`);
        if (!trackEl.empty()) {
          const node = trackEl.node();
          try {
            const bbox = node.getBBox();
            cx = bbox.x + bbox.width / 2;
            cy = bbox.y + bbox.height / 2;
          } catch (_e) {
            continue;
          }
        } else {
          continue;
        }
      }

      this._conflictGroup.append('circle')
        .attr('cx', cx)
        .attr('cy', cy)
        .attr('r', 15)
        .attr('fill', 'rgba(204, 51, 255, 0.3)')
        .attr('stroke', '#cc33ff')
        .attr('stroke-width', 2)
        .attr('class', 'conflict-warning-pulse')
        .attr('data-conflict-track', cp.trackId);
    }
  }

  clearConflictWarnings() {
    if (this._conflictGroup) {
      this._conflictGroup.selectAll('*').remove();
    }
    if (this.g) {
      this.g.selectAll('.conflict-warning-pulse').remove();
    }
  }

  _buildSwitchPath(sw, position) {
    const x = sw.x;
    const normalY = sw.normalY;
    const reverseY = sw.reverseY;
    const frogLen = 50;

    if (position === 'NORMAL') {
      return `M${x - frogLen},${normalY} L${x + frogLen},${normalY}`;
    }

    return `M${x - frogLen},${normalY} L${x},${normalY} L${x + frogLen},${reverseY}`;
  }

  _fitView() {
    try {
      const bbox = this.g.node().getBBox();
      if (bbox.width === 0 || bbox.height === 0) return;

      const containerRect = this.container.getBoundingClientRect();
      const padding = 40;
      const scaleX = (containerRect.width - padding * 2) / bbox.width;
      const scaleY = (containerRect.height - padding * 2) / bbox.height;
      const scale = Math.min(scaleX, scaleY, 1.5);

      const tx = containerRect.width / 2 - (bbox.x + bbox.width / 2) * scale;
      const ty = containerRect.height / 2 - (bbox.y + bbox.height / 2) * scale;

      this.svg.call(
        this.zoomBehavior.transform,
        d3.zoomIdentity.translate(tx, ty).scale(scale)
      );
    } catch (_e) {}
  }

  _enqueueMutation(fn) {
    this._pendingMutations.push(fn);
    if (!this._rafId) {
      this._rafId = requestAnimationFrame(() => this._flushMutations());
    }
  }

  _flushMutations() {
    this._rafId = null;
    const mutations = this._pendingMutations;
    this._pendingMutations = [];
    for (let i = 0; i < mutations.length; i++) {
      mutations[i]();
    }
  }

  updateTrackState(trackId, occupied) {
    const circuit = this.trackCircuits.get(trackId);
    if (!circuit) return;

    const prevOccupied = this._prevTrackState.get(trackId);
    if (prevOccupied === occupied) return;

    this._prevTrackState.set(trackId, occupied);

    const className = occupied ? 'track-segment tc-occupied' : 'track-segment tc-clear';
    const nativeNodes = circuit.domPaths.nodes();
    const engine = this;

    this._enqueueMutation(() => {
      for (let i = 0; i < nativeNodes.length; i++) {
        const node = nativeNodes[i];
        const sel = d3.select(node);
        sel.interrupt();
        sel.transition()
          .duration(TRANSITION_DURATION)
          .attr('class', className);
      }
    });
  }

  updateSignalState(signalId, aspect) {
    const signal = this.signals.get(signalId);
    if (!signal) return;

    const prevAspect = this._prevSignalState.get(signalId);
    if (prevAspect === aspect) return;

    this._prevSignalState.set(signalId, aspect);

    const color = SIGNAL_COLORS[aspect] || SIGNAL_COLORS.RED;
    const filterId = SIGNAL_FILTERS[aspect] || 'glow-red';
    const circle = signal.circle;

    this._enqueueMutation(() => {
      circle.interrupt();
      circle.transition()
        .duration(TRANSITION_DURATION)
        .attr('fill', color)
        .style('filter', `url(#${filterId})`);
    });
  }

  updateSwitchState(switchId, position) {
    const sw = this.switches.get(switchId);
    if (!sw) return;

    const prevPosition = this._prevSwitchState.get(switchId);
    if (prevPosition === position) return;

    this._prevSwitchState.set(switchId, position);

    const newPath = this._buildSwitchPath(sw, position);
    const pathEl = sw.pathEl;

    this._enqueueMutation(() => {
      pathEl.interrupt();
      pathEl.transition()
        .duration(TRANSITION_DURATION)
        .attrTween('d', function () {
          const prev = this.getAttribute('d');
          return lerpPath(prev, newPath);
        });
    });
  }

  updateAll(state) {
    if (!state) return;

    if (state.trackCircuits) {
      const entries = Object.entries(state.trackCircuits);
      for (let i = 0; i < entries.length; i++) {
        const [id, tc] = entries[i];
        const occupied = typeof tc === 'object' ? tc.occupied : Boolean(tc);
        this.updateTrackState(id, occupied);
      }
    }

    if (state.signals) {
      const entries = Object.entries(state.signals);
      for (let i = 0; i < entries.length; i++) {
        const [id, sig] = entries[i];
        const aspect = typeof sig === 'object' ? sig.aspect : sig;
        if (aspect) {
          this.updateSignalState(id, aspect);
        }
      }
    }

    if (state.switches) {
      const entries = Object.entries(state.switches);
      for (let i = 0; i < entries.length; i++) {
        const [id, sw] = entries[i];
        const position = typeof sw === 'object' ? sw.position : (sw === 1 ? 'REVERSE' : 'NORMAL');
        if (position) {
          this.updateSwitchState(id, position);
        }
      }
    }

    if (state.routes) {
      this.routes.clear();
      const entries = Object.entries(state.routes);
      for (let i = 0; i < entries.length; i++) {
        this.routes.set(entries[i][0], entries[i][1]);
      }
    }
  }

  _scheduleViewportCulling() {
    if (this._cullingTimer !== null) return;
    this._cullingTimer = setTimeout(() => {
      this._cullingTimer = null;
      this._applyViewportCulling();
    }, 150);
  }

  _applyViewportCulling() {
    if (!this.g || !this.g.node()) return;

    const containerRect = this.container.getBoundingClientRect();
    const t = this._currentTransform;

    const viewLeft = -t.x / t.k;
    const viewTop = -t.y / t.k;
    const viewRight = viewLeft + containerRect.width / t.k;
    const viewBottom = viewTop + containerRect.height / t.k;

    const margin = 100 / t.k;
    const vl = viewLeft - margin;
    const vr = viewRight + margin;
    const vt = viewTop - margin;
    const vb = viewBottom + margin;

    const trackPaths = this._trackGroup.selectAll('path.track-segment');
    trackPaths.each(function (d) {
      if (!d) return;
      const minX = Math.min(d.x1, d.x2);
      const maxX = Math.max(d.x1, d.x2);
      const minY = Math.min(d.y1, d.y2);
      const maxY = Math.max(d.y1, d.y2);

      const visible = maxX >= vl && minX <= vr && maxY >= vt && minY <= vb;
      this.style.display = visible ? '' : 'none';
    });

    const signalUnits = this._signalGroup.selectAll('g.signal-unit');
    signalUnits.each(function (d) {
      if (!d) return;
      const visible = d.x >= vl && d.x <= vr && d.y >= vt && d.y <= vb;
      this.style.display = visible ? '' : 'none';
    });

    const switchFrogs = this._switchGroup.selectAll('path.switch-frog');
    switchFrogs.each(function (d) {
      if (!d) return;
      const x = d.x;
      const minY = Math.min(d.normalY, d.reverseY);
      const maxY = Math.max(d.normalY, d.reverseY);
      const visible = (x + 50) >= vl && (x - 50) <= vr && maxY >= vt && minY <= vb;
      this.style.display = visible ? '' : 'none';
    });

    if (this._conflictGroup) {
      this._conflictGroup.selectAll('.conflict-warning-pulse').each(function () {
        this.style.display = '';
      });
    }
  }

  forceInfoSync() {
    if (this._rafId) {
      cancelAnimationFrame(this._rafId);
      this._rafId = null;
    }
    const mutations = this._pendingMutations;
    this._pendingMutations = [];
    for (let i = 0; i < mutations.length; i++) {
      mutations[i]();
    }
  }

  destroy() {
    if (this._rafId) {
      cancelAnimationFrame(this._rafId);
      this._rafId = null;
    }
    if (this._cullingTimer !== null) {
      clearTimeout(this._cullingTimer);
      this._cullingTimer = null;
    }
    if (this.svg) {
      this.svg.selectAll('*').remove();
      this.svg.on('.zoom', null);
    }
    this.trackCircuits.clear();
    this.signals.clear();
    this.switches.clear();
    this.routes.clear();
    this._prevTrackState.clear();
    this._prevSignalState.clear();
    this._prevSwitchState.clear();
    this._pendingMutations = [];
    this._conflictGroup = null;
    this.svg = null;
    this.g = null;
    this.zoomBehavior = null;
  }
}
