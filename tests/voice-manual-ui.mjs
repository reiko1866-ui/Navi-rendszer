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
  await page.waitForFunction(() => /Összes 697/.test(document.getElementById("voice-status").textContent || ""), { timeout: 10000 });
  await page.evaluate(() => document.getElementById("assign-open").click());
  const count = await page.$eval("#assign-list", (el) => el.getAttribute("data-count"));
  check("panel-lists-697", count === "697", count);
  const clip = await page.evaluate(async () => {
    const res = await fetch("hang/clips/" + encodeURIComponent("Balra.ogg"));
    return res.status + " " + (res.headers.get("content-type") || "");
  });
  check("ogg-fetch", clip.indexOf("200") === 0, clip);
  await page.$eval("#assign-search", (el) => {
    el.value = "Balra.ogg";
    el.dispatchEvent(new Event("input"));
  });
  const visible = await page.$$eval("#assign-list .assign-row", (els) => els.filter((el) => !el.hidden).map((el) => ({
    path: el.getAttribute("data-path"),
    text: el.querySelector(".assign-transcript").textContent,
    state: el.querySelector(".assign-state").textContent
  })));
  check("search-finds-file", visible.length === 1 && visible[0].path === "hang/clips/Balra.ogg", JSON.stringify(visible));
  check("search-shows-transcript", visible[0] && visible[0].text.indexOf("BÓRA") >= 0, visible[0] && visible[0].text);
  const played = await page.evaluate(() => {
    const before = localStorage.getItem("toonnav-voice-manual");
    document.querySelector("#assign-list .assign-play").click();
    return { before: before, after: localStorage.getItem("toonnav-voice-manual"), label: document.querySelector("#assign-list .assign-play").textContent };
  });
  check("play-does-not-save", played.before === played.after, played.label);
  await page.evaluate(() => {
    const art = document.querySelector("#assign-list .assign-row:not([hidden])");
    art.querySelector(".assign-event").value = "jobbra";
    art.querySelector(".assign-note").value = "kézi";
    art.querySelector(".assign-save").click();
  });
  const stored = await page.evaluate(() => JSON.parse(localStorage.getItem("toonnav-voice-manual")));
  const saved = stored.tetelek.find((row) => row.utvonal === "hang/clips/Balra.ogg");
  check("save-persists-memory", saved && saved.allapot === "mentve" && saved.esemeny === "jobbra" && saved.megjegyzes === "kézi");
  await page.reload({ waitUntil: "domcontentloaded" });
  await page.waitForFunction(() => /Jóváhagyott, szólhat 1/.test(document.getElementById("voice-status").textContent || ""), { timeout: 10000 });
  const reloaded = await page.evaluate(() => {
    const data = JSON.parse(localStorage.getItem("toonnav-voice-manual"));
    return data.tetelek.find((row) => row.utvonal === "hang/clips/Balra.ogg");
  });
  check("save-survives-reload", reloaded && reloaded.esemeny === "jobbra" && reloaded.allapot === "mentve");
  await page.evaluate(() => document.getElementById("assign-open").click());
  await page.$eval("#assign-search", (el) => {
    el.value = "Balra.ogg";
    el.dispatchEvent(new Event("input"));
  });
  await page.evaluate(() => {
    const art = document.querySelector("#assign-list .assign-row:not([hidden])");
    art.querySelector(".assign-event").value = "balra";
    art.querySelector(".assign-save").click();
  });
  const modified = await page.evaluate(() => JSON.parse(localStorage.getItem("toonnav-voice-manual")).tetelek.find((row) => row.utvonal === "hang/clips/Balra.ogg").esemeny);
  check("modify-in-panel", modified === "balra", modified);
  await page.evaluate(() => document.querySelector("#assign-list .assign-row:not([hidden]) .assign-clear").click());
  const cleared = await page.evaluate(() => JSON.parse(localStorage.getItem("toonnav-voice-manual")).tetelek.find((row) => row.utvonal === "hang/clips/Balra.ogg"));
  check("clear-in-panel", cleared.allapot === "nincs" && cleared.esemeny === "");
  await page.$eval("#assign-search", (el) => {
    el.value = "";
    el.dispatchEvent(new Event("input"));
  });
  await page.evaluate(() => {
    const rows = Array.from(document.querySelectorAll("#assign-list .assign-row")).slice(0, 2);
    rows.forEach((row) => { row.querySelector(".assign-check").checked = true; });
    document.getElementById("assign-bulk-event").value = "dugo";
    document.getElementById("assign-bulk-event").dispatchEvent(new Event("change"));
    document.getElementById("assign-bulk-check").click();
  });
  const preview = await page.$eval("#assign-bulk-note", (el) => el.textContent || "");
  const beforeGroup = await page.evaluate(() => localStorage.getItem("toonnav-voice-manual"));
  check("group-shows-count", preview.indexOf("2 fájlt érint") >= 0, preview);
  check("group-not-saved-yet", beforeGroup === await page.evaluate(() => localStorage.getItem("toonnav-voice-manual")));
  await page.click("#assign-bulk-save");
  const after = await page.evaluate(() => {
    const data = JSON.parse(localStorage.getItem("toonnav-voice-manual"));
    const paths = Array.from(document.querySelectorAll("#assign-list .assign-row")).slice(0, 2).map((row) => row.getAttribute("data-path"));
    return {
      rows: paths.map((path) => data.tetelek.find((item) => item.utvonal === path)),
      state: document.querySelector("#assign-list .assign-state").textContent,
      counts: document.getElementById("assign-counts").textContent
    };
  });
  check("group-saved", after.rows.every((row) => row && row.allapot === "mentve" && row.esemeny === "dugo"));
  check("group-missing-label", (after.state || "").indexOf("eseményfelismerés még hiányzik") >= 0, after.state);
  check("counts-update", /Felismerés hiányzik 2/.test(after.counts || ""), after.counts);
  check("no-page-error", errors.length === 0, errors.join(" | "));
  console.log(fails.length ? "VOICE-MANUAL-UI-FAIL " + fails.length : "VOICE-MANUAL-UI-OK");
} finally {
  await browser.close();
}
if (fails.length) process.exitCode = 1;
