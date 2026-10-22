import puppeteer from "/tmp/node_modules/puppeteer-core/lib/puppeteer/puppeteer-core.js";
import fs from "fs";

const origin = "http://127.0.0.1:8765";
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
function minDist(coords, lat, lon) {
  let best = Infinity;
  for (let i = 1; i < coords.length; i++) {
    const [lon1, lat1] = coords[i - 1], [lon2, lat2] = coords[i];
    const vx = lon2 - lon1, vy = lat2 - lat1, len2 = vx * vx + vy * vy;
    let t = 0;
    if (len2 > 0) t = ((lon - lon1) * vx + (lat - lat1) * vy) / len2;
    if (t < 0) t = 0;
    if (t > 1) t = 1;
    const d = haversine(lat, lon, lat1 + (lat2 - lat1) * t, lon1 + (lon2 - lon1) * t);
    if (d < best) best = d;
  }
  return best;
}
function offsetEast(lat, lon, meters) {
  const dlon = meters / (111320 * Math.cos(lat * Math.PI / 180));
  return { lat: lat, lon: lon + dlon };
}
async function pump(page, n) {
  for (let i = 0; i < n; i++) await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => r())));
}
async function searchPick(page, field, query, expect) {
  const open = await page.$eval("#sheet-toggle", (el) => el.getAttribute("aria-expanded"));
  if (open !== "true") {
    await page.evaluate(() => {
      const reopen = document.getElementById("search-reopen");
      if (reopen) reopen.click();
      else document.getElementById("sheet-toggle").click();
    });
    await page.waitForFunction(() => document.getElementById("sheet-toggle").getAttribute("aria-expanded") === "true");
  }
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
  const idx = buttons.findIndex((b) => b.toLowerCase().includes(expect.toLowerCase()));
  if (idx < 0) throw new Error(JSON.stringify(buttons));
  await page.evaluate((i) => document.querySelectorAll("#goal-list button")[i].click(), idx);
}
async function push(page, lat, lon, acc, speed, t) {
  await page.evaluate((q) => window.__gps.push(q.lat, q.lon, q.acc, q.speed, q.t), { lat, lon, acc, speed, t });
}
async function view(page) {
  return page.evaluate(() => window.__routeView);
}
async function note(page) {
  return page.$eval("#nav-note", (el) => el.textContent || "");
}
async function hits(page) {
  return page.evaluate(() => window.__routeHits || 0);
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
      },
      fail(code) { listeners.forEach((l) => l.error && l.error({ code: code })); },
      listeners() { return listeners.length; }
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
  if ((await page.$eval("#sheet-toggle", (el) => el.getAttribute("aria-expanded"))) !== "true") {
    await page.evaluate(() => document.getElementById("sheet-toggle").click());
  }
  await searchPick(page, "#from-q", "Ferenciek tere Budapest", "Ferenciek");
  await new Promise((r) => setTimeout(r, 1400));
  await searchPick(page, "#to-q", "Vörösmarty tér Budapest", "Vörösmarty");
  await page.waitForFunction(() => /Tervezett útvonal/.test(document.getElementById("nav-note").textContent || ""), { timeout: 30000 });
  const coords = route.geometry.coordinates;
  const home = pointAt(coords, 80);
  const t0 = Date.now();
  await push(page, home.lat, home.lon, 8, 8, t0);
  await pump(page, 16);
  await push(page, home.lat, home.lon, 8, 8, t0 + 1000);
  await pump(page, 12);
  const locked = await view(page);
  check("locked-on-route", locked.gpsOnRoute === true && locked.route === true && locked.coast === false, "along=" + locked.along);

  const beforeHold = locked.along;
  await page.evaluate(() => window.__gps.fail(2));
  await pump(page, 24);
  const held = await view(page);
  const hint = await page.$eval("#hint", (el) => el.textContent || "");
  const speed = await page.$eval("#speed", (el) => el.textContent || "");
  const listeners = await page.evaluate(() => window.__gps.listeners());
  say("WEAK " + hint + " along=" + held.along + " hold=" + held.hold);
  check("code2-keeps-position", held.hold === true && Math.abs(held.along - beforeHold) < 1.5, "before=" + beforeHold + " after=" + held.along);
  check("code2-signals", hint.indexOf("GPS-jel akadozik") === 0 && speed.trim() === "—", hint + " speed=" + speed);
  check("code2-keeps-watch", listeners === 1, "n=" + listeners);
  await push(page, home.lat, home.lon, 8, 9, t0 + 4000);
  await pump(page, 16);
  const back = await view(page);
  const hintBack = await page.$eval("#hint", (el) => el.textContent || "");
  check("code2-resumes", back.hold === false && back.gpsOnRoute === true && hintBack.indexOf("GPS elérhető") === 0, hintBack);

  await page.evaluate(() => {
    const orig = window.fetch.bind(window);
    window.__routeHits = 0;
    window.__holdNext = false;
    window.__failNext = false;
    window.__mockOk = false;
    window.__pending = null;
    window.fetch = function (input, init) {
      const url = String(input);
      if (url.indexOf("/route/") < 0) return orig(input, init);
      window.__routeHits += 1;
      if (window.__holdNext) {
        window.__holdNext = false;
        return new Promise(function (resolve, reject) { window.__pending = { resolve: resolve, reject: reject }; });
      }
      if (window.__failNext) {
        window.__failNext = false;
        return Promise.reject(new Error("network"));
      }
      if (window.__mockOk) {
        const pair = url.split("/driving/")[1].split("?")[0].split(";");
        const a = pair[0].split(",").map(Number);
        const b = pair[1].split(",").map(Number);
        const body = {
          code: "Ok",
          routes: [{
            distance: 420,
            duration: 90,
            geometry: { type: "LineString", coordinates: [[a[0], a[1]], [b[0], b[1]]] },
            legs: [{ steps: [] }]
          }],
          waypoints: [
            { location: [a[0], a[1]], name: "Most", distance: 4 },
            { location: [b[0], b[1]], name: "Cél", distance: 0 }
          ]
        };
        return Promise.resolve(new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json" } }));
      }
      return orig(input, init);
    };
  });

  let far = null;
  let away = 0;
  for (const meters of [180, 260, 420, 700]) {
    const cand = offsetEast(home.lat, home.lon, meters);
    away = minDist(coords, cand.lat, cand.lon);
    if (away > 130) { far = cand; break; }
  }
  say("FAR dist=" + Math.round(away));
  check("far-point", !!far && away > 130, "dist=" + Math.round(away));

  await push(page, far.lat, far.lon, 150, 0, t0 + 6000);
  await pump(page, 6);
  const poor = await view(page);
  check("poor-sample-stays", poor.gpsOnRoute === true && (await hits(page)) === 0, "on=" + poor.gpsOnRoute + " hits=" + (await hits(page)));

  await push(page, far.lat, far.lon, 8, 0, t0 + 8000);
  await push(page, home.lat, home.lon, 8, 8, t0 + 9000);
  await pump(page, 8);
  const flickered = await view(page);
  check("flicker-returns", flickered.gpsOnRoute === true && (await hits(page)) === 0, "on=" + flickered.gpsOnRoute);

  await push(page, far.lat, far.lon, 55, 0, t0 + 11000);
  await push(page, far.lat, far.lon, 55, 0, t0 + 22000);
  await pump(page, 6);
  check("inaccurate-pair-no-reroute", (await hits(page)) === 0 && (await view(page)).gpsOnRoute === false, "hits=" + (await hits(page)));

  await push(page, home.lat, home.lon, 8, 8, t0 + 23000);
  await pump(page, 8);
  await push(page, far.lat, far.lon, 10, 0, t0 + 25000);
  await push(page, far.lat, far.lon, 10, 0, t0 + 28000);
  await pump(page, 6);
  check("short-gap-no-reroute", (await hits(page)) === 0, "hits=" + (await hits(page)));

  await push(page, home.lat, home.lon, 8, 8, t0 + 29000);
  await pump(page, 8);
  await page.evaluate(() => { window.__holdNext = true; });
  await push(page, far.lat, far.lon, 10, 0, t0 + 31000);
  await push(page, far.lat, far.lon, 10, 0, t0 + 41000);
  await pump(page, 8);
  const pending = await view(page);
  check("confirmed-one-request", (await hits(page)) === 1 && pending.rerouteBusy === true, "hits=" + (await hits(page)) + " busy=" + pending.rerouteBusy);
  await push(page, far.lat, far.lon, 10, 0, t0 + 43000);
  await push(page, far.lat, far.lon, 10, 0, t0 + 52000);
  await push(page, far.lat, far.lon, 10, 0, t0 + 61000);
  await pump(page, 6);
  check("no-parallel-reroute", (await hits(page)) === 1, "hits=" + (await hits(page)));

  await page.evaluate(() => window.__pending.reject(new Error("network")));
  await pump(page, 8);
  const failedNote = await note(page);
  const failedView = await view(page);
  say("FAILNOTE " + failedNote);
  check("osrm-error-keeps-route", failedView.route === true && failedView.rerouteBusy === false && failedNote.indexOf("régi útvonal megmarad") >= 0, failedNote);
  await push(page, far.lat, far.lon, 10, 0, t0 + 80000);
  await push(page, far.lat, far.lon, 10, 0, t0 + 92000);
  await pump(page, 6);
  check("no-auto-retry-after-error", (await hits(page)) === 1, "hits=" + (await hits(page)));

  await new Promise((r) => setTimeout(r, 1300));
  await page.evaluate(() => { window.__mockOk = true; document.getElementById("search-reopen").click(); });
  await page.waitForFunction(() => document.getElementById("sheet-toggle").getAttribute("aria-expanded") === "true");
  await page.evaluate(() => document.getElementById("gps").click());
  await page.waitForFunction(() => /Tervezett útvonal|nem sikerült|folyamatban|Várj/.test(document.getElementById("nav-note").textContent || ""), { timeout: 15000 });
  await pump(page, 12);
  const retried = await view(page);
  const retryNote = await note(page);
  say("RETRY " + retryNote + " hits=" + (await hits(page)) + " on=" + retried.gpsOnRoute);
  check("user-can-retry", (await hits(page)) === 2 && retried.route === true && retryNote.indexOf("Tervezett útvonal") >= 0, retryNote);
  check("retry-follows", retried.gpsOnRoute === true && retried.coast === false, "on=" + retried.gpsOnRoute);

  say(fails.length ? "REROUTE-FAIL " + fails.length : "REROUTE-OK");
} finally {
  fs.writeFileSync("/tmp/nav-reroute.log", lines.join("\n") + "\n");
  await browser.close();
}
if (fails.length) process.exitCode = 1;
