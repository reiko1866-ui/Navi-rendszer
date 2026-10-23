import fs from "fs";

const html = fs.readFileSync(new URL("../index.html", import.meta.url), "utf8");
const catalog = JSON.parse(fs.readFileSync(new URL("../hang/katalog.json", import.meta.url), "utf8"));
const map = JSON.parse(fs.readFileSync(new URL("../hang/integracio.json", import.meta.url), "utf8"));
const questions = JSON.parse(fs.readFileSync(new URL("../hang/kerdesek.json", import.meta.url), "utf8"));
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

const disk = fs.readdirSync(new URL("../hang/clips", import.meta.url)).filter((name) => name.endsWith(".ogg"));
const rows = catalog.hangok;
check("catalog-697", rows.length === 697, "n=" + rows.length);
check("disk-697", disk.length === 697, "n=" + disk.length);
const paths = rows.map((row) => row.utvonal);
check("unique-paths", new Set(paths).size === 697);
check("every-disk-file", disk.every((name) => paths.indexOf("hang/clips/" + name) >= 0));

const wired = rows.filter((row) => row.allapot === "bekotve");
const asked = rows.filter((row) => row.allapot === "kerdes");
check("every-file-placed", wired.length + asked.length === 697, wired.length + "+" + asked.length);
check("wired-has-event", wired.every((row) => row.helyzet && row.szerep && row.esemeny_hozzarendeles === row.helyzet));
check("question-has-id", asked.every((row) => row.kerdes && row.esemeny_hozzarendeles === null));

const mapped = [];
Object.keys(map.esemenyek).forEach((key) => {
  if (key === "nincs") return;
  map.esemenyek[key].fajlok.forEach((file) => mapped.push(file.utvonal));
});
const askedPaths = [];
questions.kerdesek.forEach((group) => group.fajlok.forEach((file) => askedPaths.push(file.utvonal)));
check("map-covers-wired", wired.every((row) => mapped.indexOf(row.utvonal) >= 0));
check("questions-cover-asked", asked.every((row) => askedPaths.indexOf(row.utvonal) >= 0));
check("no-double-path", mapped.every((path) => askedPaths.indexOf(path) < 0));
check("map-count", mapped.length + askedPaths.length === 697, mapped.length + "+" + askedPaths.length);

function norm(text) {
  return String(text || "").toLowerCase()
    .replace(/á/g, "a").replace(/é/g, "e").replace(/í/g, "i")
    .replace(/ó/g, "o").replace(/ö/g, "o").replace(/ő/g, "o")
    .replace(/ú/g, "u").replace(/ü/g, "u").replace(/ű/g, "u");
}
function heard(row) {
  return norm([row.leirat, row.ismetelt_leirat_small, row.ismetelt_leirat_medium].join(" "));
}
const need = {
  jobbra: ["jobbra", "jobra"],
  balra: ["balra"],
  elesen_jobbra: ["jobbra", "jobra"],
  elesen_balra: ["balra"],
  enyhen_jobbra: ["jobbra", "jobra"],
  enyhen_balra: ["balra"],
  egyenes: ["egyenes"],
  vissza: ["vissza"],
  korforgalom: ["korforgal", "kor forgal"],
  gps_hiany: ["gps", "felmond"],
  ujratervezes: ["ujra"]
};
let directionOk = true;
wired.forEach((row) => {
  const words = need[row.helyzet];
  const text = heard(row);
  if (!words || !words.some((word) => text.indexOf(word) >= 0)) directionOk = false;
  if (row.helyzet.indexOf("elesen_") === 0 && text.indexOf("eles") < 0) directionOk = false;
  if (row.helyzet.indexOf("enyhen_") === 0 && text.indexOf("enyh") < 0) directionOk = false;
  if (row.helyzet === "ujratervezes" && text.indexOf("tervez") < 0) directionOk = false;
});
check("direction-is-in-the-transcript", directionOk);

const twins = new Map();
rows.forEach((row) => {
  if (!twins.has(row.md5)) twins.set(row.md5, new Set());
  twins.get(row.md5).add(row.allapot + "|" + row.helyzet + "|" + row.szerep + "|" + row.kerdes);
});
check("identical-bytes-share-assignment", Array.from(twins.values()).every((set) => set.size === 1));

