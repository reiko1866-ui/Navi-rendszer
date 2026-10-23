import puppeteer from "/tmp/node_modules/puppeteer-core/lib/puppeteer/puppeteer-core.js";
import fs from "fs";

const origin = "http://127.0.0.1:8765";
const fromTarget = { lon: 19.00233, lat: 47.58056 };
const toTarget = { lon: 18.99374, lat: 47.58739 };
const lines = [];
const fails = [];
function say(msg) { console.log(msg); lines.push(msg); }
function check(name, ok, detail) {
  const row = (ok ? "PASS " : "FAIL ") + name + (detail ? " " + detail : "");
  say(row);
  if (!ok) fails.push(row);
}
function haversine(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const p1 = lat1 * Math.PI / 180, p2 = lat2 * Math.PI / 180;
  const dp = (lat2 - lat1) * Math.PI / 180, dl = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dp / 2) ** 2 + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(a)));
}
function pointAt(coords, target) {
  let along = 0;
  for (let i = 1; i < coords.length; i++) {
    const [lon1, lat1] = coords[i - 1], [lon2, lat2] = coords[i];
    const seg = haversine(lat1, lon1, lat2, lon2);
    if (along + seg >= target) {
      const t = seg > 0 ? (target - along) / seg : 0;
      return { lon: lon1 + (lon2 - lon1) * t, lat: lat1 + (lat2 - lat1) * t, along: target };
    }
    along += seg;
  }
  const last = coords[coords.length - 1];
  return { lon: last[0], lat: last[1], along };
}
function parseCoord(text) {
  const m = /(\d+,\d+)°\s*([ÉD])\s*,\s*(\d+,\d+)°\s*([KNy])/.exec(text || "");
  if (!m) return null;
  let lat = Number(m[1].replace(",", "."));
  let lon = Number(m[3].replace(",", "."));
  if (m[2] === "D") lat = -lat;
  if (m[4] === "Ny") lon = -lon;
  return { lat, lon };
}
function parseDist(text) {
  const t = String(text).replace(/\s/g, "").replace(",", ".");
  const km = /([\d.]+)km/.exec(t);
  if (km) return Number(km[1]) * 1000;
  const m = /([\d.]+)m/.exec(t);
  if (m) return Number(m[1]);
  return null;
}
function wrap(a) {
  while (a > Math.PI) a -= Math.PI * 2;
  while (a < -Math.PI) a += Math.PI * 2;
  return a;
}
async function pump(page, n) {
  for (let i = 0; i < n; i++) await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => r())));
}
async function openSheet(page) {
  const open = await page.$eval("#sheet-toggle", (el) => el.getAttribute("aria-expanded"));
  if (open === "true") return;
  await page.evaluate(() => {
    const reopen = document.getElementById("search-reopen");
    if (reopen && !reopen.hidden) reopen.click();
    else document.getElementById("sheet-toggle").click();
  });
  await page.waitForFunction(() => document.getElementById("sheet-toggle").getAttribute("aria-expanded") === "true");
}
async function searchPickNear(page, field, query, target) {
  await openSheet(page);
  await page.evaluate((sel) => document.querySelector(sel).focus(), field);
  await page.$eval(field, (el) => { el.value = ""; });
  await page.type(field, query, { delay: 4 });
  await new Promise((r) => setTimeout(r, 1400));
  await page.evaluate(() => document.getElementById("search-btn").click());
  await page.waitForFunction(() => {
    const note = document.getElementById("nav-note").textContent || "";
    return document.querySelectorAll("#goal-list button").length > 0 || /Várj|nem elérhető|Nincs találat/.test(note);
  }, { timeout: 25000 });
  const note = await page.$eval("#nav-note", (el) => el.textContent || "");
  if (/Várj/.test(note)) {
    await new Promise((r) => setTimeout(r, 1400));
    await page.evaluate(() => document.getElementById("search-btn").click());
    await page.waitForFunction(() => document.querySelectorAll("#goal-list button").length > 0, { timeout: 25000 });
  }
  const buttons = await page.$$eval("#goal-list button", (els) => els.map((el) => el.innerText));
  let best = -1;
  let bestD = Infinity;
  buttons.forEach((text, i) => {
    const c = parseCoord(text);
    if (!c) return;
    const d = haversine(target.lat, target.lon, c.lat, c.lon);
    if (d < bestD) { bestD = d; best = i; }
  });
  say("PICK " + field + " d=" + Math.round(bestD) + " " + (buttons[best] || "").replace(/\n/g, " | "));
  if (best < 0 || bestD > 250) throw new Error("no nearby result " + JSON.stringify(buttons));
  await page.evaluate((i) => document.querySelectorAll("#goal-list button")[i].click(), best);
}
async function hud(page) {
  return page.evaluate(() => ({
    street: document.getElementById("street").textContent || "",
    dist: document.getElementById("dist").textContent || "",
    hint: document.getElementById("hint").textContent || "",
    view: window.__routeView
  }));
}

