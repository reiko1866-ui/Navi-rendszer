import fs from "fs";

const html = fs.readFileSync(new URL("../index.html", import.meta.url), "utf8");
const catalog = JSON.parse(fs.readFileSync(new URL("../hang/katalog.json", import.meta.url), "utf8"));
const manual = JSON.parse(fs.readFileSync(new URL("../hang/hozzarendeles.json", import.meta.url), "utf8"));
const events = JSON.parse(fs.readFileSync(new URL("../hang/esemenyek.json", import.meta.url), "utf8"));
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

const names = [
  "voiceEventList", "voiceRecognition", "voiceNormText", "voiceSlug", "voiceManualClone",
  "voiceManualBlank", "voiceManualEnsure", "voiceManualMerge", "voiceManualUpsert",
  "voiceManualClear", "voiceGroupTouchCount", "voiceManualGroup", "voiceManualSetMode",
  "voiceAssignCounts", "voiceCountText", "voiceSimilarPaths", "voiceSameAudioPaths",
  "voiceLiveEvent", "voiceRowsBuild", "voiceQueueFrom", "voicePickMode", "voiceChainNext"
];
const src = names.map(extract).join("\n");
const disk = fs.readdirSync(new URL("../hang/clips", import.meta.url)).filter((name) => name.endsWith(".ogg"));
const paths = manual.tetelek.map((row) => row.utvonal);
check("assign-697", manual.tetelek.length === 697, "n=" + manual.tetelek.length);
check("assign-unique", new Set(paths).size === 697);
check("assign-on-disk", paths.every((path) => disk.indexOf(path.split("/").pop()) >= 0));
check("catalog-paths", catalog.hangok.every((row) => paths.indexOf(row.utvonal) >= 0));
const eventIds = events.esemenyek.map((event) => event.id);
check("event-dugo-missing", events.esemenyek.some((event) => event.id === "dugo" && event.felismeres === "hianyzik"));
check("event-speed-missing", events.esemenyek.some((event) => event.id === "sebesseghatar" && event.felismeres === "hianyzik"));
check("event-right-works", events.esemenyek.some((event) => event.id === "jobbra" && event.felismeres === "mukodik"));

