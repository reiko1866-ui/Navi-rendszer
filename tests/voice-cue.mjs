import fs from "fs";

const html = fs.readFileSync(new URL("../index.html", import.meta.url), "utf8");
const manifest = JSON.parse(fs.readFileSync(new URL("../hang/manifest.json", import.meta.url), "utf8"));
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

const ids = manifest.slots.map((slot) => slot.id);
check("manifest-verified-set", ids.length === 3, "n=" + ids.length);
check("manifest-ids", ids.join(",") === "jobbra,balra-elesen,ujratervezes", ids.join(","));
check("manifest-files-exist", manifest.slots.every((slot) => fs.existsSync(new URL("../hang/" + slot.file, import.meta.url))));
const catalog = JSON.parse(fs.readFileSync(new URL("../hang/katalog.json", import.meta.url), "utf8"));
const exact = catalog.hangok.filter((clip) => clip.pontos).map((clip) => clip.fajl);
const wired = manifest.slots.map((slot) => slot.file.replace(/^clips\//, ""));
check("wired-clips-are-exact", wired.every((file) => exact.indexOf(file) >= 0), wired.join("|"));
check("exact-count", exact.length === 5, "n=" + exact.length);
check("catalog-count", catalog.hangok.length === 478, "n=" + catalog.hangok.length);
const src = ["roundaboutExit", "voiceSlotFor", "voiceCueKey", "voiceShouldPlay", "voiceFileOk"].map(extract).join("\n");
const harness = new Function("check", "ids", src + `
  function play(man, extra) {
    const slot = voiceSlotFor(man);
    const key = voiceCueKey(man, slot, extra && extra.along || 0);
    return voiceShouldPlay(Object.assign({
      muted: false, busy: false, slot: slot, key: key, lastKey: "", meters: man.meters, kind: man.kind
    }, extra || {}));
  }
  check("plain-left-silent", voiceSlotFor({ type: "turn", modifier: "left", meters: 80 }) === "");
  check("slot-sharp-left", voiceSlotFor({ type: "turn", modifier: "sharp left", meters: 80 }) === "balra-elesen");
  check("slot-right", voiceSlotFor({ type: "turn", modifier: "right", meters: 80 }) === "jobbra");
  check("sharp-right-silent", voiceSlotFor({ type: "turn", modifier: "sharp right", meters: 80 }) === "");
  check("straight-silent", voiceSlotFor({ type: "continue", modifier: "straight", meters: 80 }) === "");
  check("slight-silent", voiceSlotFor({ type: "turn", modifier: "slight left", meters: 80 }) === "");
  check("roundabout-exit-silent", voiceSlotFor({ type: "roundabout", modifier: "slight right", exit: 2, meters: 80 }) === "");
  check("rotary-exit-silent", voiceSlotFor({ type: "rotary", exit: 1, meters: 40 }) === "");
  check("roundabout-without-exit-silent", voiceSlotFor({ type: "roundabout", modifier: "right", meters: 80 }) === "");
  check("exit-step-silent", voiceSlotFor({ type: "exit rotary", exit: 2, meters: 30 }) === "");
  check("arrive-silent", voiceSlotFor({ type: "arrive", meters: 40 }) === "");
  check("slot-reroute", voiceSlotFor({ kind: "reroute", seq: 3 }) === "ujratervezes");
  check("uturn-has-no-clip", voiceSlotFor({ type: "turn", modifier: "uturn", meters: 50 }) === "");
  check("known-slots-exist", ["jobbra", "balra-elesen", "ujratervezes"].every((id) => ids.indexOf(id) >= 0));
  check("plays-once", play({ type: "turn", modifier: "right", meters: 80 }, { along: 100 }) === true);
  const right = { type: "turn", modifier: "right", meters: 80 };
  const key = voiceCueKey(right, "jobbra", 100);
  check("no-repeat", voiceShouldPlay({ muted: false, busy: false, slot: "jobbra", key: key, lastKey: key, meters: 80 }) === false);
  check("muted", play(right, { along: 100, muted: true }) === false);
  check("busy-blocks", play(right, { along: 100, busy: true }) === false);
  check("too-far", play({ type: "turn", modifier: "right", meters: 500 }, { along: 0 }) === false);
  check("plain-left-does-not-play", play({ type: "turn", modifier: "left", meters: 80 }, { along: 100 }) === false);
  check("reroute-ignores-distance", play({ kind: "reroute", seq: 4 }) === true);
  check("reroute-key-changes", voiceCueKey({ kind: "reroute", seq: 4 }, "ujratervezes", 0) !== voiceCueKey({ kind: "reroute", seq: 5 }, "ujratervezes", 0));
  check("later-right-is-new", voiceCueKey(right, "jobbra", 100) !== voiceCueKey(right, "jobbra", 900));
  check("ogg-ok", voiceFileOk({ type: "audio/ogg", name: "balra.ogg", size: 2000 }) === true);
  check("mp3-ok", voiceFileOk({ type: "", name: "jobbra.mp3", size: 2000 }) === true);
  check("txt-rejected", voiceFileOk({ type: "text/plain", name: "jegyzet.txt", size: 20 }) === false);
  check("huge-rejected", voiceFileOk({ type: "audio/ogg", name: "nagy.ogg", size: 2000000 }) === false);
`);
harness(check, ids);
console.log(fails.length ? "VOICE-CUE-FAIL " + fails.length : "VOICE-CUE-OK");
if (fails.length) process.exitCode = 1;