say("SIM injected coordinates. Not a phone GPS test.");
const browser = await puppeteer.launch({
  executablePath: "/usr/local/bin/google-chrome",
  headless: "new",
  args: ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--no-sandbox", "--disable-dev-shm-usage"]
});
try {
  const page = await browser.newPage();
  const client = await page.createCDPSession();
  await client.send("Browser.setPermission", { permission: { name: "geolocation" }, setting: "granted", origin });
  await page.evaluateOnNewDocument(() => {
    try { localStorage.removeItem("toonnav-car"); localStorage.removeItem("toonnav-gfx"); } catch (e) {}
    const listeners = [];
    window.__gps = {
      push(lat, lon, acc, speed, timestamp) {
        listeners.forEach((l) => l.success && l.success({
          coords: { latitude: lat, longitude: lon, accuracy: acc, speed: speed, heading: null, altitude: null, altitudeAccuracy: null },
          timestamp: timestamp || Date.now()
        }));
      }
    };
    navigator.geolocation.watchPosition = function (success, error) {
      listeners.push({ success, error });
      return listeners.length;
    };
    navigator.geolocation.clearWatch = function () {};
  });
  page.on("pageerror", (e) => say("PAGE " + String(e)));
  let route = null;
  page.on("response", async (res) => {
    if (!res.url().includes("/route/")) return;
    try {
      const data = await res.json();
      if (data && data.routes && data.routes[0] && data.routes[0].geometry) route = data.routes[0];
    } catch (e) {}
  });
  await page.setViewport({ width: 360, height: 740, isMobile: true, hasTouch: true, deviceScaleFactor: 1 });
  await page.goto(origin + "/index.html?gfx=mobile", { waitUntil: "domcontentloaded", timeout: 40000 });
  await page.waitForFunction(() => document.getElementById("hint").textContent.indexOf("Helymeghatározás") === 0);
  await searchPickNear(page, "#from-q", "Bécsi út Üröm", fromTarget);
  await new Promise((r) => setTimeout(r, 1400));
  await searchPickNear(page, "#to-q", "Bécsi út Üröm", toTarget);
  await page.waitForFunction(() => /Tervezett útvonal/.test(document.getElementById("nav-note").textContent || ""), { timeout: 30000 });
  const steps = route.legs[0].steps;
  const rotary = steps.find((s) => /^(rotary|roundabout)$/.test((s.maneuver && s.maneuver.type) || ""));
  const exitNum = rotary && rotary.maneuver && rotary.maneuver.exit;
  say("ROTARY " + (rotary ? rotary.maneuver.type + " exit=" + exitNum + " pts=" + rotary.geometry.coordinates.length + " m=" + Math.round(rotary.distance) : "none"));
  check("osrm-has-exit", !!rotary && Number.isInteger(exitNum) && exitNum >= 1, exitNum);
  const arc = rotary.geometry.coordinates;
  const early = pointAt(arc, Math.min(8, rotary.distance * 0.25));
  const later = pointAt(arc, Math.min(rotary.distance * 0.72, rotary.distance - 6));
  const t0 = Date.now();
  await page.evaluate((q) => window.__gps.push(q.lat, q.lon, 8, 6, q.t), { lat: early.lat, lon: early.lon, t: t0 });
  await pump(page, 20);
  const first = await hud(page);
  say("EARLY " + first.street + " | " + first.dist + " | " + first.hint + " yaw=" + (first.view && first.view.carYaw));
  const phrase = "Körforgalom, " + exitNum + ". kijárat";
  check("exit-phrase", first.street === phrase, first.street);
  check("early-on-route", first.view && first.view.gpsOnRoute === true && first.view.coast === false, JSON.stringify(first.view && { on: first.view.gpsOnRoute, coast: first.view.coast }));
  const d1 = parseDist(first.dist);
  await page.evaluate((q) => window.__gps.push(q.lat, q.lon, 8, 6, q.t), { lat: later.lat, lon: later.lon, t: t0 + 3000 });
  await pump(page, 28);
  const second = await hud(page);
  const d2 = parseDist(second.dist);
  const dyaw = wrap((second.view ? second.view.carYaw : 0) - (first.view ? first.view.carYaw : 0));
  say("LATER " + second.street + " | " + second.dist + " yawΔ=" + dyaw.toFixed(3));
  check("exit-phrase-follows-gps", second.street === phrase, second.street);
  check("distance-shrinks", d1 !== null && d2 !== null && d2 < d1 - 4, "d1=" + d1 + " d2=" + d2);
  check("later-still-on-arc", second.view && second.view.gpsOnRoute === true, "on=" + (second.view && second.view.gpsOnRoute));
  check("yaw-follows-arc", Math.abs(dyaw) > 0.22 && Math.abs(dyaw) < 2.6, "dyaw=" + dyaw.toFixed(3));
  await page.screenshot({ path: "/opt/cursor/artifacts/roundabout-drive.png" });
  say(fails.length ? "ROUNDABOUT-FAIL " + fails.length : "ROUNDABOUT-OK");
} finally {
  fs.writeFileSync("/tmp/nav-roundabout.log", lines.join("\n") + "\n");
  await browser.close();
}
if (fails.length) process.exitCode = 1;