const harness = new Function("check", "manual", "catalog", "eventIds", src + `
  const counts = voiceAssignCounts(manual.tetelek);
  check("counts-sum", counts.osszes === 697 && counts.hozzarendelt + counts.varo === 697, voiceCountText(counts));
  check("nothing-approved-yet", counts.jovahagyott === 0);
  const listIds = voiceEventList().map((event) => event.id);
  check("event-ids", eventIds.every((id) => listIds.indexOf(id) >= 0));
  check("traffic-missing", voiceRecognition("dugo") === "hianyzik");
  check("custom-missing", voiceRecognition("egyeb:sajat") === "hianyzik");
  check("right-known", voiceRecognition("jobbra") === "mukodik");
  const samplePath = manual.tetelek[0].utvonal;
  const twinPath = manual.tetelek.find((row) => row.utvonal !== samplePath).utvonal;
  const before = JSON.stringify(manual);
  const saved = voiceManualUpsert(manual, samplePath, { esemeny: "jobbra", sajat_nev: "", megjegyzes: "jegyzet", sorrend: 3, elozo: "" });
  check("save-one", saved.error === "" && saved.applied === 1);
  const row = saved.doc.tetelek.find((item) => item.utvonal === samplePath);
  check("save-fields", row.allapot === "mentve" && row.esemeny === "jobbra" && row.megjegyzes === "jegyzet" && row.sorrend === 3);
  check("save-leaves-other", saved.doc.tetelek.find((item) => item.utvonal === twinPath).allapot === manual.tetelek.find((item) => item.utvonal === twinPath).allapot);
  check("save-does-not-mutate", JSON.stringify(manual) === before);
  const changed = voiceManualUpsert(saved.doc, samplePath, { esemeny: "balra", megjegyzes: "mas", sorrend: 1, elozo: "" });
  check("modify", changed.doc.tetelek.find((item) => item.utvonal === samplePath).esemeny === "balra");
  const cleared = voiceManualClear(changed.doc, samplePath);
  const gone = cleared.doc.tetelek.find((item) => item.utvonal === samplePath);
  check("clear-assignment", gone.allapot === "nincs" && gone.esemeny === "" && gone.utvonal === samplePath);
  check("clear-keeps-record", cleared.doc.tetelek.length === 697);
  const unknown = voiceManualUpsert(manual, "hang/clips/nincs-ilyen.ogg", { esemeny: "jobbra" });
  check("unknown-file", unknown.error !== "" && unknown.doc === manual);
  const groupPaths = [samplePath, twinPath, samplePath];
  check("group-count-before-save", voiceGroupTouchCount(groupPaths) === 2);
  const grouped = voiceManualGroup(manual, groupPaths, { esemeny: "dugo", megjegyzes: "dugó", sorrend: 0, elozo: "" });
  check("group-save", grouped.applied === 2 && grouped.darab === 2);
  check("group-both", groupPaths.every((path) => grouped.doc.tetelek.find((item) => item.utvonal === path).esemeny === "dugo"));
  const groupCounts = voiceAssignCounts(grouped.doc.tetelek);
  check("group-missing-count", groupCounts.hianyzo >= 2 && groupCounts.jovahagyott === 0);
  const named = voiceManualUpsert(manual, samplePath, { esemeny: "egyeb", sajat_nev: "Komp", megjegyzes: "saját", sorrend: 0, elozo: "" });
  check("custom-event", named.error === "" && named.doc.tetelek.find((item) => item.utvonal === samplePath).esemeny.indexOf("egyeb:") === 0);
  check("custom-not-playable", voiceRecognition(named.doc.tetelek.find((item) => item.utvonal === samplePath).esemeny) === "hianyzik");
  const nameless = voiceManualUpsert(manual, samplePath, { esemeny: "egyeb", sajat_nev: "  ", sorrend: 0, elozo: "" });
  check("custom-needs-name", nameless.error !== "" && nameless.doc === manual);
  const mode = voiceManualSetMode(manual, "jobbra", "ugyanaz");
  check("mode-event", mode.doc.modok.jobbra === "ugyanaz" && mode.doc.alap_mod === manual.alap_mod);
  const globalMode = voiceManualSetMode(mode.doc, "", "veletlen");
  check("mode-global-clears", globalMode.doc.alap_mod === "veletlen" && !globalMode.doc.modok.jobbra);
  const played = voiceRowsBuild(catalog.hangok, grouped.doc);
  const queue = voiceQueueFrom(played);
  check("queue-skips-traffic", !queue.dugo);
  const approved = voiceManualUpsert(manual, samplePath, { esemeny: "jobbra", sorrend: 4, megjegyzes: "", elozo: "" });
  const approvedRows = voiceRowsBuild(catalog.hangok, approved.doc);
  const approvedQueue = voiceQueueFrom(approvedRows);
  check("queue-has-saved-right", (approvedQueue.jobbra || []).some((item) => item.utvonal === samplePath));
  const chain = voiceChainNext([
    { utvonal: "masodik.ogg", kezi_allapot: "mentve", kezi_esemeny: "masik_folytatas", kezi_elozo: samplePath, kezi_sorrend: 2 },
    { utvonal: "elso.ogg", kezi_allapot: "mentve", kezi_esemeny: "masik_folytatas", kezi_elozo: samplePath, kezi_sorrend: 1 },
    { utvonal: "mas.ogg", kezi_allapot: "mentve", kezi_esemeny: "masik_folytatas", kezi_elozo: "mas-fajl.ogg", kezi_sorrend: 1 }
  ], samplePath).map((item) => item.utvonal);
  check("chain-order", chain.join(",") === "elso.ogg,masodik.ogg", chain.join(","));
  const similarRows = [
    { utvonal: "a.ogg", leirat: "Menj el jobbra!", md5: "1" },
    { utvonal: "b.ogg", leirat: "Menj el jobbra!", md5: "2" },
    { utvonal: "c.ogg", leirat: "Más mondat teljesen", md5: "1" }
  ];
  const docBefore = JSON.stringify(similarRows);
  const similar = voiceSimilarPaths(similarRows, "a.ogg");
  check("similar-selects", similar.join(",") === "a.ogg,b.ogg");
  check("similar-does-not-save", JSON.stringify(similarRows) === docBefore);
  check("same-audio-separate", voiceSameAudioPaths(similarRows, "a.ogg").join(",") === "a.ogg,c.ogg");
  const overlay = voiceManualMerge(manual, { tetelek: [{ utvonal: samplePath, esemeny: "cel", allapot: "mentve", sajat_nev: "", megjegyzes: "", sorrend: 0, elozo: "" }], alap_mod: "ugyanaz", modok: {}, egyeb_esemenyek: [] });
  check("merge-keeps-path", overlay.tetelek.find((item) => item.utvonal === samplePath).esemeny === "cel");
  check("merge-ignores-unknown", voiceManualMerge(manual, { tetelek: [{ utvonal: "nincs.ogg", esemeny: "cel", allapot: "mentve" }] }).tetelek.length === 697);
`);
harness(check, manual, catalog, eventIds);
console.log(fails.length ? "VOICE-MANUAL-FAIL " + fails.length : "VOICE-MANUAL-OK");
if (fails.length) process.exitCode = 1;
