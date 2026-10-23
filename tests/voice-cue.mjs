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
check("manifest-small-set", ids.length === 9, "n=" + ids.length);
check("manifest-files", manifest.slots.every((slot) => slot.file === "clips/" + slot.id + ".ogg"));
const src = ["roundaboutExit", "voiceSlotFor", "voiceCueKey", "voiceShouldPlay", "voiceFileOk"].map(extract).join("\n");
const harness = new Function("check", "ids", src + `
  function play(man, extra) {
    const slot = voiceSlotFor(man);
    const key = voiceCueKey(man, slot, extra && extra.along || 0);
    return voiceShouldPlay(Object.assign({
      muted: false, busy: false, slot: slot, key: key, lastKey: "", meters: man.meters, kind: man.kind
    }, extra || {}));
  }
  check("slot-left", voiceSlotFor({ type: "turn", modifier: "left", meters: 80 }) === "balra");
  check("slot-sharp-right", voiceSlotFor({ type: "turn", modifier: "sharp right", meters: 80 }) === "jobbra");
  check("slot-straight", voiceSlotFor({ type: "continue", modifier: "straight", meters: 80 }) === "egyenesen");
  check("slot-slight-not-a-hard-turn", voiceSlotFor({ type: "turn", modifier: "slight left", meters: 80 }) === "egyenesen");
  check("slot-exit-2", voiceSlotFor({ type: "roundabout", modifier: "slight right", exit: 2, meters: 80 }) === "korforgalom-2");
  check("slot-exit-1", voiceSlotFor({ type: "rotary", exit: 1, meters: 40 }) === "korforgalom-1");
  check("slot-exit-missing", voiceSlotFor({ type: "roundabout", modifier: "right", meters: 80 }) === "korforgalom");
  check("slot-exit-4-not-invented", voiceSlotFor({ type: "roundabout", exit: 4, meters: 80 }) === "korforgalom");
  check("exit-step-silent", voiceSlotFor({ type: "exit rotary", exit: 2, meters: 30 }) === "");
  check("slot-arrive", voiceSlotFor({ type: "arrive", meters: 40 }) === "cel");
  check("slot-reroute", voiceSlotFor({ kind: "reroute", seq: 3 }) === "ujratervezes");
  check("uturn-has-no-clip", voiceSlotFor({ type: "turn", modifier: "uturn", meters: 50 }) === "");
  check("known-slots-exist", ["balra", "jobbra", "egyenesen", "korforgalom", "korforgalom-1", "korforgalom-2", "korforgalom-3", "ujratervezes", "cel"].every((id) => ids.indexOf(id) >= 0));
  check("plays-once", play({ type: "turn", modifier: "left", meters: 80 }, { along: 100 }) === true);
  const left = { type: "turn", modifier: "left", meters: 80 };
  const key = voiceCueKey(left, "balra", 100);
  check("no-repeat", voiceShouldPlay({ muted: false, busy: false, slot: "balra", key: key, lastKey: key, meters: 80 }) === false);
  check("muted", play(left, { along: 100, muted: true }) === false);
  check("busy-blocks", play(left, { along: 100, busy: true }) === false);
  check("too-far", play({ type: "turn", modifier: "right", meters: 500 }, { along: 0 }) === false);
  check("reroute-ignores-distance", play({ kind: "reroute", seq: 4 }) === true);
  check("reroute-key-changes", voiceCueKey({ kind: "reroute", seq: 4 }, "ujratervezes", 0) !== voiceCueKey({ kind: "reroute", seq: 5 }, "ujratervezes", 0));
  check("later-left-is-new", voiceCueKey(left, "balra", 100) !== voiceCueKey(left, "balra", 900));
  check("ogg-ok", voiceFileOk({ type: "audio/ogg", name: "balra.ogg", size: 2000 }) === true);
  check("mp3-ok", voiceFileOk({ type: "", name: "jobbra.mp3", size: 2000 }) === true);
  check("txt-rejected", voiceFileOk({ type: "text/plain", name: "jegyzet.txt", size: 20 }) === false);
  check("huge-rejected", voiceFileOk({ type: "audio/ogg", name: "nagy.ogg", size: 2000000 }) === false);
`);
harness(check, ids);
console.log(fails.length ? "VOICE-CUE-FAIL " + fails.length : "VOICE-CUE-OK");
if (fails.length) process.exitCode = 1;
