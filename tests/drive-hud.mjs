import puppeteer from "/tmp/node_modules/puppeteer-core/lib/puppeteer/puppeteer-core.js";

const origin = "http://127.0.0.1:8765";
const fails = [];
function check(name, ok, detail) {
  const row = (ok ? "PASS " : "FAIL ") + name + (detail ? " " + detail : "");
  console.log(row);
  if (!ok) fails.push(row);
}

const browser = await puppeteer.launch({
  executablePath: "/usr/local/bin/google-chrome",
  headless: "new",
  args: ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--no-sandbox", "--disable-dev-shm-usage"]
});
try {
  const page = await browser.newPage();
  const errors = [];
  page.on("pageerror", (err) => errors.push(String(err)));
  await page.setViewport({ width: 390, height: 844, isMobile: true, hasTouch: true, deviceScaleFactor: 1 });
  await page.goto(origin + "/index.html?gfx=mobile", { waitUntil: "domcontentloaded", timeout: 40000 });
  await page.waitForFunction(() => {
    const hint = document.getElementById("hint");
    return hint && hint.textContent && hint.textContent !== "A kocsi áll";
  }, { timeout: 8000 });

  const idle = await page.evaluate(() => {
    const bar = getComputedStyle(document.getElementById("maneuver"));
    const speed = getComputedStyle(document.getElementById("speed-pill"));
    const card = getComputedStyle(document.getElementById("drive-card"));
    const tools = getComputedStyle(document.getElementById("tools"));
    return {
      bar: bar.backgroundColor,
      radius: speed.borderRadius,
      speedBg: speed.backgroundColor,
      cardDisplay: card.display,
      toolsDir: tools.flexDirection,
      hint: document.getElementById("hint").textContent,
      street: document.getElementById("street").textContent,
      dist: document.getElementById("dist").textContent,
      compass: document.getElementById("compass-letter").textContent,
      search: !!document.getElementById("nav-search")
    };
  });
  check("blue-bar", idle.bar === "rgb(26, 120, 212)", idle.bar);
  check("round-speed", idle.radius === "50%" && idle.speedBg === "rgb(255, 255, 255)", idle.radius + " " + idle.speedBg);
  check("card-hidden-idle", idle.cardDisplay === "none", idle.cardDisplay);
  check("tools-column", idle.toolsDir === "column", idle.toolsDir);
  check("idle-copy", idle.street === "Nincs útvonal" && idle.dist === "—" && /GPS|Helymeghatározás|Nincs engedély|Nincs GPS/.test(idle.hint), idle.hint);
  check("compass-letter", /^(É|K|D|Ny)$/.test(idle.compass), idle.compass);
  check("search-button", idle.search === true);
  await page.screenshot({ path: "/tmp/hud-idle.png" });

  await page.click("#nav-search");
  await page.waitForFunction(() => document.body.classList.contains("sheet-open"));
  const open = await page.evaluate(() => ({
    expanded: document.getElementById("sheet-toggle").getAttribute("aria-expanded"),
    tools: getComputedStyle(document.getElementById("tools")).display,
    speed: getComputedStyle(document.getElementById("speed-pill")).display
  }));
  check("search-opens", open.expanded === "true" && open.tools === "none" && open.speed === "none", JSON.stringify(open));
  await page.screenshot({ path: "/tmp/hud-search.png" });
  await page.click("#sheet-toggle");
  await page.waitForFunction(() => !document.body.classList.contains("sheet-open"));

  await page.click("#voice-mute");
  const muted = await page.evaluate(() => ({
    pressed: document.getElementById("voice-mute").getAttribute("aria-pressed"),
    box: document.getElementById("voice-enabled").checked,
    store: localStorage.getItem("toonnav-voice")
  }));
  check("mute", muted.pressed === "true" && muted.box === false && muted.store === "off", JSON.stringify(muted));
  await page.click("#voice-mute");
  const unmuted = await page.evaluate(() => localStorage.getItem("toonnav-voice") === "on" && document.getElementById("voice-enabled").checked === true);
  check("unmute", unmuted === true);

  check("no-pageerror", errors.length === 0, errors.join(" | "));
  await page.setViewport({ width: 1280, height: 800, isMobile: false, hasTouch: false, deviceScaleFactor: 1 });
  await page.screenshot({ path: "/tmp/hud-desktop.png" });
  const desktop = await page.evaluate(() => {
    const sheet = document.getElementById("sheet").getBoundingClientRect();
    const speed = document.getElementById("speed-pill").getBoundingClientRect();
    const bar = document.getElementById("maneuver").getBoundingClientRect();
    return { sheet: Math.round(sheet.width), bar: Math.round(bar.width), speedTop: Math.round(speed.top) };
  });
  check("desktop-sheet-card", desktop.sheet <= 460 && desktop.bar > 1000, JSON.stringify(desktop));
} finally {
  await browser.close();
}
if (fails.length) {
  console.error(fails.join("\n"));
  process.exit(1);
}
