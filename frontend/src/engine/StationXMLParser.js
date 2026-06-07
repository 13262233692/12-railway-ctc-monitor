export class StationXMLParser {
  parse(xmlString) {
    const parser = new DOMParser();
    const doc = parser.parseFromString(xmlString, 'application/xml');

    const errorNode = doc.querySelector('parsererror');
    if (errorNode) {
      throw new Error('XML解析失败: ' + errorNode.textContent);
    }

    const stationEl = doc.querySelector('station');
    if (!stationEl) {
      throw new Error('未找到station根元素');
    }

    const stationName = stationEl.getAttribute('name') || '';

    const trackCircuits = this._parseTrackCircuits(stationEl);
    const signals = this._parseSignals(stationEl);
    const switches = this._parseSwitches(stationEl);

    return { stationName, trackCircuits, signals, switches };
  }

  _parseTrackCircuits(stationEl) {
    const circuits = [];
    const els = stationEl.querySelectorAll('trackCircuit');

    els.forEach((el) => {
      const id = el.getAttribute('id');
      const type = el.getAttribute('type') || 'main';
      const segments = [];

      el.querySelectorAll('segment').forEach((seg) => {
        segments.push({
          x1: parseFloat(seg.getAttribute('x1')),
          y1: parseFloat(seg.getAttribute('y1')),
          x2: parseFloat(seg.getAttribute('x2')),
          y2: parseFloat(seg.getAttribute('y2')),
        });
      });

      circuits.push({ id, type, segments });
    });

    return circuits;
  }

  _parseSignals(stationEl) {
    const signals = [];
    const els = stationEl.querySelectorAll('signal');

    els.forEach((el) => {
      signals.push({
        id: el.getAttribute('id'),
        type: el.getAttribute('type') || 'entry',
        x: parseFloat(el.getAttribute('x')),
        y: parseFloat(el.getAttribute('y')),
        direction: el.getAttribute('direction') || 'right',
      });
    });

    return signals;
  }

  _parseSwitches(stationEl) {
    const switches = [];
    const els = stationEl.querySelectorAll('switch');

    els.forEach((el) => {
      switches.push({
        id: el.getAttribute('id'),
        x: parseFloat(el.getAttribute('x')),
        normalY: parseFloat(el.getAttribute('normalY')),
        reverseY: parseFloat(el.getAttribute('reverseY')),
      });
    });

    return switches;
  }
}
