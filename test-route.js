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

assert.strictEqual(nav.addressLabel({ street: "Kossuth utca", housenumber: "1", city: "Szeged" }), "Kossuth utca 1, Szeged");
assert.strictEqual(nav.addressLabel({ name: "Dóm tér", city: "Szeged" }), "Dóm tér, Szeged");
const photon = nav.photonUrl("Kossuth utca, Szeged");
assert.ok(photon.indexOf("lang=") === -1, "a Photon cím ne kérjen hu nyelvet");
assert.ok(photon.indexOf("bbox=16.10,45.74,22.90,48.58") !== -1);
const osrm = nav.osrmUrl({ lon: 19.04032, lat: 47.49791 }, { lon: 20.14826, lat: 46.25301 });
assert.ok(osrm.indexOf("19.04032,47.49791;20.14826,46.25301") !== -1);
assert.ok(osrm.indexOf("overview=full") !== -1);

const drawn = nav.routeFromGeometry("budapest", { label: "Kossuth utca, Szeged" }, [
  [19.04, 47.50],
  [19.05, 47.50],
  [19.05, 47.49],
], [
  { maneuver: { type: "depart", modifier: "straight" }, name: "Induló utca", distance: 800 },
  { maneuver: { type: "turn", modifier: "right" }, name: "Kossuth utca", distance: 900 },
  { maneuver: { type: "arrive" }, name: "", distance: 0 },
], 1700, 5400);
assert.ok(Math.abs(drawn.totalKm - 1.7) < 0.001, "a táv az OSRM válaszból jön");
assert.strictEqual(drawn.durationHours, 1.5);
assert.strictEqual(drawn.addressLabel, "Kossuth utca, Szeged");
assert.strictEqual(drawn.points[0].road, "Induló utca");
assert.ok(drawn.points[drawn.points.length - 1].road === "Kossuth utca");
assert.ok(drawn.maneuvers.some(function (item) { return item.action === "right" && item.text.indexOf("Jobbra") === 0; }));
assert.strictEqual(drawn.maneuvers[drawn.maneuvers.length - 1].action, "arrive");
assert.strictEqual(nav.routeFromGeometry("budapest", { label: "X" }, [[19, 47]], [], 0, 0).points.length, 1);

const ranked = nav.rankAddresses([
  { properties: { name: "Kossuth Lajos Általános Iskola", street: "Szerb utca", city: "Szeged", osm_key: "amenity", countrycode: "HU" } },
  { properties: { name: "Kossuth Lajos sugárút", city: "Szeged", osm_key: "highway", osm_value: "primary", countrycode: "HU" } },
], "Kossuth Lajos sugárút, Szeged");
assert.strictEqual(nav.addressLabel(ranked[0].properties), "Kossuth Lajos sugárút, Szeged");

const settlements = nav.rankAddresses([
  { properties: { name: "Indóház tér", city: "Szeged", osm_key: "railway", osm_value: "station" } },
  { properties: { name: "Szeged", osm_key: "place", osm_value: "city", county: "Csongrád-Csanád megye" } },
], "Szeged");
assert.strictEqual(nav.addressLabel(settlements[0].properties), "Szeged");
assert.strictEqual(nav.addressLabel({ name: "Tiszasziget", osm_key: "place", osm_value: "village", county: "Csongrád-Csanád megye" }), "Tiszasziget, Csongrád-Csanád megye");

const houses = nav.rankAddresses([
  { properties: { name: "Váci utca", city: "Budapest", osm_key: "historic", osm_value: "memorial" } },
  { properties: { street: "Váci utca", housenumber: "12", city: "Budapest", osm_key: "building", osm_value: "yes" } },
], "Váci utca 12, Budapest");
assert.strictEqual(nav.addressLabel(houses[0].properties), "Váci utca 12, Budapest");

const wrongTown = [
  { properties: { name: "Határ utca", city: "Gyömrő", district: "Dózsatanya", postcode: "2230", osm_key: "highway", osm_value: "living_street" } },
  { properties: { street: "Határ utca", housenumber: "27", city: "Győr", postcode: "9019", osm_key: "building", osm_value: "yes" } },
  { properties: { street: "Kossuth Ferenc utca", housenumber: "27", city: "Gyömrő", postcode: "2230", osm_key: "building", osm_value: "detached" } },
];
["Gyömrő, Határ utca 27", "Határ utca 27, Gyömrő", "2230 Határ utca 27"].forEach(function (query) {
  const hits = nav.rankAddresses(wrongTown, query);
  assert.ok(hits.length === 1, query + " találatok: " + hits.length);
  assert.ok(hits.missingHouseNumber, query + " ne fogadja el a győri házszámot");
  assert.strictEqual(nav.addressLabel(hits[0].properties), "Határ utca, Dózsatanya, Gyömrő, 2230");
});
const realHouse = nav.rankAddresses(wrongTown, "Kossuth Ferenc utca 27, Gyömrő");
assert.strictEqual(nav.addressLabel(realHouse[0].properties), "Kossuth Ferenc utca 27, Gyömrő, 2230");
assert.ok(!realHouse.missingHouseNumber);
const accentless = nav.rankAddresses(wrongTown, "Gyomro, Hatar utca 27");
assert.strictEqual(nav.addressLabel(accentless[0].properties), "Határ utca, Dózsatanya, Gyömrő, 2230");

console.log("útvonalteszt rendben");
