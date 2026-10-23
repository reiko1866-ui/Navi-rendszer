import fs from "fs";

const html = fs.readFileSync(new URL("../index.html", import.meta.url), "utf8");
const fails = [];
function check(name, ok, detail) {
  const row = (ok ? "PASS " : "FAIL ") + name + (detail ? " " + detail : "");
  console.log(row);
  if (!ok) fails.push(row);
}
function extract(name) {
  const start = html.indexOf("function " + name);
  if (start < 0) throw new Error("missing " + name);
  let i = html.indexOf("{", start);
  let depth = 0;
  for (; i < html.length; i++) {
    if (html[i] === "{") depth++;
    else if (html[i] === "}") {
      depth--;
      if (depth === 0) return html.slice(start, i + 1);
    }
  }
  throw new Error("unclosed " + name);
}
function extractMethod(name) {
  const token = "RouteCurve.prototype." + name + " = function";
  const start = html.indexOf(token);
  if (start < 0) throw new Error("missing " + name);
  let i = html.indexOf("{", start);
  let depth = 0;
  for (; i < html.length; i++) {
    if (html[i] === "{") depth++;
    else if (html[i] === "}") {
      depth--;
      if (depth === 0) return html.slice(start, i + 1);
    }
  }
  throw new Error("unclosed " + name);
}

class Vector3 {
  constructor(x, y, z) { this.x = x || 0; this.y = y || 0; this.z = z || 0; }
  set(x, y, z) { this.x = x; this.y = y; this.z = z; return this; }
  distanceTo(o) { return Math.hypot(this.x - o.x, this.y - o.y, this.z - o.z); }
  lengthSq() { return this.x * this.x + this.y * this.y + this.z * this.z; }
}
const THREE = { Vector3 };

const names = ["hypot2", "cleanPolyline", "cornerInfo", "planCorners", "filletGeom", "pushLine", "arcTriple", "markArcVerts", "buildPieces", "RouteCurve", "samplePieces", "buildDriveCurve", "roundaboutExit", "maneuverPhrase", "readSteps"];
const src = names.map((name) => extract(name)).join("\n") + "\n" + ["evalAt", "getPointAt", "getTangentAt"].map(extractMethod).join("\n");
const harness = new Function("THREE", "check", src + `
  function alongUFromLonLat() { return null; }
  function circle(radius, a0, a1, step) {
    const pts = [];
    for (let a = a0; a <= a1 + 1e-6; a += step) pts.push({ x: radius * Math.cos(a), z: radius * Math.sin(a) });
    return pts;
  }
  function radialError(curve, radius) {
    let worst = 0;
    const n = 48;
    for (let i = 0; i <= n; i++) {
      const u = i / n;
      const p = { x: 0, y: 0, z: 0 };
      curve.evalAt(u, p, null);
      const r = Math.hypot(p.x, p.z);
      const err = Math.abs(r - radius);
      if (err > worst) worst = err;
    }
    return worst;
  }
  const arc = buildDriveCurve(circle(18, 0.15, 2.15, 0.22));
  const arcErr = radialError(arc, 18);
  check("arc-stays-on-circle", arcErr < 1.6, "err=" + arcErr.toFixed(2));
  const marks = markArcVerts(circle(18, 0.15, 2.15, 0.22).filter((_, i, a) => i === 0 || i === a.length - 1 || true));
  check("arc-marked", marks.filter(Boolean).length >= 3, "n=" + marks.filter(Boolean).length);
  const tight = buildDriveCurve(circle(14, 0.4, 2.4, 0.28));
  const tightErr = radialError(tight, 14);
  check("tighter-arc-stays-out-of-center", tightErr < 2.2 && tightErr < 8, "err=" + tightErr.toFixed(2));
  const straightPts = [];
  for (let i = 0; i < 7; i++) straightPts.push({ x: 0, z: i * 12 });
  const straight = buildDriveCurve(straightPts);
  let straightOff = 0;
  for (let i = 0; i <= 20; i++) {
    const p = { x: 0, y: 0, z: 0 };
    straight.evalAt(i / 20, p, null);
    if (Math.abs(p.x) > straightOff) straightOff = Math.abs(p.x);
  }
  check("straight-not-treated-as-roundabout", markArcVerts(straightPts).every((v) => !v) && straightOff < 0.2, "off=" + straightOff.toFixed(3));
  const corner = [{ x: 0, z: 0 }, { x: 24, z: 0 }, { x: 24, z: 24 }];
  check("plain-corner-not-a-roundabout", markArcVerts(corner).every((v) => !v));
  const cornerCurve = buildDriveCurve(corner);
  let cornerClear = Infinity;
  for (let i = 0; i <= 30; i++) {
    const p = { x: 0, y: 0, z: 0 };
    cornerCurve.evalAt(i / 30, p, null);
    const d = Math.hypot(p.x - 24, p.z);
    if (d < cornerClear) cornerClear = d;
  }
  check("plain-corner-still-filleted", cornerClear > 0.7, "clear=" + cornerClear.toFixed(2));
  check("exit-two", maneuverPhrase({ type: "rotary", modifier: "slight right", exit: 2 }) === "Körforgalom, 2. kijárat");
  check("exit-one-roundabout", maneuverPhrase({ type: "roundabout", modifier: "right", exit: 1 }) === "Körforgalom, 1. kijárat");
  check("exit-missing-no-invented-number", maneuverPhrase({ type: "rotary", modifier: "slight right" }) === "Körforgalom");
  check("exit-zero-ignored", maneuverPhrase({ type: "exit roundabout", modifier: "left", exit: 0 }) === "Körforgalom");
  check("exit-fraction-ignored", maneuverPhrase({ type: "rotary", exit: 2.4 }) === "Körforgalom");
  check("ordinary-turn-unchanged", maneuverPhrase({ type: "turn", modifier: "left" }) === "Balra");
  const steps = readSteps({
    distance: 200,
    legs: [{ steps: [
      { distance: 80, name: "Bécsi út", maneuver: { type: "depart", modifier: "left", location: [19, 47] } },
      { distance: 40, name: "Bécsi út", maneuver: { type: "rotary", modifier: "slight right", exit: 2, location: [19.01, 47.01] } },
      { distance: 80, name: "Bécsi út", maneuver: { type: "exit rotary", modifier: "slight right", exit: 2, location: [19.02, 47.02] } }
    ] }]
  });
  check("steps-keep-exit", steps[1].exit === 2 && steps[2].exit === 2 && steps[0].exit === null, JSON.stringify(steps.map((s) => s.exit)));
  check("steps-without-exit-stay-null", readSteps({
    distance: 30,
    legs: [{ steps: [{ distance: 30, name: "", maneuver: { type: "roundabout", modifier: "right", location: [19, 47] } }] }]
  })[0].exit === null);
`);
harness(THREE, check);
console.log(fails.length ? "ROUNDABOUT-GEOM-FAIL " + fails.length : "ROUNDABOUT-GEOM-OK");
if (fails.length) process.exitCode = 1;
