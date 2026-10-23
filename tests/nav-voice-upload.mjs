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
function wavBytes() {
  const samples = 200;
  const data = samples * 2;
  const buf = Buffer.alloc(44 + data);
  buf.write("RIFF", 0);
  buf.writeUInt32LE(36 + data, 4);
  buf.write("WAVE", 8);
  buf.write("fmt ", 12);
  buf.writeUInt32LE(16, 16);
  buf.writeUInt16LE(1, 20);
  buf.writeUInt16LE(1, 22);
  buf.writeUInt32LE(8000, 24);
  buf.writeUInt32LE(16000, 28);
  buf.writeUInt16LE(2, 32);
  buf.writeUInt16LE(16, 34);
  buf.write("data", 36);
  buf.writeUInt32LE(data, 40);
  return buf;
}

say("Voice upload UI. No phone and no SCS pack.");
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
  await page.waitForFunction(() => document.querySelectorAll("#voice-slots .voice-row").length >= 9, { timeout: 10000 });
  const labels = await page.$$eval("#voice-slots .voice-row span", (els) => els.map((el) => el.textContent));
  say("SLOTS " + labels.join(" | "));
  check("nine-slots", labels.length === 9, "n=" + labels.length);
  check("roundabout-slot", labels.some((text) => text.indexOf("2. kijárat") >= 0));
  check("mute-starts-on", await page.$eval("#voice-enabled", (el) => el.checked === true));
  const wav = "/tmp/toonnav-voice-probe.wav";
  fs.writeFileSync(wav, wavBytes());
  const input = await page.$("#voice-slots input");
  await input.uploadFile(wav);
  await page.waitForFunction(() => {
    const mark = document.querySelector("#voice-slots small");
    return mark && mark.textContent === "kész";
  }, { timeout: 5000 });
  check("upload-kept", await page.$eval("#voice-slots small", (el) => el.textContent) === "kész");
  const bad = "/tmp/toonnav-voice-probe.txt";
  fs.writeFileSync(bad, "nem hang");
  const inputs = await page.$$("#voice-slots input");
  await inputs[1].uploadFile(bad);
  await page.waitForFunction(() => /ogg, mp3/.test(document.getElementById("voice-status").textContent || ""), { timeout: 5000 });
  check("text-rejected", /ogg, mp3/.test(await page.$eval("#voice-status", (el) => el.textContent || "")));
  await page.$eval("#voice-enabled", (el) => { el.checked = false; el.dispatchEvent(new Event("change")); });
  check("mute-saves", await page.evaluate(() => localStorage.getItem("toonnav-voice")) === "off");
  say(fails.length ? "VOICE-UPLOAD-FAIL " + fails.length : "VOICE-UPLOAD-OK");
} finally {
  fs.writeFileSync("/tmp/nav-voice-upload.log", lines.join("\n") + "\n");
  await browser.close();
}
if (fails.length) process.exitCode = 1;
