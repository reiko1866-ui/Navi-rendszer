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
say("Voice panel. No phone and no event map.");
const browser = await puppeteer.launch({
  executablePath: "/usr/local/bin/google-chrome",
  headless: "new",
  args: ["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--no-sandbox", "--disable-dev-shm-usage"]
});
try {
  const page = await browser.newPage();
  page.on("pageerror", (err) => say("PAGE " + String(err)));
  await page.setViewport({ width: 360, height: 740, isMobile: true, hasTouch: true, deviceScaleFactor: 1 });
  await page.goto(origin + "/index.html?gfx=mobile", { waitUntil: "domcontentloaded", timeout: 40000 });
  await page.waitForFunction(() => /Bekötve/.test((document.getElementById("voice-status").textContent || "")), { timeout: 10000 });
  const rows = await page.$$eval("#voice-slots .voice-row", (els) => els.length);
  say("SLOTS " + rows);
  check("no-upload-rows", rows === 0, "n=" + rows);
  const statusText = await page.$eval("#voice-status", (el) => el.textContent || "");
  check("status-counts", statusText === "Bekötve 86 hang. Kérdésre vár 611.", statusText);
  check("volume-slider", await page.$eval("#voice-volume", (el) => el.value === "100"));
  await page.$eval("#voice-volume", (el) => {
    el.value = "40";
    el.dispatchEvent(new Event("input"));
  });
  check("volume-saves", await page.evaluate(() => localStorage.getItem("toonnav-voice-volume")) === "40");
  check("mute-starts-on", await page.$eval("#voice-enabled", (el) => el.checked === true));
  await page.$eval("#voice-enabled", (el) => { el.checked = false; el.dispatchEvent(new Event("change")); });
  check("mute-saves", await page.evaluate(() => localStorage.getItem("toonnav-voice")) === "off");
  say(fails.length ? "VOICE-UPLOAD-FAIL " + fails.length : "VOICE-UPLOAD-OK");
} finally {
  fs.writeFileSync("/tmp/nav-voice-upload.log", lines.join("\n") + "\n");
  await browser.close();
}
if (fails.length) process.exitCode = 1;
