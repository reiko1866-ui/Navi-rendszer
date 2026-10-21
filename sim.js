(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (typeof document !== "undefined") api.boot();
  else root.RajzNav = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const KM_PER_DEG_LAT = 111.32;
  const KM_PER_DEG_LON = 111.32 * Math.cos((47.2 * Math.PI) / 180);
  const PX_PER_KM = 6;

  const PLACES = {
    sopron: { name: "Sopron", lon: 16.59, lat: 47.68, city: true },
    szombathely: { name: "Szombathely", lon: 16.62, lat: 47.23, city: true },
    csorna: { name: "Csorna", lon: 17.25, lat: 47.61, city: true },
    gyor: { name: "Győr", lon: 17.63, lat: 47.69, city: true },
    tatabanya: { name: "Tatabánya", lon: 18.39, lat: 47.59, city: true },
    zalaegerszeg: { name: "Zalaegerszeg", lon: 16.84, lat: 46.84, city: true },
    veszprem: { name: "Veszprém", lon: 17.91, lat: 47.09, city: true },
    siofok: { name: "Siófok", lon: 18.06, lat: 46.91, city: true },
    szfvar: { name: "Székesfehérvár", lon: 18.41, lat: 47.19, city: true },
    budapest: { name: "Budapest", lon: 19.04, lat: 47.5, city: true, capital: true },
    dunaujvaros: { name: "Dunaújváros", lon: 18.94, lat: 46.96, city: true },
    kaposvar: { name: "Kaposvár", lon: 17.8, lat: 46.36, city: true },
    pecs: { name: "Pécs", lon: 18.23, lat: 46.07, city: true },
    kecskemet: { name: "Kecskemét", lon: 19.69, lat: 46.91, city: true },
    szeged: { name: "Szeged", lon: 20.15, lat: 46.25, city: true },
    szolnok: { name: "Szolnok", lon: 20.2, lat: 47.18, city: true },
    bekescsaba: { name: "Békéscsaba", lon: 21.09, lat: 46.68, city: true },
    hatvan: { name: "Hatvan", lon: 19.68, lat: 47.67, city: true },
    fuzesabony: { name: "Füzesabony", lon: 20.42, lat: 47.75, city: false },
    eger: { name: "Eger", lon: 20.38, lat: 47.9, city: true },
    m30: { name: "M30", lon: 20.8, lat: 47.78, city: false },
    miskolc: { name: "Miskolc", lon: 20.78, lat: 48.1, city: true },
    polgar: { name: "Polgár", lon: 21.12, lat: 47.86, city: true },
    nyiregyhaza: { name: "Nyíregyháza", lon: 21.72, lat: 47.96, city: true },
    debrecen: { name: "Debrecen", lon: 21.63, lat: 47.53, city: true },
  };

  const ROADS = [
    { road: "M1", limit: 80, kind: "motorway", pts: ["budapest", { lon: 18.78, lat: 47.48 }, "tatabanya", { lon: 18.05, lat: 47.75 }, "gyor", "csorna"] },
    { road: "85-ös", limit: 70, kind: "main", pts: ["csorna", { lon: 16.95, lat: 47.66 }, "sopron"] },
    { road: "86-os", limit: 70, kind: "main", pts: ["csorna", { lon: 16.95, lat: 47.4 }, "szombathely"] },
    { road: "84-es", limit: 70, kind: "main", pts: ["sopron", { lon: 16.55, lat: 47.45 }, "szombathely"] },
    { road: "M7", limit: 80, kind: "motorway", pts: ["budapest", { lon: 18.75, lat: 47.42 }, "szfvar", { lon: 18.22, lat: 47.02 }, "siofok"] },
    { road: "8-as", limit: 70, kind: "main", pts: ["szfvar", "veszprem", { lon: 17.35, lat: 47.0 }, "zalaegerszeg", { lon: 16.7, lat: 47.05 }, "szombathely"] },
    { road: "65-ös", limit: 70, kind: "main", pts: ["siofok", { lon: 17.95, lat: 46.6 }, "kaposvar"] },
    { road: "66-os", limit: 70, kind: "main", pts: ["kaposvar", { lon: 17.95, lat: 46.2 }, "pecs"] },
    { road: "M6", limit: 80, kind: "motorway", pts: ["budapest", { lon: 18.98, lat: 47.25 }, "dunaujvaros", { lon: 18.7, lat: 46.45 }, "pecs"] },
    { road: "M5", limit: 80, kind: "motorway", pts: ["budapest", { lon: 19.45, lat: 47.25 }, "kecskemet", { lon: 19.95, lat: 46.55 }, "szeged"] },
    { road: "M3", limit: 80, kind: "motorway", pts: ["budapest", { lon: 19.4, lat: 47.6 }, "hatvan", "fuzesabony", "m30", "polgar", "nyiregyhaza"] },
    { road: "M25", limit: 80, kind: "motorway", pts: ["fuzesabony", "eger"] },
    { road: "M30", limit: 80, kind: "motorway", pts: ["m30", "miskolc"] },
    { road: "M35", limit: 80, kind: "motorway", pts: ["polgar", { lon: 21.4, lat: 47.7 }, "debrecen"] },
    { road: "4-es", limit: 70, kind: "main", pts: ["budapest", { lon: 19.7, lat: 47.35 }, "szolnok", { lon: 20.9, lat: 47.35 }, "debrecen"] },
    { road: "44-es", limit: 70, kind: "main", pts: ["szolnok", { lon: 20.6, lat: 46.95 }, "bekescsaba"] },
    { road: "47-es", limit: 70, kind: "main", pts: ["bekescsaba", { lon: 20.7, lat: 46.45 }, "szeged"] },
    { road: "441-es", limit: 70, kind: "main", pts: ["kecskemet", { lon: 19.95, lat: 47.05 }, "szolnok"] },
  ];

  const HUNGARY = [
    [16.1, 46.85], [16.15, 47.2], [16.4, 47.65], [16.85, 47.8], [17.2, 48.02],
    [17.9, 47.95], [18.7, 47.85], [19.5, 48.15], [20.4, 48.45], [21.4, 48.5],
    [22.15, 48.4], [22.85, 48.05], [22.9, 47.55], [22.4, 47.05], [21.6, 46.45],
    [20.8, 46.15], [19.8, 46.1], [18.7, 45.85], [17.7, 45.9], [16.7, 46.25],
    [16.2, 46.55], [16.1, 46.85],
  ];

  const DANUBE = [
    [18.55, 47.95], [18.85, 47.75], [19.0, 47.6], [19.05, 47.49], [18.99, 47.25],
    [18.9, 46.95], [18.8, 46.6], [18.85, 46.25], [18.7, 45.95],
  ];

  const TISZA = [
    [21.4, 48.4], [21.6, 48.1], [21.35, 47.9], [21.0, 47.7], [20.55, 47.4],
    [20.2, 47.18], [20.25, 46.85], [20.15, 46.4], [20.15, 46.25],
  ];

  const BALATON = [
    [17.25, 46.72], [17.55, 46.8], [17.9, 46.88], [18.15, 46.92], [18.32, 46.86],
    [18.1, 46.78], [17.7, 46.7], [17.35, 46.68],
  ];

  const FORESTS = [
    { lon: 17.7, lat: 47.2, rx: 36, ry: 22, name: "Bakony" },
    { lon: 20.55, lat: 48.05, rx: 30, ry: 16, name: "Bükk" },
    { lon: 18.22, lat: 46.18, rx: 24, ry: 14, name: "Mecsek" },
    { lon: 18.75, lat: 46.4, rx: 16, ry: 24, name: "Gemenc" },
    { lon: 16.45, lat: 46.95, rx: 22, ry: 16, name: "Őrség" },
  ];

  function project(lon, lat) {
    return {
      x: lon * KM_PER_DEG_LON * PX_PER_KM,
      y: -lat * KM_PER_DEG_LAT * PX_PER_KM,
    };
  }

  function haversine(a, b) {
    const r = 6371;
    const dLat = ((b.lat - a.lat) * Math.PI) / 180;
    const dLon = ((b.lon - a.lon) * Math.PI) / 180;
    const la1 = (a.lat * Math.PI) / 180;
    const la2 = (b.lat * Math.PI) / 180;
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) ** 2;
    return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
  }

  function lonLatOf(point) {
    return typeof point === "string" ? PLACES[point] : point;
  }

  function mulberry32(seed) {
    let a = seed >>> 0;
    return function () {
      a |= 0;
      a = (a + 0x6d2b79f5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function pointInPoly(lon, lat, poly) {
    let inside = false;
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
      const xi = poly[i][0];
      const yi = poly[i][1];
      const xj = poly[j][0];
      const yj = poly[j][1];
      const hit = yi > lat !== yj > lat && lon < ((xj - xi) * (lat - yi)) / (yj - yi + 1e-12) + xi;
      if (hit) inside = !inside;
    }
    return inside;
  }

  function buildGraph() {
    const adj = {};
    Object.keys(PLACES).forEach(function (id) { adj[id] = []; });
    const drawn = [];

    ROADS.forEach(function (road) {
      const chunks = [];
      let current = [];
      road.pts.forEach(function (point) {
        current.push(point);
        if (typeof point === "string" && current.length > 1) {
          chunks.push(current);
          current = [point];
        }
      });
      chunks.forEach(function (chunk) {
        const a = chunk[0];
        const b = chunk[chunk.length - 1];
        let km = 0;
        for (let i = 1; i < chunk.length; i++) km += haversine(lonLatOf(chunk[i - 1]), lonLatOf(chunk[i]));
        const edge = { a: a, b: b, road: road.road, limit: road.limit, kind: road.kind, via: chunk.slice(1, -1), km: km };
        const hours = km / road.limit;
        adj[a].push({ to: b, edge: edge, km: km, hours: hours });
        adj[b].push({ to: a, edge: edge, km: km, hours: hours });
        drawn.push(edge);
      });
    });
    return { adj: adj, edges: drawn };
  }

  const GRAPH = buildGraph();

  function geometry(edge, fromId) {
    const forward = edge.a === fromId;
    const raw = forward ? [edge.a].concat(edge.via, [edge.b]) : [edge.b].concat(edge.via.slice().reverse(), [edge.a]);
    return raw.map(lonLatOf);
  }

  function planRoute(fromId, toId) {
    if (!PLACES[fromId] || !PLACES[toId] || fromId === toId) return null;
    const dist = {};
    const prev = {};
    const prevEdge = {};
    const queue = new Set(Object.keys(PLACES));
    queue.forEach(function (id) { dist[id] = Infinity; });
    dist[fromId] = 0;

    while (queue.size) {
      let best = null;
      queue.forEach(function (id) {
        if (best === null || dist[id] < dist[best]) best = id;
      });
      if (best === null || dist[best] === Infinity) break;
      queue.delete(best);
      if (best === toId) break;
      GRAPH.adj[best].forEach(function (link) {
        if (!queue.has(link.to)) return;
        const alt = dist[best] + link.hours;
        if (alt < dist[link.to]) {
          dist[link.to] = alt;
          prev[link.to] = best;
          prevEdge[link.to] = link.edge;
        }
      });
    }
    if (dist[toId] === Infinity) return null;

    const edgeList = [];
    let cursor = toId;
    while (cursor !== fromId) {
      edgeList.push({ edge: prevEdge[cursor], from: prev[cursor] });
      cursor = prev[cursor];
    }
    edgeList.reverse();

    const points = [];
    let km = 0;
    edgeList.forEach(function (step) {
      const geos = geometry(step.edge, step.from);
      geos.forEach(function (geo, index) {
        if (points.length && index === 0) return;
        const previous = points[points.length - 1];
        if (previous) km += haversine(previous, geo);
        const xy = project(geo.lon, geo.lat);
        const nodeId = index === geos.length - 1 ? (step.edge.a === step.from ? step.edge.b : step.edge.a) : (index === 0 ? step.from : null);
        points.push({
          lon: geo.lon,
          lat: geo.lat,
          x: xy.x,
          y: xy.y,
          km: km,
          road: step.edge.road,
          limit: step.edge.limit,
          kind: step.edge.kind,
          nodeId: nodeId,
        });
      });
    });
    if (points.length) points[0].nodeId = fromId;

    const maneuvers = [];
    for (let i = 1; i < points.length - 1; i++) {
      const point = points[i];
      if (!point.nodeId) continue;
      const before = points[i - 1];
      const after = points[i + 1];
      const inHeading = Math.atan2(point.y - before.y, point.x - before.x);
      const outHeading = Math.atan2(after.y - point.y, after.x - point.x);
      let delta = outHeading - inHeading;
      while (delta > Math.PI) delta -= Math.PI * 2;
      while (delta < -Math.PI) delta += Math.PI * 2;
      const roadChange = before.road !== after.road;
      const sharp = Math.abs(delta) > 0.45;
      if (!roadChange && !sharp) continue;
      const place = PLACES[point.nodeId];
      const placeName = place && place.city ? place.name : "";
      let action = "straight";
      let text = roadChange ? "Tovább: " + after.road : "Egyenesen";
      if (delta > 0.45) {
        action = "right";
        text = placeName ? "Jobbra · " + placeName : "Jobbra, " + after.road;
      } else if (delta < -0.45) {
        action = "left";
        text = placeName ? "Balra · " + placeName : "Balra, " + after.road;
      } else if (placeName) {
        text = "Egyenesen · " + placeName;
      }
      maneuvers.push({
        km: point.km,
        action: action,
        text: text,
        road: after.road,
        place: placeName,
        nodeId: point.nodeId,
      });
    }
    maneuvers.push({
      km: km,
      action: "arrive",
      text: "Érkezés · " + PLACES[toId].name,
      road: points.length ? points[points.length - 1].road : "",
      place: PLACES[toId].name,
      nodeId: toId,
    });

    const roads = [];
    points.forEach(function (point) {
      if (point.road && roads[roads.length - 1] !== point.road) roads.push(point.road);
    });

    return {
      fromId: fromId,
      toId: toId,
      points: points,
      totalKm: km,
      maneuvers: maneuvers,
      roads: roads,
    };
  }

  function citiesInsideCountry() {
    return Object.keys(PLACES).filter(function (id) {
      const place = PLACES[id];
      return place.city && pointInPoly(place.lon, place.lat, HUNGARY);
    });
  }

  function buildScenery() {
    const land = HUNGARY.map(function (pair) {
      const xy = project(pair[0], pair[1]);
      return { x: xy.x, y: xy.y };
    });
    const river = function (line) {
      return line.map(function (pair) {
        const xy = project(pair[0], pair[1]);
        return { x: xy.x, y: xy.y };
      });
    };
    const lake = river(BALATON);
    const rnd = mulberry32(1866);
    const patches = [];
    const bounds = boundsOf(land);
    for (let i = 0; i < 46; i++) {
      const lon = 16.2 + rnd() * 6.4;
      const lat = 46.0 + rnd() * 2.4;
      if (!pointInPoly(lon, lat, HUNGARY)) continue;
      const xy = project(lon, lat);
      const colors = ["#c5d48a", "#d9c98a", "#a9c47a", "#e0d2a0", "#8fb56e"];
      patches.push({
        x: xy.x,
        y: xy.y,
        rx: (18 + rnd() * 38) * PX_PER_KM,
        ry: (14 + rnd() * 26) * PX_PER_KM,
        rot: rnd() * Math.PI,
        color: colors[i % colors.length],
      });
    }
    const trees = [];
    FORESTS.forEach(function (forest, forestIndex) {
      const rndForest = mulberry32(40 + forestIndex * 17);
      for (let i = 0; i < 16; i++) {
        const angle = rndForest() * Math.PI * 2;
        const radius = Math.sqrt(rndForest());
        const lon = forest.lon + (Math.cos(angle) * radius * forest.rx) / KM_PER_DEG_LON;
        const lat = forest.lat + (Math.sin(angle) * radius * forest.ry) / KM_PER_DEG_LAT;
        const xy = project(lon, lat);
        trees.push({ x: xy.x, y: xy.y, s: 0.7 + rndForest() * 0.8 });
      }
    });
    return { land: land, danube: river(DANUBE), tisza: river(TISZA), lake: lake, patches: patches, trees: trees, bounds: bounds };
  }

  function boundsOf(points) {
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    points.forEach(function (point) {
      minX = Math.min(minX, point.x);
      minY = Math.min(minY, point.y);
      maxX = Math.max(maxX, point.x);
      maxY = Math.max(maxY, point.y);
    });
    return { minX: minX, minY: minY, maxX: maxX, maxY: maxY, cx: (minX + maxX) / 2, cy: (minY + maxY) / 2 };
  }

  const SCENERY = buildScenery();

  function roadPolylines() {
    return GRAPH.edges.map(function (edge) {
      const geos = geometry(edge, edge.a);
      return {
        kind: edge.kind,
        road: edge.road,
        points: geos.map(function (geo) { return project(geo.lon, geo.lat); }),
      };
    });
  }

  const ROAD_LINES = roadPolylines();

  function formatDuration(hours) {
    const minutes = Math.max(0, Math.round(hours * 60));
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h <= 0) return m + " perc";
    return h + " óra " + m + " perc";
  }

  function formatClock(simMinutes) {
    const minutes = ((Math.floor(simMinutes) % (24 * 60)) + 24 * 60) % (24 * 60);
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return String(h).padStart(2, "0") + ":" + String(m).padStart(2, "0");
  }

  function remainingHours(route, traveledKm) {
    if (!route) return 0;
    let hours = 0;
    for (let i = 1; i < route.points.length; i++) {
      const a = route.points[i - 1];
      const b = route.points[i];
      const start = Math.max(a.km, traveledKm);
      const end = b.km;
      if (end <= start) continue;
      hours += (end - start) / b.limit;
    }
    return hours;
  }

  function sampleRoute(route, km) {
    if (!route || !route.points.length) return null;
    const clamped = Math.max(0, Math.min(route.totalKm, km));
    const points = route.points;
    let index = 1;
    while (index < points.length - 1 && points[index].km < clamped) index += 1;
    const b = points[index];
    const a = points[index - 1];
    const span = Math.max(0.0001, b.km - a.km);
    const t = (clamped - a.km) / span;
    const heading = Math.atan2(b.y - a.y, b.x - a.x);
    return {
      x: a.x + (b.x - a.x) * t,
      y: a.y + (b.y - a.y) * t,
      heading: heading,
      road: b.road,
      limit: b.limit,
      kind: b.kind,
      km: clamped,
    };
  }

  function nextManeuver(route, traveledKm) {
    if (!route) return null;
    for (let i = 0; i < route.maneuvers.length; i++) {
      if (route.maneuvers[i].km > traveledKm + 0.03) return route.maneuvers[i];
    }
    return route.maneuvers[route.maneuvers.length - 1];
  }

  function boot() {
    const mapCanvas = document.getElementById("map");
    const miniCanvas = document.getElementById("minimap");
    const windCanvas = document.getElementById("wind");
    const atlas = document.getElementById("atlas");
    const cab = document.getElementById("cab");
    const fromSelect = document.getElementById("from");
    const toSelect = document.getElementById("to");
    const headline = document.getElementById("headline");
    const subline = document.getElementById("subline");
    const startButton = document.getElementById("start");

    const cityIds = Object.keys(PLACES).filter(function (id) { return PLACES[id].city; })
      .sort(function (a, b) { return PLACES[a].name.localeCompare(PLACES[b].name, "hu"); });

    cityIds.forEach(function (id) {
      const optionA = document.createElement("option");
      optionA.value = id;
      optionA.textContent = PLACES[id].name;
      const optionB = optionA.cloneNode(true);
      fromSelect.appendChild(optionA);
      toSelect.appendChild(optionB);
    });
    fromSelect.value = "budapest";
    toSelect.value = "gyor";

    const state = {
      fromId: "budapest",
      toId: "gyor",
      route: planRoute("budapest", "gyor"),
      traveledKm: 0,
      speedKmh: 0,
      driving: false,
      paused: false,
      arrived: false,
      timeScale: 40,
      simMinutes: 8 * 60 + 20,
      view: "atlas",
      zoom: 1,
      panX: 0,
      panY: 0,
      fitted: false,
    };

    function fitCountry(width, height) {
      const bounds = SCENERY.bounds;
      const pad = 36;
      const zx = (width - pad * 2) / Math.max(1, bounds.maxX - bounds.minX);
      const zy = (height - pad * 2) / Math.max(1, bounds.maxY - bounds.minY);
      state.zoom = Math.max(0.05, Math.min(zx, zy));
      state.panX = 0;
      state.panY = 0;
      state.fitted = true;
    }

    function syncSelects() {
      if (fromSelect.value !== state.fromId) fromSelect.value = state.fromId;
      if (toSelect.value !== state.toId) toSelect.value = state.toId;
    }

    function replanParked() {
      state.route = planRoute(state.fromId, state.toId);
      state.traveledKm = 0;
      state.speedKmh = 0;
      state.arrived = false;
      state.driving = false;
      state.paused = false;
      renderSummary();
    }

    function renderSummary() {
      const route = state.route;
      if (!route) {
        headline.textContent = "Válassz másik várost";
        subline.textContent = "Az indulás és a cél nem lehet ugyanaz.";
        startButton.disabled = true;
        return;
      }
      startButton.disabled = false;
      headline.textContent = PLACES[route.fromId].name + " → " + PLACES[route.toId].name;
      subline.textContent = route.totalKm.toFixed(0) + " km · " + route.roads.join(" · ") + " · " + formatDuration(remainingHours(route, 0));
    }

    fromSelect.addEventListener("change", function () {
      state.fromId = fromSelect.value;
      if (state.fromId === state.toId) {
        state.route = null;
        renderSummary();
        return;
      }
      replanParked();
    });
    toSelect.addEventListener("change", function () {
      state.toId = toSelect.value;
      if (state.fromId === state.toId) {
        state.route = null;
        renderSummary();
        return;
      }
      if (state.driving && !state.arrived) {
        const ahead = upcomingNode(state.route, state.traveledKm);
        const rest = ahead ? planRoute(ahead.nodeId, state.toId) : null;
        if (ahead && rest) state.route = joinPartial(state.route, state.traveledKm, ahead, rest);
        else state.route = planRoute(state.fromId, state.toId);
        state.traveledKm = 0;
        state.arrived = false;
      } else {
        replanParked();
      }
      renderSummary();
    });

    document.getElementById("panel").addEventListener("submit", function (event) {
      event.preventDefault();
      if (!state.route) return;
      state.driving = true;
      state.paused = false;
      state.arrived = false;
      showCab();
    });
    document.getElementById("to-map").addEventListener("click", showAtlas);
    document.getElementById("pause").addEventListener("click", function () {
      if (state.arrived) return;
      state.paused = !state.paused;
      document.getElementById("pause").textContent = state.paused ? "Tovább" : "Szünet";
    });
    document.getElementById("scale-actions").addEventListener("click", function (event) {
      const button = event.target.closest("button");
      if (!button) return;
      state.timeScale = Number(button.dataset.scale);
      document.querySelectorAll("#scale-actions button").forEach(function (item) {
        item.setAttribute("aria-pressed", item === button ? "true" : "false");
      });
    });
    document.getElementById("zin").addEventListener("click", function () { state.zoom *= 1.25; });
    document.getElementById("zout").addEventListener("click", function () { state.zoom = Math.max(0.05, state.zoom / 1.25); });
    document.getElementById("zfit").addEventListener("click", function () {
      const box = canvasBox(mapCanvas);
      fitCountry(box.w, box.h);
    });

    let drag = null;
    mapCanvas.addEventListener("pointerdown", function (event) {
      drag = { x: event.clientX, y: event.clientY, panX: state.panX, panY: state.panY, moved: false };
      mapCanvas.setPointerCapture(event.pointerId);
    });
    mapCanvas.addEventListener("pointermove", function (event) {
      if (!drag) return;
      const dx = event.clientX - drag.x;
      const dy = event.clientY - drag.y;
      if (Math.hypot(dx, dy) > 6) drag.moved = true;
      state.panX = drag.panX - dx / state.zoom;
      state.panY = drag.panY - dy / state.zoom;
    });
    mapCanvas.addEventListener("pointerup", function (event) {
      if (!drag) return;
      const moved = drag.moved;
      drag = null;
      if (moved || state.driving) return;
      const rect = mapCanvas.getBoundingClientRect();
      const camera = atlasCamera(canvasBox(mapCanvas).w, canvasBox(mapCanvas).h);
      const world = screenToWorld(event.clientX - rect.left, event.clientY - rect.top, camera);
      const hit = nearestCity(world.x, world.y, 26 / state.zoom);
      if (!hit || hit === state.fromId) return;
      state.toId = hit;
      toSelect.value = hit;
      replanParked();
    });
    mapCanvas.addEventListener("pointercancel", function () { drag = null; });
    mapCanvas.addEventListener("wheel", function (event) {
      event.preventDefault();
      const factor = event.deltaY < 0 ? 1.12 : 0.89;
      state.zoom = Math.max(0.05, Math.min(8, state.zoom * factor));
    }, { passive: false });

    function showCab() {
      state.view = "drive";
      atlas.hidden = true;
      cab.hidden = false;
      document.getElementById("pause").textContent = "Szünet";
    }
    function showAtlas() {
      state.view = "atlas";
      cab.hidden = true;
      atlas.hidden = false;
    }

    let last = performance.now();
    function frame(now) {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      step(dt);
      draw();
      requestAnimationFrame(frame);
    }

    function step(dt) {
      if (!state.driving || state.paused || state.arrived || !state.route) return;
      const here = sampleRoute(state.route, state.traveledKm);
      const target = here ? here.limit : 80;
      state.speedKmh += (target - state.speedKmh) * Math.min(1, dt * 1.4);
      const add = (state.speedKmh * dt * state.timeScale) / 3600;
      state.traveledKm = Math.min(state.route.totalKm, state.traveledKm + add);
      state.simMinutes += (dt * state.timeScale) / 60;
      if (state.traveledKm >= state.route.totalKm - 0.02) {
        state.traveledKm = state.route.totalKm;
        state.speedKmh = 0;
        state.arrived = true;
        state.paused = true;
      }
    }

    function draw() {
      live.route = state.route;
      live.km = state.traveledKm;
      live.minutes = state.simMinutes;
      activeRoute = state.route;
      activeKm = state.traveledKm;
      if (state.view === "atlas") {
        const box = canvasBox(mapCanvas);
        if (!state.fitted && box.w > 0) fitCountry(box.w, box.h);
        if (box.w > 0) drawMap(box.ctx, box.w, box.h, atlasCamera(box.w, box.h), "atlas");
      } else {
        const glass = canvasBox(windCanvas);
        const mini = canvasBox(miniCanvas);
        if (glass.w > 0) drawWindshield(glass.ctx, glass.w, glass.h);
        if (mini.w > 0) drawMap(mini.ctx, mini.w, mini.h, driveCamera(mini.w, mini.h), "drive");
        updateDash();
      }
    }

    function updateDash() {
      const route = state.route;
      const maneuver = nextManeuver(route, state.traveledKm);
      const remainKm = route ? Math.max(0, route.totalKm - state.traveledKm) : 0;
      const hours = remainingHours(route, state.traveledKm);
      document.getElementById("speed-num").textContent = String(Math.round(state.speedKmh));
      document.getElementById("remain").textContent = remainKm.toFixed(0) + " km";
      document.getElementById("eta").textContent = state.arrived
        ? "Megérkeztél"
        : "érkezés " + formatClock(state.simMinutes + hours * 60);
      const dist = maneuver ? Math.max(0, maneuver.km - state.traveledKm) : 0;
      document.getElementById("man-km").textContent = state.arrived ? "0 km" : dist.toFixed(dist < 10 ? 1 : 0) + " km";
      document.getElementById("man-where").textContent = maneuver ? maneuver.text : "";
      document.getElementById("man-road").textContent = hereRoad();
      drawArrow(document.getElementById("arrow"), maneuver ? maneuver.action : "straight");
    }

    function hereRoad() {
      const here = sampleRoute(state.route, state.traveledKm);
      if (!here) return "";
      const kind = here.kind === "motorway" ? "autópálya" : "főút";
      return here.road + " " + kind + " · " + here.limit + " km/h";
    }

    function atlasCamera(width, height) {
      return {
        focusX: SCENERY.bounds.cx + state.panX,
        focusY: SCENERY.bounds.cy + state.panY,
        zoom: state.zoom,
        rot: 0,
        originX: width / 2,
        originY: height / 2,
        width: width,
        height: height,
      };
    }

    function driveCamera(width, height) {
      const here = sampleRoute(state.route, state.traveledKm) || { x: SCENERY.bounds.cx, y: SCENERY.bounds.cy, heading: -Math.PI / 2 };
      const ahead = 0.62;
      return {
        focusX: here.x,
        focusY: here.y,
        zoom: Math.max(width, height) / (22 * PX_PER_KM),
        rot: -here.heading - Math.PI / 2,
        originX: width / 2,
        originY: height * ahead,
        width: width,
        height: height,
        truck: here,
      };
    }

    renderSummary();
    requestAnimationFrame(frame);
    window.addEventListener("resize", function () { state.fitted = false; });

    const params = new URLSearchParams(location.search);
    if (params.get("to") && PLACES[params.get("to")]) {
      state.toId = params.get("to");
      toSelect.value = state.toId;
      replanParked();
    }
    if (params.get("from") && PLACES[params.get("from")]) {
      state.fromId = params.get("from");
      fromSelect.value = state.fromId;
      replanParked();
    }
    if (params.get("km")) state.traveledKm = Math.min(state.route ? state.route.totalKm : 0, Number(params.get("km")) || 0);
    if (params.get("drive") === "1" && state.route) {
      state.driving = true;
      state.speedKmh = sampleRoute(state.route, state.traveledKm).limit;
      showCab();
    }
    syncSelects();
  }

  function upcomingNode(route, traveledKm) {
    if (!route) return null;
    for (let i = 0; i < route.points.length; i++) {
      const point = route.points[i];
      if (point.nodeId && point.km > traveledKm + 0.05 && point.nodeId !== route.fromId) {
        return { nodeId: point.nodeId, km: point.km };
      }
    }
    return { nodeId: route.toId, km: route.totalKm };
  }

  function joinPartial(route, traveledKm, ahead, rest) {
    if (!rest) return route;
    const prefix = [];
    const here = sampleRoute(route, traveledKm);
    prefix.push({
      lon: 0, lat: 0, x: here.x, y: here.y, km: 0,
      road: here.road, limit: here.limit, kind: here.kind, nodeId: null,
    });
    route.points.forEach(function (point) {
      if (point.km <= traveledKm + 0.001) return;
      if (point.km > ahead.km + 0.001) return;
      const shift = point.km - traveledKm;
      prefix.push(Object.assign({}, point, { km: shift }));
    });
    const offset = prefix.length ? prefix[prefix.length - 1].km : 0;
    rest.points.forEach(function (point, index) {
      if (index === 0) return;
      prefix.push(Object.assign({}, point, { km: point.km + offset }));
    });
    const maneuvers = rest.maneuvers.map(function (maneuver) {
      return Object.assign({}, maneuver, { km: maneuver.km + offset });
    });
    return {
      fromId: route.fromId,
      toId: rest.toId,
      points: prefix,
      totalKm: prefix.length ? prefix[prefix.length - 1].km : 0,
      maneuvers: maneuvers,
      roads: rest.roads,
    };
  }

  function canvasBox(canvas) {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    if (w < 2 || h < 2) return { ctx: null, w: 0, h: 0 };
    const width = Math.floor(w * dpr);
    const height = Math.floor(h * dpr);
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    const ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    return { ctx: ctx, w: w, h: h };
  }

  function screenToWorld(sx, sy, camera) {
    const rx = (sx - camera.originX) / camera.zoom;
    const ry = (sy - camera.originY) / camera.zoom;
    const cos = Math.cos(camera.rot);
    const sin = Math.sin(camera.rot);
    return {
      x: camera.focusX + rx * cos + ry * sin,
      y: camera.focusY - rx * sin + ry * cos,
    };
  }

  function worldToScreen(x, y, camera) {
    const dx = x - camera.focusX;
    const dy = y - camera.focusY;
    const cos = Math.cos(camera.rot);
    const sin = Math.sin(camera.rot);
    return {
      x: camera.originX + (dx * cos - dy * sin) * camera.zoom,
      y: camera.originY + (dx * sin + dy * cos) * camera.zoom,
    };
  }

  function nearestCity(x, y, maxDist) {
    let best = null;
    let bestDist = maxDist;
    Object.keys(PLACES).forEach(function (id) {
      if (!PLACES[id].city) return;
      const xy = project(PLACES[id].lon, PLACES[id].lat);
      const dist = Math.hypot(xy.x - x, xy.y - y);
      if (dist < bestDist) {
        bestDist = dist;
        best = id;
      }
    });
    return best;
  }

  function drawMap(ctx, width, height, camera, mode) {
    const px = function (screenPx) { return screenPx / Math.max(0.05, camera.zoom); };
    ctx.clearRect(0, 0, width, height);
    ctx.fillStyle = mode === "drive" ? "#d7c49a" : "#cbb892";
    ctx.fillRect(0, 0, width, height);
    paintGrain(ctx, width, height);

    ctx.save();
    ctx.translate(camera.originX, camera.originY);
    ctx.rotate(camera.rot);
    ctx.scale(camera.zoom, camera.zoom);
    ctx.translate(-camera.focusX, -camera.focusY);

    tracePoly(ctx, SCENERY.land);
    ctx.fillStyle = "#d5e2b4";
    ctx.fill();
    ctx.save();
    ctx.clip();
    SCENERY.patches.forEach(function (patch) {
      ctx.fillStyle = patch.color;
      ctx.globalAlpha = 0.55;
      ctx.beginPath();
      ctx.ellipse(patch.x, patch.y, patch.rx, patch.ry, patch.rot, 0, Math.PI * 2);
      ctx.fill();
    });
    ctx.globalAlpha = 1;
    SCENERY.trees.forEach(function (tree) { drawTree(ctx, tree.x, tree.y, px(8 * tree.s)); });
    ctx.restore();

    strokeLine(ctx, SCENERY.danube, px(11), "#9fd0e4");
    strokeLine(ctx, SCENERY.danube, px(6), "#3e93b8");
    strokeLine(ctx, SCENERY.tisza, px(8), "#9fd0e4");
    strokeLine(ctx, SCENERY.tisza, px(4.5), "#3e93b8");
    tracePoly(ctx, SCENERY.lake);
    ctx.fillStyle = "#4aa3c8";
    ctx.fill();
    ctx.strokeStyle = "#2f6f8d";
    ctx.lineWidth = px(2);
    ctx.stroke();

    tracePoly(ctx, SCENERY.land);
    ctx.strokeStyle = "rgba(42, 36, 28, 0.28)";
    ctx.lineWidth = px(7);
    ctx.stroke();
    ctx.strokeStyle = "#2a241c";
    ctx.lineWidth = px(2.4);
    ctx.stroke();

    ROAD_LINES.forEach(function (line) {
      const wide = line.kind === "motorway";
      strokeLine(ctx, line.points, px(wide ? 8 : 5), "#2a241c");
      strokeLine(ctx, line.points, px(wide ? 5 : 2.6), wide ? "#6d675d" : "#c4894a");
    });

    if (camera.route) {
      const progress = routePointsUntil(camera.route, camera.traveledKm);
      strokeLine(ctx, camera.route.points, px(10), "#a85b12");
      strokeLine(ctx, camera.route.points, px(5.5), "#ff9f1c");
      if (progress.length > 1) strokeLine(ctx, progress, px(2.2), "rgba(255, 246, 224, 0.9)");
      drawFlag(ctx, camera.route.points[camera.route.points.length - 1], camera.zoom);
    }

    Object.keys(PLACES).forEach(function (id) {
      if (!PLACES[id].city) return;
      const xy = project(PLACES[id].lon, PLACES[id].lat);
      drawTown(ctx, xy.x, xy.y, !!PLACES[id].capital, camera.zoom);
    });

    if (camera.truck) drawTruck(ctx, camera.truck.x, camera.truck.y, camera.truck.heading, camera.zoom);

    ctx.restore();
    drawLabels(ctx, camera, mode);
    drawScale(ctx, width, height, camera);
  }

  function routePointsUntil(route, km) {
    const points = [];
    for (let i = 0; i < route.points.length; i++) {
      if (route.points[i].km <= km) points.push(route.points[i]);
    }
    const here = sampleRoute(route, km);
    if (here) points.push(here);
    return points;
  }

  function tracePoly(ctx, points) {
    ctx.beginPath();
    ctx.moveTo(points[0].x, points[0].y);
    for (let i = 1; i < points.length; i++) ctx.lineTo(points[i].x, points[i].y);
    ctx.closePath();
  }

  function strokeLine(ctx, points, width, color) {
    if (!points || points.length < 2) return;
    ctx.beginPath();
    ctx.moveTo(points[0].x, points[0].y);
    for (let i = 1; i < points.length; i++) ctx.lineTo(points[i].x, points[i].y);
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    ctx.stroke();
  }

  function drawTree(ctx, x, y, size) {
    ctx.fillStyle = "#6b4a2e";
    ctx.fillRect(x - size * 0.12, y, size * 0.24, size * 0.45);
    ctx.fillStyle = "#2f6b45";
    ctx.beginPath();
    ctx.moveTo(x, y - size);
    ctx.lineTo(x + size * 0.55, y + size * 0.15);
    ctx.lineTo(x - size * 0.55, y + size * 0.15);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = "#1e3d28";
    ctx.lineWidth = 0.8;
    ctx.stroke();
  }

  function drawTown(ctx, x, y, capital, zoom) {
    ctx.save();
    ctx.translate(x, y);
    const scale = (capital ? 1.35 : 1.05) / zoom;
    ctx.scale(scale, scale);
    ctx.fillStyle = "rgba(70, 55, 30, 0.22)";
    ctx.beginPath();
    ctx.ellipse(0, 6, 12, 4, 0, 0, Math.PI * 2);
    ctx.fill();
    const houses = capital ? [[-9, 0], [0, -2], [8, 1]] : [[-5, 1], [3, 0]];
    houses.forEach(function (house) {
      ctx.fillStyle = "#f7edd4";
      ctx.strokeStyle = "#2a241c";
      ctx.lineWidth = 1.1;
      ctx.beginPath();
      ctx.rect(house[0], house[1], 7, 6);
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle = capital ? "#c4493a" : "#b85a32";
      ctx.beginPath();
      ctx.moveTo(house[0] - 1, house[1]);
      ctx.lineTo(house[0] + 3.5, house[1] - 4);
      ctx.lineTo(house[0] + 8, house[1]);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();
    });
    if (capital) {
      ctx.fillStyle = "#f0d48a";
      ctx.beginPath();
      ctx.arc(0, -1, 4.2, Math.PI, 0);
      ctx.fill();
      ctx.stroke();
    }
    ctx.restore();
  }

  function drawTruck(ctx, x, y, heading, zoom) {
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(heading);
    ctx.scale(1.2 / zoom, 1.2 / zoom);
    ctx.fillStyle = "rgba(36, 28, 20, 0.28)";
    ctx.beginPath();
    ctx.ellipse(0, 3, 16, 8, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = "#f4efe4";
    ctx.strokeStyle = "#2a241c";
    ctx.lineWidth = 1.2;
    roundRect(ctx, -16, -6.5, 18, 13, 2);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = "#d94f30";
    roundRect(ctx, 2, -6.5, 13, 13, 2);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = "#f2d7a4";
    ctx.fillRect(10, -4.5, 3.5, 9);
    ctx.fillStyle = "#1c1915";
    [[-12, -7.5], [-12, 5], [3, -7.5], [3, 5], [10, -7.5], [10, 5]].forEach(function (wheel) {
      ctx.fillRect(wheel[0], wheel[1], 4.5, 2.6);
    });
    ctx.restore();
  }

  function drawFlag(ctx, point, zoom) {
    ctx.save();
    ctx.translate(point.x, point.y);
    ctx.scale(1 / zoom, 1 / zoom);
    ctx.strokeStyle = "#2a241c";
    ctx.lineWidth = 1.4;
    ctx.beginPath();
    ctx.moveTo(0, 2);
    ctx.lineTo(0, -16);
    ctx.stroke();
    ctx.fillStyle = "#d94f30";
    ctx.beginPath();
    ctx.moveTo(0, -16);
    ctx.lineTo(12, -11);
    ctx.lineTo(0, -6);
    ctx.fill();
    ctx.stroke();
    ctx.restore();
  }

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  function drawLabels(ctx, camera, mode) {
    const boxes = [];
    if (camera.route && mode === "atlas") {
      const shields = {};
      ROAD_LINES.forEach(function (line) {
        if (line.kind !== "motorway" || line.points.length < 2) return;
        let length = 0;
        for (let i = 1; i < line.points.length; i++) {
          length += Math.hypot(line.points[i].x - line.points[i - 1].x, line.points[i].y - line.points[i - 1].y);
        }
        if (!shields[line.road] || length > shields[line.road].length) {
          shields[line.road] = { length: length, point: line.points[Math.floor(line.points.length / 2)] };
        }
      });
      Object.keys(shields).forEach(function (name) {
        const screen = worldToScreen(shields[name].point.x, shields[name].point.y, camera);
        if (screen.x < 24 || screen.y < 18 || screen.x > camera.width - 24 || screen.y > camera.height - 18) return;
        ctx.font = "600 11px Sora, sans-serif";
        const width = ctx.measureText(name).width + 12;
        boxes.push({ l: screen.x - width / 2, t: screen.y - 10, r: screen.x + width / 2, b: screen.y + 10, priority: 2 });
        drawShield(ctx, screen.x, screen.y, name);
      });
    }

    ctx.font = '15px "Patrick Hand", "Segoe Print", cursive';
    ctx.textAlign = "left";
    ctx.textBaseline = "middle";
    const ids = Object.keys(PLACES).filter(function (id) { return PLACES[id].city; });
    ids.sort(function (a, b) { return (PLACES[b].capital ? 1 : 0) - (PLACES[a].capital ? 1 : 0); });
    ids.forEach(function (id) {
      const place = PLACES[id];
      const xy = project(place.lon, place.lat);
      const screen = worldToScreen(xy.x, xy.y, camera);
      if (screen.x < -40 || screen.y < -20 || screen.x > camera.width + 40 || screen.y > camera.height + 20) return;
      const priority = place.capital ? 3 : (camera.route && (id === camera.route.fromId || id === camera.route.toId) ? 2 : 1);
      if (mode === "drive" && priority < 2 && Math.hypot(screen.x - camera.originX, screen.y - camera.originY) > Math.min(camera.width, camera.height) * 0.72) return;
      const label = place.name;
      const width = ctx.measureText(label).width;
      const lift = place.capital ? 30 : 20;
      const left = screen.x - width / 2;
      const box = { l: left, t: screen.y - lift - 10, r: left + width, b: screen.y - lift + 8, priority: priority };
      const blocked = boxes.some(function (other) {
        return !(box.r < other.l || box.l > other.r || box.b < other.t || box.t > other.b) && other.priority >= priority;
      });
      if (blocked && priority < 3) return;
      boxes.push(box);
      ctx.lineWidth = 4;
      ctx.strokeStyle = "rgba(244, 230, 196, 0.92)";
      ctx.strokeText(label, left, screen.y - lift);
      ctx.fillStyle = "#241c14";
      ctx.fillText(label, left, screen.y - lift);
    });
  }

  function drawShield(ctx, x, y, text) {
    ctx.font = "600 11px Sora, sans-serif";
    const width = ctx.measureText(text).width + 10;
    ctx.fillStyle = "#1f7a43";
    ctx.strokeStyle = "#f4e6c4";
    ctx.lineWidth = 1.5;
    roundRect(ctx, x - width / 2, y - 8, width, 16, 4);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = "#f7f1e4";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(text, x, y + 0.5);
  }

  function drawScale(ctx, width, height, camera) {
    const pxPerKm = PX_PER_KM * camera.zoom;
    let km = 50;
    let px = km * pxPerKm;
    while (px > 130 && km > 1) { km /= 2; px /= 2; }
    while (px < 48) { km *= 2; px *= 2; }
    const x = 16;
    const y = 18;
    ctx.strokeStyle = "#241c14";
    ctx.fillStyle = "#241c14";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.lineTo(x + px, y);
    ctx.moveTo(x, y - 4);
    ctx.lineTo(x, y + 4);
    ctx.moveTo(x + px, y - 4);
    ctx.lineTo(x + px, y + 4);
    ctx.stroke();
    ctx.font = "12px Sora, sans-serif";
    ctx.textAlign = "left";
    ctx.fillText((km >= 1 ? km : km.toFixed(1)) + " km", x, y + 16);
    void height;
  }

  let grainCanvas = null;
  function paintGrain(ctx, width, height) {
    if (!grainCanvas) {
      grainCanvas = document.createElement("canvas");
      grainCanvas.width = 180;
      grainCanvas.height = 180;
      const g = grainCanvas.getContext("2d");
      const image = g.createImageData(180, 180);
      const rnd = mulberry32(9);
      for (let i = 0; i < image.data.length; i += 4) {
        const shade = 180 + Math.floor(rnd() * 60);
        image.data[i] = shade;
        image.data[i + 1] = shade - 10;
        image.data[i + 2] = shade - 30;
        image.data[i + 3] = Math.floor(rnd() * 38);
      }
      g.putImageData(image, 0, 0);
    }
    ctx.save();
    ctx.globalAlpha = 0.35;
    const pattern = ctx.createPattern(grainCanvas, "repeat");
    ctx.fillStyle = pattern;
    ctx.fillRect(0, 0, width, height);
    ctx.restore();
  }

  function drawWindshield(ctx, width, height) {
    const hour = ((live.minutes || 8 * 60) / 60) % 24;
    const day = hour >= 6 && hour < 20;
    const horizon = height * 0.4;
    const sky = ctx.createLinearGradient(0, 0, 0, horizon);
    sky.addColorStop(0, day ? "#8ecae6" : "#141b2e");
    sky.addColorStop(1, day ? "#d7f0c8" : "#243044");
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, width, height);
    if (day) {
      ctx.fillStyle = "#ffe08a";
      ctx.beginPath();
      ctx.arc(width * 0.8, horizon * 0.42, Math.max(18, width * 0.03), 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = "#2a241c";
      ctx.lineWidth = 2;
      ctx.stroke();
    }
    drawCloud(ctx, width * 0.22, horizon * 0.35, 1);
    drawCloud(ctx, width * 0.55, horizon * 0.22, 0.75);

    ctx.fillStyle = day ? "#6f9a62" : "#243028";
    ctx.beginPath();
    ctx.moveTo(0, horizon + 10);
    for (let x = 0; x <= width; x += 16) {
      const y = horizon - 10 + Math.sin(x * 0.012) * 18 + Math.sin(x * 0.004 + 2) * 10;
      ctx.lineTo(x, y);
    }
    ctx.lineTo(width, height);
    ctx.lineTo(0, height);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = "#2a241c";
    ctx.lineWidth = 2;
    ctx.stroke();

    const here = sampleRoute(currentRoute(), currentKm());
    if (here) drawRoadAhead(ctx, width, height, horizon, here, day);
    drawHood(ctx, width, height);
  }

  function currentRoute() { return bootStateRoute(); }
  function currentKm() { return bootStateKm(); }

  let activeRoute = null;
  let activeKm = 0;
  function bootStateRoute() { return activeRoute; }
  function bootStateKm() { return activeKm; }

  function drawRoadAhead(ctx, width, height, horizon, here, day) {
    const samples = samplesAhead(activeRoute, activeKm, here, 1400, 18);
    const left = [];
    const right = [];
    const mid = [];
    samples.forEach(function (sample) {
      if (sample.forwardM < 8) return;
      left.push(projectGround(width, height, horizon, sample.forwardM, sample.rightM - 7.2));
      right.push(projectGround(width, height, horizon, sample.forwardM, sample.rightM + 7.2));
      mid.push(Object.assign(projectGround(width, height, horizon, sample.forwardM, sample.rightM), { dash: sample.dash }));
    });
    if (left.length < 2) return;
    const grassL = [];
    const grassR = [];
    samples.forEach(function (sample) {
      if (sample.forwardM < 8) return;
      grassL.push(projectGround(width, height, horizon, sample.forwardM, sample.rightM - 46));
      grassR.push(projectGround(width, height, horizon, sample.forwardM, sample.rightM + 46));
    });
    if (grassL.length > 1) {
      ctx.beginPath();
      ctx.moveTo(grassL[0].x, grassL[0].y);
      grassL.forEach(function (point) { ctx.lineTo(point.x, point.y); });
      for (let i = grassR.length - 1; i >= 0; i--) ctx.lineTo(grassR[i].x, grassR[i].y);
      ctx.closePath();
      ctx.fillStyle = day ? "#b7cf86" : "#243224";
      ctx.fill();
    }
    ctx.beginPath();
    ctx.moveTo(left[0].x, left[0].y);
    left.forEach(function (point) { ctx.lineTo(point.x, point.y); });
    for (let i = right.length - 1; i >= 0; i--) ctx.lineTo(right[i].x, right[i].y);
    ctx.closePath();
    ctx.fillStyle = day ? "#5c615b" : "#323632";
    ctx.fill();
    ctx.strokeStyle = "#2a241c";
    ctx.lineWidth = 3;
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(left[0].x, left[0].y);
    left.forEach(function (point) { ctx.lineTo(point.x, point.y); });
    ctx.strokeStyle = "#f4efe4";
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(right[0].x, right[0].y);
    right.forEach(function (point) { ctx.lineTo(point.x, point.y); });
    ctx.stroke();

    ctx.strokeStyle = "rgba(255, 159, 28, 0.95)";
    ctx.lineWidth = 5;
    ctx.lineCap = "round";
    ctx.beginPath();
    ctx.moveTo(mid[0].x, mid[0].y);
    mid.forEach(function (point) { ctx.lineTo(point.x, point.y); });
    ctx.stroke();

    ctx.strokeStyle = "rgba(255,246,224,0.9)";
    ctx.lineWidth = 3;
    for (let i = 0; i < mid.length - 1; i++) {
      if (!mid[i].dash) continue;
      ctx.beginPath();
      ctx.moveTo(mid[i].x, mid[i].y);
      ctx.lineTo(mid[i + 1].x, mid[i + 1].y);
      ctx.stroke();
    }

    const trees = [];
    samples.forEach(function (sample) {
      if (sample.forwardM < 24 || sample.forwardM > 700 || !sample.tree) return;
      [-1, 1].forEach(function (side) {
        if (sample.treeSide !== side && sample.treeSide !== 0) return;
        trees.push({
          forwardM: sample.forwardM,
          point: projectGround(width, height, horizon, sample.forwardM, sample.rightM + side * (16 + sample.treeSpread)),
        });
      });
    });
    trees.sort(function (a, b) { return b.forwardM - a.forwardM; });
    trees.forEach(function (tree) {
      const size = Math.max(8, 1400 / tree.forwardM);
      drawTree(ctx, tree.point.x, tree.point.y, size);
    });
  }

  function samplesAhead(route, km, here, meters, step) {
    const fx = Math.cos(here.heading);
    const fy = Math.sin(here.heading);
    const rx = -fy;
    const ry = fx;
    const list = [];
    for (let dist = 0; dist <= meters; dist += step) {
      const at = sampleRoute(route, km + dist / 1000);
      if (!at) break;
      const dx = (at.x - here.x) / PX_PER_KM * 1000;
      const dy = (at.y - here.y) / PX_PER_KM * 1000;
      const forwardM = dx * fx + dy * fy;
      const rightM = dx * rx + dy * ry;
      const seed = Math.floor(at.km * 20);
      list.push({
        forwardM: forwardM,
        rightM: rightM,
        dash: seed % 4 < 2,
        tree: seed % 3 === 0,
        treeSide: seed % 2 === 0 ? -1 : 1,
        treeSpread: (seed % 5) * 1.4,
      });
    }
    return list;
  }

  function projectGround(width, height, horizon, forwardM, rightM) {
    const depth = Math.max(forwardM, 7);
    const focal = Math.max(width, height, 520) * 0.92;
    return {
      x: width / 2 + (rightM * focal) / depth,
      y: horizon + (7.4 * focal) / depth,
    };
  }

  function drawCloud(ctx, x, y, scale) {
    ctx.fillStyle = "rgba(255,255,255,0.9)";
    ctx.strokeStyle = "rgba(42,36,28,0.45)";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.ellipse(x, y, 34 * scale, 16 * scale, 0, 0, Math.PI * 2);
    ctx.ellipse(x + 24 * scale, y + 6 * scale, 24 * scale, 13 * scale, 0, 0, Math.PI * 2);
    ctx.ellipse(x - 22 * scale, y + 7 * scale, 20 * scale, 12 * scale, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
  }

  function drawHood(ctx, width, height) {
    ctx.fillStyle = "#c4493a";
    ctx.beginPath();
    ctx.moveTo(0, height);
    ctx.lineTo(width * 0.18, height * 0.84);
    ctx.quadraticCurveTo(width * 0.5, height * 0.76, width * 0.82, height * 0.84);
    ctx.lineTo(width, height);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = "#2a241c";
    ctx.lineWidth = 3;
    ctx.stroke();
    ctx.strokeStyle = "rgba(255, 224, 160, 0.45)";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(width * 0.3, height * 0.9);
    ctx.quadraticCurveTo(width * 0.5, height * 0.84, width * 0.7, height * 0.9);
    ctx.stroke();
  }

  function drawArrow(svg, action) {
    const rotation = action === "left" ? -90 : action === "right" ? 90 : action === "arrive" ? 0 : 0;
    const color = action === "arrive" ? "#8fd18a" : "#ff9f1c";
    svg.innerHTML = '<g transform="translate(23 23) rotate(' + rotation + ')">' +
      '<path d="M0 16 V-8 M0 -8 L-10 2 M0 -8 L10 2" fill="none" stroke="' + color + '" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>' +
      "</g>";
  }

  const live = { route: null, km: 0, minutes: 8 * 60 + 20 };
  const baseDrawMap = drawMap;
  drawMap = function (ctx, width, height, camera, mode) {
    camera.route = live.route;
    camera.traveledKm = live.km;
    camera.truck = sampleRoute(live.route, live.km);
    activeRoute = live.route;
    activeKm = live.km;
    baseDrawMap(ctx, width, height, camera, mode);
  };

  return {
    PLACES: PLACES,
    planRoute: planRoute,
    project: project,
    citiesInsideCountry: citiesInsideCountry,
    pointInPoly: pointInPoly,
    HUNGARY: HUNGARY,
    boot: boot,
  };
});
