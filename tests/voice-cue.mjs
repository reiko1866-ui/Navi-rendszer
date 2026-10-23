import fs from "fs";

const html = fs.readFileSync(new URL("../index.html", import.meta.url), "utf8");
const manifest = JSON.parse(fs.readFileSync(new URL("../hang/manifest.json", import.meta.url), "utf8"));
const catalog = JSON.parse(fs.readFileSync(new URL("../hang/katalog.json", import.meta.url), "utf8"));
const inventory = JSON.parse(fs.readFileSync(new URL("../hang/leltar.json", import.meta.url), "utf8"));
const missing = JSON.parse(fs.readFileSync(new URL("../hang/hianyzo.json", import.meta.url), "utf8"));
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

const classes = ["biztos", "bizonytalan", "vicces", "nem_navigacios", "meg_nem_ellenorzott"];
const clipPaths = fs.readdirSync(new URL("../hang/clips", import.meta.url)).filter((name) => name.endsWith(".ogg"));
check("inventory-697", inventory.fajlok.length === 697 && inventory.helyi_ogg === 697, "n=" + inventory.fajlok.length);
check("missing-audio-empty", Array.isArray(missing.hianyzo_hangfajlok) && missing.hianyzo_hangfajlok.length === 0);
check("missing-config-stated", missing.hianyzo_konfiguracio.length > 0);
check("catalog-one-record-per-file", catalog.hangok.length === 697, "n=" + catalog.hangok.length);
check("catalog-matches-disk", catalog.hangok.every((clip) => clipPaths.indexOf(clip.utvonal.split("/").pop()) >= 0));
check("no-event-assignment", catalog.hangok.every((clip) => clip.esemeny_hozzarendeles === null) && manifest.slots.length === 0);
check("classes-only", catalog.hangok.every((clip) => classes.indexOf(clip.osztaly) >= 0));
const md5s = new Set(catalog.hangok.map((clip) => clip.md5));
check("duplicates-stay-separate", catalog.hangok.length > md5s.size, catalog.hangok.length + "/" + md5s.size);
const src = ["voiceSlotFor", "voiceFileOk"].map(extract).join("\n");
const harness = new Function("check", src + `
  const samples = [
    { type: "turn", modifier: "left", meters: 80 },
    { type: "turn", modifier: "sharp left", meters: 80 },
    { type: "turn", modifier: "right", meters: 80 },
    { type: "turn", modifier: "sharp right", meters: 80 },
    { type: "roundabout", exit: 2, meters: 40 },
    { type: "arrive", meters: 20 },
    { kind: "reroute", seq: 3 }
  ];
  check("no-invented-slots", samples.every((man) => voiceSlotFor(man) === ""));
  check("ogg-ok", voiceFileOk({ type: "audio/ogg", name: "balra.ogg", size: 2000 }) === true);
  check("txt-rejected", voiceFileOk({ type: "text/plain", name: "jegyzet.txt", size: 20 }) === false);
  check("huge-rejected", voiceFileOk({ type: "audio/ogg", name: "nagy.ogg", size: 2000000 }) === false);
`);
harness(check);
console.log(fails.length ? "VOICE-CUE-FAIL " + fails.length : "VOICE-CUE-OK");
if (fails.length) process.exitCode = 1;
