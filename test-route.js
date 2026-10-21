const assert = require("assert");
const nav = require("./sim.js");

const cities = Object.keys(nav.PLACES).filter(function (id) { return nav.PLACES[id].city; });
const outside = cities.filter(function (id) {
  const place = nav.PLACES[id];
  return !nav.pointInPoly(place.lon, place.lat, nav.HUNGARY);
});
assert.deepStrictEqual(outside, [], "város az ország körvonalán kívül");

cities.forEach(function (id) {
  if (id === "budapest") return;
  const route = nav.planRoute("budapest", id);
  assert.ok(route, id + " nem elérhető");
  assert.ok(route.totalKm > 15 && route.totalKm < 650, id + " táv " + route.totalKm);
  console.log(nav.PLACES[id].name.padEnd(18, " "), route.totalKm.toFixed(0).padStart(4, " "), "km  ", route.roads.join(" → "));
});

const gyor = nav.planRoute("budapest", "gyor");
assert.ok(gyor.roads.indexOf("M1") === 0, "Győr az M1-en indul");
assert.ok(gyor.totalKm > 90 && gyor.totalKm < 150, "Győr táv " + gyor.totalKm);

const pecs = nav.planRoute("budapest", "pecs");
assert.ok(pecs.roads.indexOf("M6") !== -1, "Pécs érinti az M6-ot");
const debrecen = nav.planRoute("budapest", "debrecen");
assert.ok(debrecen.roads.indexOf("M3") !== -1, "Debrecen az autópályát használja: " + debrecen.roads.join(","));

const same = nav.planRoute("budapest", "budapest");
assert.strictEqual(same, null);

console.log("útvonalteszt rendben");
