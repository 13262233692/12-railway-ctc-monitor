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
  }

  init() {
    this.svg = d3.select(this.container);
    this.svg.selectAll('*').remove();

    this.svg.attr('class', 'station-svg');

    const defs = this.svg.append('defs');

    this._createGlowFilter(defs, 'glow-red', '#ff3333');
    this._createGlowFilter(defs, 'glow-yellow', '#ffcc00');
    this._createGlowFilter(defs, 'glow-green', '#00cc66');

    this.g = this.svg.append('g').attr('class', 'station-group');

    this.zoomBehavior = d3.zoom()
      .scaleExtent([0.3, 5])
      .on('zoom', (event) => {
        this.g.attr('transform', event.transform);
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

    const trackGroup = this.g.append('g').attr('class', 'tracks');
    const switchGroup = this.g.append('g').attr('class', 'switches');
    const signalGroup = this.g.append('g').attr('class', 'signals');
    const labelGroup = this.g.append('g').attr('class', 'labels');

    this._renderTrackCircuits(trackGroup, labelGroup, data.trackCircuits);
    this._renderSwitches(switchGroup, labelGroup, data.switches);
    this._renderSignals(signalGroup, labelGroup, data.signals);

    this._fitView();
  }

  _renderTrackCircuits(trackGroup, labelGroup, circuits) {
    circuits.forEach((circuit) => {
      const paths = [];

      circuit.segments.forEach((seg) => {
        const pathEl = trackGroup.append('path')
          .attr('d', `M${seg.x1},${seg.y1} L${seg.x2},${seg.y2}`)
          .attr('stroke', CLEAR_COLOR)
          .attr('stroke-width', CLEAR_WIDTH)
          .attr('fill', 'none')
          .attr('stroke-linecap', 'round')
          .attr('data-track-id', circuit.id)
          .attr('data-type', circuit.type);

        paths.push(pathEl);
      });

      const lastSeg = circuit.segments[circuit.segments.length - 1];
      if (lastSeg) {
        const labelX = (circuit.segments[0].x1 + lastSeg.x2) / 2;
        const labelY = lastSeg.y2 - 15;
        labelGroup.append('text')
          .attr('x', labelX)
          .attr('y', labelY)
          .attr('text-anchor', 'middle')
          .attr('fill', '#8899bb')
          .attr('font-size', '11px')
          .attr('font-family', 'monospace')
          .text(circuit.id);
      }

      this.trackCircuits.set(circuit.id, { ...circuit, paths });
    });
  }

  _renderSignals(signalGroup, labelGroup, signals) {
    signals.forEach((sig) => {
      const postHeight = 25;
      const radius = 7;
      const postY = sig.direction === 'right' ? sig.y : sig.y;
      const circleY = postY - postHeight;

      signalGroup.append('line')
        .attr('x1', sig.x)
        .attr('y1', postY)
        .attr('x2', sig.x)
        .attr('y2', circleY + radius)
        .attr('stroke', '#5a6a8b')
        .attr('stroke-width', 2);

      const circle = signalGroup.append('circle')
        .attr('cx', sig.x)
        .attr('cy', circleY)
        .attr('r', radius)
        .attr('fill', SIGNAL_COLORS.RED)
        .attr('data-signal-id', sig.id)
        .style('filter', 'url(#glow-red)');

      labelGroup.append('text')
        .attr('x', sig.x)
        .attr('y', circleY - radius - 6)
        .attr('text-anchor', 'middle')
        .attr('fill', '#e0e6f0')
        .attr('font-size', '12px')
        .attr('font-weight', 'bold')
        .attr('font-family', 'monospace')
        .text(sig.id);

      this.signals.set(sig.id, { ...sig, circle, circleY, radius });
    });
  }

  _renderSwitches(switchGroup, labelGroup, switches) {
    switches.forEach((sw) => {
      const normalPath = this._buildSwitchPath(sw, 'NORMAL');
      const pathEl = switchGroup.append('path')
        .attr('d', normalPath)
        .attr('stroke', '#6b8ab8')
        .attr('stroke-width', CLEAR_WIDTH)
        .attr('fill', 'none')
        .attr('stroke-linecap', 'round')
        .attr('data-switch-id', sw.id);

      labelGroup.append('text')
        .attr('x', sw.x + 10)
        .attr('y', sw.normalY - 10)
        .attr('text-anchor', 'start')
        .attr('fill', '#8899bb')
        .attr('font-size', '10px')
        .attr('font-family', 'monospace')
        .text(sw.id);

      this.switches.set(sw.id, { ...sw, pathEl });
    });
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
    } catch (_e) {
      // ignore fit errors on empty diagrams
    }
  }

  updateTrackState(trackId, occupied) {
    const circuit = this.trackCircuits.get(trackId);
    if (!circuit) return;

    const color = occupied ? OCCUPIED_COLOR : CLEAR_COLOR;
    const width = occupied ? OCCUPIED_WIDTH : CLEAR_WIDTH;

    circuit.paths.forEach((pathEl) => {
      pathEl
        .transition()
        .duration(TRANSITION_DURATION)
        .attr('stroke', color)
        .attr('stroke-width', width);
    });
  }

  updateSignalState(signalId, aspect) {
    const signal = this.signals.get(signalId);
    if (!signal) return;

    const color = SIGNAL_COLORS[aspect] || SIGNAL_COLORS.RED;
    const filterId = {
      RED: 'glow-red',
      YELLOW: 'glow-yellow',
      GREEN: 'glow-green',
    }[aspect] || 'glow-red';

    signal.circle
      .transition()
      .duration(TRANSITION_DURATION)
      .attr('fill', color)
      .style('filter', `url(#${filterId})`);
  }

  updateSwitchState(switchId, position) {
    const sw = this.switches.get(switchId);
    if (!sw) return;

    const newPath = this._buildSwitchPath(sw, position);

    sw.pathEl
      .transition()
      .duration(TRANSITION_DURATION)
      .attrTween('d', function () {
        const prev = this.getAttribute('d');
        return lerpPath(prev, newPath);
      });
  }

  updateAll(state) {
    if (!state) return;

    if (state.trackCircuits) {
      Object.entries(state.trackCircuits).forEach(([id, tc]) => {
        const occupied = typeof tc === 'object' ? tc.occupied : Boolean(tc);
        this.updateTrackState(id, occupied);
      });
    }

    if (state.signals) {
      Object.entries(state.signals).forEach(([id, sig]) => {
        const aspect = typeof sig === 'object' ? sig.aspect : sig;
        if (aspect) {
          this.updateSignalState(id, aspect);
        }
      });
    }

    if (state.switches) {
      Object.entries(state.switches).forEach(([id, sw]) => {
        const position = typeof sw === 'object' ? sw.position : (sw === 1 ? 'REVERSE' : 'NORMAL');
        if (position) {
          this.updateSwitchState(id, position);
        }
      });
    }

    if (state.routes) {
      this.routes.clear();
      Object.entries(state.routes).forEach(([id, route]) => {
        this.routes.set(id, route);
      });
    }
  }

  destroy() {
    if (this.svg) {
      this.svg.selectAll('*').remove();
      this.svg.on('.zoom', null);
    }
    this.trackCircuits.clear();
    this.signals.clear();
    this.switches.clear();
    this.routes.clear();
    this.svg = null;
    this.g = null;
    this.zoomBehavior = null;
  }
}