const src = ["voiceEventList", "voiceRecognition", "voiceLiveEvent", "voiceQueueFrom", "voicePick", "voicePickMode", "voiceGain", "voiceFollowOk", "voiceShouldPlay", "voiceEventFor", "voiceCueKey"].map(extract).join("\n");
const harness = new Function("check", src + `
  const saved = [
    { utvonal: "b.ogg", kezi_allapot: "mentve", kezi_esemeny: "jobbra", kezi_sorrend: 2 },
    { utvonal: "a.ogg", kezi_allapot: "mentve", kezi_esemeny: "jobbra", kezi_sorrend: 1 },
    { utvonal: "s.ogg", kezi_allapot: "ellenorzes", kezi_esemeny: "jobbra", kezi_sorrend: 0 },
    { utvonal: "d.ogg", kezi_allapot: "mentve", kezi_esemeny: "dugo", kezi_sorrend: 0 },
    { utvonal: "f.ogg", kezi_allapot: "mentve", kezi_esemeny: "folytatas", kezi_sorrend: 1 }
  ];
  const queues = voiceQueueFrom(saved);
  const rights = (queues.jobbra || []).map((row) => row.utvonal);
  check("saved-right-only", rights.join(",") === "a.ogg,b.ogg", rights.join(","));
  check("suggestion-not-queued", rights.indexOf("s.ogg") < 0);
  check("missing-recognition-not-queued", !queues.dugo);
  check("follow-not-in-primary", rights.indexOf("f.ogg") < 0);
  const sample = [{ utvonal: "a" }, { utvonal: "b" }, { utvonal: "c" }];
  const seen = [];
  let cursor = 0;
  for (let n = 0; n < 4; n++) {
    const pick = voicePick(sample, cursor);
    seen.push(pick.item.utvonal);
    cursor = pick.next;
  }
  check("cycle-no-repeat-until-end", seen.slice(0, 3).join(",") === "a,b,c" && seen[3] === "a", seen.join(","));
  const rotated = voicePickMode(sample, 0, "felvaltva", "a", null);
  check("rotate-skips-last", rotated.item.utvonal === "b");
  check("same-clip-mode", voicePickMode(sample, 2, "ugyanaz", "c", null).item.utvonal === "a");
  check("random-avoids-last", voicePickMode(sample, 0, "veletlen", "a", function () { return 0; }).item.utvonal !== "a");
  check("gain-mute", voiceGain(true, 0.4) === 0);
  check("gain-level", voiceGain(false, 0.4) === 0.4);
  check("gain-cap", voiceGain(false, 2) === 1);
  const clip = { szerep: "folytatas", helyzet: "jobbra" };
  check("follow-ok", voiceFollowOk({ muted: false, busy: false, followed: false, nextEvent: "jobbra", clip: clip }) === true);
  check("follow-blocked-while-busy", voiceFollowOk({ muted: false, busy: true, followed: false, nextEvent: "jobbra", clip: clip }) === false);
  check("follow-wrong-event", voiceFollowOk({ muted: false, busy: false, followed: false, nextEvent: "balra", clip: clip }) === false);
  check("overlap-blocked", voiceShouldPlay({ muted: false, busy: true, slot: "jobbra", key: "jobbra@1", lastKey: "", meters: 80, kind: "" }) === false);
  check("same-key-blocked", voiceShouldPlay({ muted: false, busy: false, slot: "jobbra", key: "jobbra@1", lastKey: "jobbra@1", meters: 80, kind: "" }) === false);
  check("right-event", voiceEventFor({ type: "turn", modifier: "right", meters: 80 }) === "jobbra");
  check("sharp-not-plain-right", voiceEventFor({ type: "turn", modifier: "sharp right", meters: 80 }) === "elesen_jobbra");
  check("roundabout-without-number", voiceEventFor({ type: "roundabout", exit: 2, meters: 40 }) === "korforgalom");
  check("roundabout-exit-event", voiceEventFor({ type: "exit roundabout", exit: 2, meters: 30 }) === "korforgalom_kijarat");
  check("reroute-event", voiceEventFor({ kind: "reroute", seq: 4 }) === "ujratervezes");
  check("offroute-event", voiceEventFor({ kind: "rossz_irany", seq: 4 }) === "rossz_irany");
  check("reroute-key", voiceCueKey({ kind: "reroute", seq: 4 }, "ujratervezes", 0) !== voiceCueKey({ kind: "reroute", seq: 5 }, "ujratervezes", 0));
`);
harness(check);
console.log(fails.length ? "VOICE-INTEGRATE-FAIL " + fails.length : "VOICE-INTEGRATE-OK");
if (fails.length) process.exitCode = 1;
