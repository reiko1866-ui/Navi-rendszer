(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (typeof document !== "undefined") api.boot();
  else root.RajzNav = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  const KM_PER_DEG_LAT = 111.32;
  const KM_PER_DEG_LON = 111.32 * Math.cos((47.2 * Math.PI) / 180);
  const PX_PER_KM = 6;

  const PLACES = {
    sopron: { name: "Sopron", lon: 16.59, lat: 47.68, city: true },
    szombathely: { name: "Szombathely", lon: 16.62, lat: 47.23, city: true },
    csorna: { name: "Csorna", lon: 17.25, lat: 47.61, city: true },
    gyor: { name: "Győr", lon: 17.63, lat: 47.69, city: true },
    tatabanya: { name: "Tatabánya", lon: 18.39, lat: 47.59, city: true },
    zalaegerszeg: { name: "Zalaegerszeg", lon: 16.84, lat: 46.84, city: true },
    veszprem: { name: "Veszprém", lon: 17.91, lat: 47.09, city: true },
    siofok: { name: "Siófok", lon: 18.06, lat: 46.91, city: true },
    szfvar: { name: "Székesfehérvár", lon: 18.41, lat: 47.19, city: true },
    budapest: { name: "Budapest", lon: 19.04, lat: 47.5, city: true, capital: true },
    dunaujvaros: { name: "Dunaújváros", lon: 18.94, lat: 46.96, city: true },
    kaposvar: { name: "Kaposvár", lon: 17.8, lat: 46.36, city: true },
    pecs: { name: "Pécs", lon: 18.23, lat: 46.07, city: true },
    kecskemet: { name: "Kecskemét", lon: 19.69, lat: 46.91, city: true },
    szeged: { name: "Szeged", lon: 20.15, lat: 46.25, city: true },
    szolnok: { name: "Szolnok", lon: 20.2, lat: 47.18, city: true },
    bekescsaba: { name: "Békéscsaba", lon: 21.09, lat: 46.68, city: true },
    hatvan: { name: "Hatvan", lon: 19.68, lat: 47.67, city: true },
    fuzesabony: { name: "Füzesabony", lon: 20.42, lat: 47.75, city: false },
    eger: { name: "Eger", lon: 20.38, lat: 47.9, city: true },
    m30: { name: "M30", lon: 20.8, lat: 47.78, city: false },
    miskolc: { name: "Miskolc", lon: 20.78, lat: 48.1, city: true },
    polgar: { name: "Polgár", lon: 21.12, lat: 47.86, city: true },
    nyiregyhaza: { name: "Nyíregyháza", lon: 21.72, lat: 47.96, city: true },
    debrecen: { name: "Debrecen", lon: 21.63, lat: 47.53, city: true },
  };

  const ROADS = [
    { road: "M1", limit: 80, kind: "motorway", pts: ["budapest", { lon: 18.78, lat: 47.48 }, "tatabanya", { lon: 18.05, lat: 47.75 }, "gyor", "csorna"] },
    { road: "85-ös", limit: 70, kind: "main", pts: ["csorna", { lon: 16.95, lat: 47.66 }, "sopron"] },
    { road: "86-os", limit: 70, kind: "main", pts: ["csorna", { lon: 16.95, lat: 47.4 }, "szombathely"] },
    { road: "84-es", limit: 70, kind: "main", pts: ["sopron", { lon: 16.55, lat: 47.45 }, "szombathely"] },
    { road: "M7", limit: 80, kind: "motorway", pts: ["budapest", { lon: 18.75, lat: 47.42 }, "szfvar", { lon: 18.22, lat: 47.02 }, "siofok"] },
    { road: "8-as", limit: 70, kind: "main", pts: ["szfvar", "veszprem", { lon: 17.35, lat: 47.0 }, "zalaegerszeg", { lon: 16.7, lat: 47.05 }, "szombathely"] },
    { road: "65-ös", limit: 70, kind: "main", pts: ["siofok", { lon: 17.95, lat: 46.6 }, "kaposvar"] },
    { road: "66-os", limit: 70, kind: "main", pts: ["kaposvar", { lon: 17.95, lat: 46.2 }, "pecs"] },
    { road: "M6", limit: 80, kind: "motorway", pts: ["budapest", { lon: 18.98, lat: 47.25 }, "dunaujvaros", { lon: 18.7, lat: 46.45 }, "pecs"] },
    { road: "M5", limit: 80, kind: "motorway", pts: ["budapest", { lon: 19.45, lat: 47.25 }, "kecskemet", { lon: 19.95, lat: 46.55 }, "szeged"] },
    { road: "M3", limit: 80, kind: "motorway", pts: ["budapest", { lon: 19.4, lat: 47.6 }, "hatvan", "fuzesabony", "m30", "polgar", "nyiregyhaza"] },
    { road: "M25", limit: 80, kind: "motorway", pts: ["fuzesabony", "eger"] },
    { road: "M30", limit: 80, kind: "motorway", pts: ["m30", "miskolc"] },
    { road: "M35", limit: 80, kind: "motorway", pts: ["polgar", { lon: 21.4, lat: 47.7 }, "debrecen"] },
    { road: "4-es", limit: 70, kind: "main", pts: ["budapest", { lon: 19.7, lat: 47.35 }, "szolnok", { lon: 20.9, lat: 47.35 }, "debrecen"] },
    { road: "44-es", limit: 70, kind: "main", pts: ["szolnok", { lon: 20.6, lat: 46.95 }, "bekescsaba"] },
    { road: "47-es", limit: 70, kind: "main", pts: ["bekescsaba", { lon: 20.7, lat: 46.45 }, "szeged"] },
    { road: "441-es", limit: 70, kind: "main", pts: ["kecskemet", { lon: 19.95, lat: 47.05 }, "szolnok"] },
  ];

  const HUNGARY = [
    [16.1139, 46.8691], [16.1561, 46.8537], [16.2332, 46.8767], [16.2905, 46.8729],
    [16.3403, 46.8469], [16.3508, 46.83], [16.3406, 46.8052], [16.3128, 46.7973],
    [16.3122, 46.778], [16.3306, 46.7752], [16.3186, 46.7541], [16.3571, 46.7142],
    [16.3798, 46.7154], [16.3689, 46.704], [16.4286, 46.694], [16.4199, 46.6585],
    [16.3915, 46.6637], [16.3859, 46.6442], [16.4456, 46.611], [16.483, 46.566],
    [16.5083, 46.5652], [16.5324, 46.5315], [16.5236, 46.5053], [16.6638, 46.4595],
    [16.6575, 46.4461], [16.674, 46.4482], [16.7187, 46.3916], [16.7342, 46.4011],
    [16.7591, 46.3779], [16.7932, 46.3871], [16.8374, 46.3741], [16.8799, 46.3363],
    [16.8863, 46.2807], [16.9525, 46.2401], [16.9732, 46.2427], [16.9731, 46.2248],
    [17.0663, 46.2021], [17.1254, 46.1684], [17.1586, 46.1698], [17.1561, 46.1587],
    [17.1807, 46.1506], [17.1779, 46.1071], [17.2147, 46.1143], [17.2095, 46.0993],
    [17.2334, 46.0977], [17.2016, 46.0775], [17.2322, 46.0801], [17.2518, 46.0644],
    [17.2314, 46.0604], [17.27, 46.0573], [17.2546, 46.03], [17.2947, 46.0311],
    [17.2578, 46.0121], [17.3044, 46.0025], [17.299, 45.9832], [17.3225, 45.9894],
    [17.3095, 45.9682], [17.3311, 45.9713], [17.3318, 45.9954], [17.3529, 45.9747],
    [17.3738, 45.9892], [17.3757, 45.9701], [17.3594, 45.9638], [17.3911, 45.9601],
    [17.3432, 45.9602], [17.3456, 45.9434], [17.3814, 45.9481], [17.3901, 45.9316],
    [17.4109, 45.9401], [17.4262, 45.9271], [17.4369, 45.9504], [17.5695, 45.936],
    [17.6267, 45.8987], [17.663, 45.8378], [17.8263, 45.8095], [17.8633, 45.767],
    [17.9062, 45.7922], [17.9964, 45.7951], [18.0827, 45.7643], [18.123, 45.7889],
    [18.1696, 45.7758], [18.1907, 45.7874], [18.2437, 45.761], [18.3391, 45.7469],
    [18.3642, 45.7729], [18.4076, 45.7397], [18.4469, 45.7371], [18.4451, 45.7605],
    [18.4821, 45.7655], [18.4907, 45.7947], [18.5223, 45.7827], [18.5749, 45.8003],
    [18.5728, 45.8136], [18.6232, 45.8395], [18.6148, 45.8529], [18.6275, 45.873],
    [18.655, 45.8742], [18.641, 45.8888], [18.665, 45.8994], [18.6595, 45.9169],
    [18.7048, 45.9181], [18.7911, 45.8782], [18.8089, 45.8795], [18.8216, 45.9147],
    [18.8682, 45.9113], [18.906, 45.9353], [19.0088, 45.9233], [19.0058, 45.9587],
    [19.0796, 45.9635], [19.0657, 46], [19.1046, 46.04], [19.1338, 46.0371],
    [19.1477, 45.9961], [19.2965, 45.9878], [19.2816, 46.0145], [19.3642, 46.0522],
    [19.3802, 46.0358], [19.4154, 46.0457], [19.4665, 46.0817], [19.4644, 46.0953],
    [19.5271, 46.1209], [19.5024, 46.1421], [19.5676, 46.1787], [19.6316, 46.1692],
    [19.6615, 46.19], [19.6981, 46.1879], [19.8178, 46.1278], [19.9351, 46.1762],
    [20.0158, 46.1768], [20.0344, 46.1458], [20.0637, 46.1437], [20.1013, 46.177],
    [20.1373, 46.1442], [20.1818, 46.16], [20.2549, 46.1158], [20.2968, 46.1522],
    [20.3556, 46.1697], [20.3684, 46.1529], [20.4591, 46.1429], [20.5015, 46.1899],
    [20.636, 46.1265], [20.6538, 46.1488], [20.687, 46.1456], [20.715, 46.1668],
    [20.7341, 46.1938], [20.7274, 46.2074], [20.7618, 46.2045], [20.7491, 46.2508],
    [20.7756, 46.276], [20.8733, 46.2878], [20.9222, 46.2618], [20.9251, 46.2766],
    [20.9466, 46.2793], [20.9608, 46.2623], [21.0247, 46.2665], [21.0366, 46.248],
    [21.0661, 46.2429], [21.1032, 46.2626], [21.1155, 46.3017], [21.1806, 46.3041],
    [21.1762, 46.3358], [21.1995, 46.3479], [21.2063, 46.4032], [21.2963, 46.4067],
    [21.3178, 46.4507], [21.2604, 46.502], [21.2742, 46.5407], [21.3208, 46.5828],
    [21.3011, 46.5908], [21.3141, 46.6176], [21.3656, 46.6378], [21.41, 46.6218],
    [21.4164, 46.6425], [21.4549, 46.6612], [21.4301, 46.6939], [21.4922, 46.686],
    [21.5293, 46.7208], [21.5267, 46.7392], [21.4832, 46.7647], [21.5187, 46.7999],
    [21.5192, 46.8359], [21.5968, 46.8613], [21.6142, 46.8865], [21.5987, 46.9275],
    [21.6788, 46.9604], [21.6685, 46.9728], [21.6889, 47.0018], [21.6487, 47.0404],
    [21.6826, 47.0457], [21.7261, 47.0985], [21.7923, 47.1063], [21.8175, 47.1728],
    [21.858, 47.187], [21.8518, 47.2392], [21.8879, 47.2721], [21.8787, 47.2874],
    [21.9385, 47.373], [22.0119, 47.3757], [22.0223, 47.3897], [22.0328, 47.451],
    [22.0071, 47.4833], [22.0242, 47.52], [22.0455, 47.5398], [22.0615, 47.529],
    [22.0714, 47.538], [22.0553, 47.5485], [22.0937, 47.5581], [22.1282, 47.598],
    [22.1766, 47.5944], [22.2262, 47.688], [22.2589, 47.6981], [22.2645, 47.7312],
    [22.3176, 47.7434], [22.3178, 47.7661], [22.4302, 47.7409], [22.4479, 47.8031],
    [22.4812, 47.8109], [22.5495, 47.7727], [22.6116, 47.772], [22.6802, 47.7878],
    [22.7136, 47.8361], [22.7778, 47.8422], [22.7581, 47.8934], [22.8473, 47.9078],
    [22.8964, 47.9528], [22.893, 47.9668], [22.8362, 47.9904], [22.8656, 48.012],
    [22.8819, 48.054], [22.8364, 48.0804], [22.8242, 48.1175], [22.8023, 48.1071],
    [22.8028, 48.1219], [22.7716, 48.1217], [22.7704, 48.1089], [22.7347, 48.1196],
    [22.673, 48.0918], [22.5903, 48.1076], [22.5983, 48.1454], [22.5614, 48.1817],
    [22.5705, 48.196], [22.5312, 48.2094], [22.4893, 48.2538], [22.4571, 48.2414],
    [22.4009, 48.2491], [22.3845, 48.2341], [22.3387, 48.279], [22.337, 48.3075],
    [22.3127, 48.3264], [22.3178, 48.3541], [22.2675, 48.361], [22.2399, 48.387],
    [22.2653, 48.4098], [22.213, 48.4254], [22.1572, 48.4101], [22.1311, 48.3912],
    [22.1359, 48.3805], [22.0867, 48.3716], [22.0765, 48.3872], [22.0546, 48.3775],
    [22.0213, 48.3927], [21.9282, 48.3616], [21.8372, 48.3633], [21.8352, 48.3346],
    [21.8174, 48.3328], [21.7119, 48.3576], [21.7017, 48.3807], [21.6646, 48.3922],
    [21.6139, 48.5094], [21.542, 48.5084], [21.5141, 48.5511], [21.4406, 48.5851],
    [21.4154, 48.559], [21.3194, 48.5612], [21.3055, 48.5222], [21.2211, 48.5375],
    [21.1173, 48.4911], [21.0663, 48.5259], [20.9562, 48.5216], [20.9223, 48.5594],
    [20.8682, 48.5518], [20.8364, 48.5828], [20.6539, 48.5614], [20.5866, 48.5358],
    [20.5465, 48.5443], [20.5375, 48.5279], [20.5065, 48.5344], [20.508, 48.4894],
    [20.4167, 48.419], [20.4098, 48.3659], [20.3375, 48.3017], [20.3257, 48.2728],
    [20.2869, 48.2616], [20.235, 48.2799], [20.2062, 48.251], [20.1332, 48.254],
    [20.1341, 48.2252], [20.07, 48.1917], [20.073, 48.1796], [19.9744, 48.166],
    [19.9374, 48.1311], [19.8983, 48.1249], [19.9145, 48.1469], [19.855, 48.1784],
    [19.8453, 48.1627], [19.8213, 48.1691], [19.7948, 48.1535], [19.7824, 48.165],
    [19.8053, 48.1837], [19.7987, 48.1948], [19.7462, 48.2165], [19.6912, 48.2039],
    [19.6699, 48.2392], [19.6308, 48.2501], [19.526, 48.2031], [19.5045, 48.1734],
    [19.5128, 48.1547], [19.4924, 48.1397], [19.4944, 48.1099], [19.4673, 48.0839],
    [19.454, 48.1014], [19.4, 48.0823], [19.3031, 48.0887], [19.2414, 48.0536],
    [19.1353, 48.0742], [19.0585, 48.0573], [19.0149, 48.0782], [18.9816, 48.0536],
    [18.8333, 48.0482], [18.82, 48.0397], [18.8157, 47.9934], [18.755, 47.9758],
    [18.7767, 47.9554], [18.7567, 47.8976], [18.7664, 47.8715], [18.8559, 47.8261],
    [18.7414, 47.8139], [18.7238, 47.7877], [18.6608, 47.7608], [18.4925, 47.7526],
    [18.453, 47.7651], [18.2901, 47.7312], [18.0858, 47.7565], [17.7799, 47.7395],
    [17.7086, 47.7567], [17.6084, 47.8219], [17.5676, 47.8151], [17.5267, 47.8655],
    [17.4539, 47.8853], [17.403, 47.9479], [17.3346, 47.9931], [17.258, 47.9986],
    [17.2418, 48.0225], [17.201, 48.02], [17.0947, 47.9709], [17.117, 47.9609],
    [17.0909, 47.9344], [17.1127, 47.9274], [17.0852, 47.8744], [17.0099, 47.8584],
    [17.0744, 47.8083], [17.051, 47.794], [17.0703, 47.7282], [17.0931, 47.7082],
    [16.8752, 47.6883], [16.8669, 47.7216], [16.8283, 47.6827], [16.749, 47.6814],
    [16.7209, 47.7353], [16.6095, 47.7604], [16.547, 47.751], [16.5365, 47.7366],
    [16.5522, 47.7225], [16.4747, 47.6813], [16.4473, 47.6963], [16.4236, 47.6631],
    [16.5725, 47.6198], [16.6521, 47.6229], [16.6731, 47.605], [16.6646, 47.5663],
    [16.7145, 47.5399], [16.6898, 47.5104], [16.6523, 47.5003], [16.6705, 47.4743],
    [16.662, 47.4557], [16.5757, 47.4064], [16.5169, 47.4106], [16.4966, 47.389],
    [16.4853, 47.4089], [16.4455, 47.4072], [16.4614, 47.3835], [16.4523, 47.3571],
    [16.4337, 47.3528], [16.4645, 47.3339], [16.4894, 47.2803], [16.4671, 47.2529],
    [16.4318, 47.2529], [16.4428, 47.2337], [16.4195, 47.1949], [16.4549, 47.1874],
    [16.4543, 47.1423], [16.5168, 47.1493], [16.5293, 47.1272], [16.4633, 47.095],
    [16.4729, 47.0736], [16.5206, 47.0562], [16.4389, 47.0297], [16.4637, 47.0322],
    [16.5123, 47.0016], [16.4323, 46.9927], [16.3498, 47.0104], [16.3418, 46.9962],
    [16.3034, 46.9983], [16.2901, 47.014], [16.2764, 46.9626], [16.2494, 46.9613],
    [16.222, 46.9356], [16.1999, 46.9418], [16.1792, 46.9066],
  ];

  const DANUBE = [
    [18.55, 47.95], [18.85, 47.75], [19.0, 47.6], [19.05, 47.49], [18.99, 47.25],
    [18.9, 46.95], [18.8, 46.6], [18.85, 46.25], [18.7, 45.95],
  ];

  const TISZA = [
    [21.4, 48.4], [21.6, 48.1], [21.35, 47.9], [21.0, 47.7], [20.55, 47.4],
    [20.2, 47.18], [20.25, 46.85], [20.15, 46.4], [20.15, 46.25],
  ];

  const BALATON = [
    [17.25, 46.72], [17.55, 46.8], [17.9, 46.88], [18.15, 46.92], [18.32, 46.86],
    [18.1, 46.78], [17.7, 46.7], [17.35, 46.68],
  ];

  const FORESTS = [
    { lon: 17.7, lat: 47.2, rx: 36, ry: 22, name: "Bakony" },
    { lon: 20.55, lat: 48.05, rx: 30, ry: 16, name: "Bükk" },
    { lon: 18.22, lat: 46.18, rx: 24, ry: 14, name: "Mecsek" },
    { lon: 18.75, lat: 46.4, rx: 16, ry: 24, name: "Gemenc" },
    { lon: 16.45, lat: 46.95, rx: 22, ry: 16, name: "Őrség" },
  ];

  function project(lon, lat) {
    return {
      x: lon * KM_PER_DEG_LON * PX_PER_KM,
      y: -lat * KM_PER_DEG_LAT * PX_PER_KM,
    };
  }

  function haversine(a, b) {
    const r = 6371;
    const dLat = ((b.lat - a.lat) * Math.PI) / 180;
    const dLon = ((b.lon - a.lon) * Math.PI) / 180;
    const la1 = (a.lat * Math.PI) / 180;
    const la2 = (b.lat * Math.PI) / 180;
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) ** 2;
    return 2 * r * Math.asin(Math.min(1, Math.sqrt(h)));
  }

  function lonLatOf(point) {
    return typeof point === "string" ? PLACES[point] : point;
  }

  function mulberry32(seed) {
    let a = seed >>> 0;
    return function () {
      a |= 0;
      a = (a + 0x6d2b79f5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function pointInPoly(lon, lat, poly) {
    let inside = false;
    for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
      const xi = poly[i][0];
      const yi = poly[i][1];
      const xj = poly[j][0];
      const yj = poly[j][1];
      const hit = yi > lat !== yj > lat && lon < ((xj - xi) * (lat - yi)) / (yj - yi + 1e-12) + xi;
      if (hit) inside = !inside;
    }
    return inside;
  }

  function buildGraph() {
    const adj = {};
    Object.keys(PLACES).forEach(function (id) { adj[id] = []; });
    const drawn = [];

    ROADS.forEach(function (road) {
      const chunks = [];
      let current = [];
      road.pts.forEach(function (point) {
        current.push(point);
        if (typeof point === "string" && current.length > 1) {
          chunks.push(current);
          current = [point];
        }
      });
      chunks.forEach(function (chunk) {
        const a = chunk[0];
        const b = chunk[chunk.length - 1];
        let km = 0;
        for (let i = 1; i < chunk.length; i++) km += haversine(lonLatOf(chunk[i - 1]), lonLatOf(chunk[i]));
        const edge = { a: a, b: b, road: road.road, limit: road.limit, kind: road.kind, via: chunk.slice(1, -1), km: km };
        const hours = km / road.limit;
        adj[a].push({ to: b, edge: edge, km: km, hours: hours });
        adj[b].push({ to: a, edge: edge, km: km, hours: hours });
        drawn.push(edge);
      });
    });
    return { adj: adj, edges: drawn };
  }

  const GRAPH = buildGraph();

  function geometry(edge, fromId) {
    const forward = edge.a === fromId;
    const raw = forward ? [edge.a].concat(edge.via, [edge.b]) : [edge.b].concat(edge.via.slice().reverse(), [edge.a]);
    return raw.map(lonLatOf);
  }

  function planRoute(fromId, toId) {
    if (!PLACES[fromId] || !PLACES[toId] || fromId === toId) return null;
    const dist = {};
    const prev = {};
    const prevEdge = {};
    const queue = new Set(Object.keys(PLACES));
    queue.forEach(function (id) { dist[id] = Infinity; });
    dist[fromId] = 0;

    while (queue.size) {
      let best = null;
      queue.forEach(function (id) {
        if (best === null || dist[id] < dist[best]) best = id;
      });
      if (best === null || dist[best] === Infinity) break;
      queue.delete(best);
      if (best === toId) break;
      GRAPH.adj[best].forEach(function (link) {
        if (!queue.has(link.to)) return;
        const alt = dist[best] + link.hours;
        if (alt < dist[link.to]) {
          dist[link.to] = alt;
          prev[link.to] = best;
          prevEdge[link.to] = link.edge;
        }
      });
    }
    if (dist[toId] === Infinity) return null;

    const edgeList = [];
    let cursor = toId;
    while (cursor !== fromId) {
      edgeList.push({ edge: prevEdge[cursor], from: prev[cursor] });
      cursor = prev[cursor];
    }
    edgeList.reverse();

    const points = [];
    let km = 0;
    edgeList.forEach(function (step) {
      const geos = geometry(step.edge, step.from);
      geos.forEach(function (geo, index) {
        if (points.length && index === 0) return;
        const previous = points[points.length - 1];
        if (previous) km += haversine(previous, geo);
        const xy = project(geo.lon, geo.lat);
        const nodeId = index === geos.length - 1 ? (step.edge.a === step.from ? step.edge.b : step.edge.a) : (index === 0 ? step.from : null);
        points.push({
          lon: geo.lon,
          lat: geo.lat,
          x: xy.x,
          y: xy.y,
          km: km,
          road: step.edge.road,
          limit: step.edge.limit,
          kind: step.edge.kind,
          nodeId: nodeId,
        });
      });
    });
    if (points.length) points[0].nodeId = fromId;

    const maneuvers = [];
    for (let i = 1; i < points.length - 1; i++) {
      const point = points[i];
      if (!point.nodeId) continue;
      const before = points[i - 1];
      const after = points[i + 1];
      const inHeading = Math.atan2(point.y - before.y, point.x - before.x);
      const outHeading = Math.atan2(after.y - point.y, after.x - point.x);
      let delta = outHeading - inHeading;
      while (delta > Math.PI) delta -= Math.PI * 2;
      while (delta < -Math.PI) delta += Math.PI * 2;
      const roadChange = before.road !== after.road;
      const sharp = Math.abs(delta) > 0.45;
      if (!roadChange && !sharp) continue;
      const place = PLACES[point.nodeId];
      const placeName = place && place.city ? place.name : "";
      let action = "straight";
      let text = roadChange ? "Tovább: " + after.road : "Egyenesen";
      if (delta > 0.45) {
        action = "right";
        text = placeName ? "Jobbra · " + placeName : "Jobbra, " + after.road;
      } else if (delta < -0.45) {
        action = "left";
        text = placeName ? "Balra · " + placeName : "Balra, " + after.road;
      } else if (placeName) {
        text = "Egyenesen · " + placeName;
      }
      maneuvers.push({
        km: point.km,
        action: action,
        text: text,
        road: after.road,
        place: placeName,
        nodeId: point.nodeId,
      });
    }
    maneuvers.push({
      km: km,
      action: "arrive",
      text: "Érkezés · " + PLACES[toId].name,
      road: points.length ? points[points.length - 1].road : "",
      place: PLACES[toId].name,
      nodeId: toId,
    });

    const roads = [];
    points.forEach(function (point) {
      if (point.road && roads[roads.length - 1] !== point.road) roads.push(point.road);
    });

    return {
      fromId: fromId,
      toId: toId,
      points: points,
      totalKm: km,
      maneuvers: maneuvers,
      roads: roads,
    };
  }

  function citiesInsideCountry() {
    return Object.keys(PLACES).filter(function (id) {
      const place = PLACES[id];
      return place.city && pointInPoly(place.lon, place.lat, HUNGARY);
    });
  }

  function buildScenery() {
    const land = HUNGARY.map(function (pair) {
      const xy = project(pair[0], pair[1]);
      return { x: xy.x, y: xy.y };
    });
    const river = function (line) {
      return line.map(function (pair) {
        const xy = project(pair[0], pair[1]);
        return { x: xy.x, y: xy.y };
      });
    };
    const lake = river(BALATON);
    const rnd = mulberry32(1866);
    const patches = [];
    const bounds = boundsOf(land);
    for (let i = 0; i < 46; i++) {
      const lon = 16.2 + rnd() * 6.4;
      const lat = 46.0 + rnd() * 2.4;
      if (!pointInPoly(lon, lat, HUNGARY)) continue;
      const xy = project(lon, lat);
      const colors = ["#c5d48a", "#d9c98a", "#a9c47a", "#e0d2a0", "#8fb56e"];
      patches.push({
        x: xy.x,
        y: xy.y,
        rx: (18 + rnd() * 38) * PX_PER_KM,
        ry: (14 + rnd() * 26) * PX_PER_KM,
        rot: rnd() * Math.PI,
        color: colors[i % colors.length],
      });
    }
    const trees = [];
    FORESTS.forEach(function (forest, forestIndex) {
      const rndForest = mulberry32(40 + forestIndex * 17);
      for (let i = 0; i < 16; i++) {
        const angle = rndForest() * Math.PI * 2;
        const radius = Math.sqrt(rndForest());
        const lon = forest.lon + (Math.cos(angle) * radius * forest.rx) / KM_PER_DEG_LON;
        const lat = forest.lat + (Math.sin(angle) * radius * forest.ry) / KM_PER_DEG_LAT;
        const xy = project(lon, lat);
        trees.push({ x: xy.x, y: xy.y, s: 0.7 + rndForest() * 0.8 });
      }
    });
    return { land: land, danube: river(DANUBE), tisza: river(TISZA), lake: lake, patches: patches, trees: trees, bounds: bounds };
  }

  function boundsOf(points) {
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    points.forEach(function (point) {
      minX = Math.min(minX, point.x);
      minY = Math.min(minY, point.y);
      maxX = Math.max(maxX, point.x);
      maxY = Math.max(maxY, point.y);
    });
    return { minX: minX, minY: minY, maxX: maxX, maxY: maxY, cx: (minX + maxX) / 2, cy: (minY + maxY) / 2 };
  }

  const SCENERY = buildScenery();

  function roadPolylines() {
    return GRAPH.edges.map(function (edge) {
      const geos = geometry(edge, edge.a);
      return {
        kind: edge.kind,
        road: edge.road,
        points: geos.map(function (geo) { return project(geo.lon, geo.lat); }),
      };
    });
  }

  const ROAD_LINES = roadPolylines();

  function formatDuration(hours) {
    const minutes = Math.max(0, Math.round(hours * 60));
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    if (h <= 0) return m + " perc";
    return h + " óra " + m + " perc";
  }

  function formatClock(simMinutes) {
    const minutes = ((Math.floor(simMinutes) % (24 * 60)) + 24 * 60) % (24 * 60);
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return String(h).padStart(2, "0") + ":" + String(m).padStart(2, "0");
  }

  function remainingHours(route, traveledKm) {
    if (!route) return 0;
    let hours = 0;
    for (let i = 1; i < route.points.length; i++) {
      const a = route.points[i - 1];
      const b = route.points[i];
      const start = Math.max(a.km, traveledKm);
      const end = b.km;
      if (end <= start) continue;
      hours += (end - start) / b.limit;
    }
    if (route.durationHours != null && route.totalKm > 0) {
      const remain = Math.max(0, route.totalKm - traveledKm);
      return route.durationHours * (remain / route.totalKm);
    }
    return hours;
  }

  function sampleRoute(route, km) {
    if (!route || !route.points.length) return null;
    const clamped = Math.max(0, Math.min(route.totalKm, km));
    const points = route.points;
    let index = 1;
    while (index < points.length - 1 && points[index].km < clamped) index += 1;
    const b = points[index];
    const a = points[index - 1];
    const span = Math.max(0.0001, b.km - a.km);
    const t = (clamped - a.km) / span;
    const heading = Math.atan2(b.y - a.y, b.x - a.x);
    return {
      x: a.x + (b.x - a.x) * t,
      y: a.y + (b.y - a.y) * t,
      heading: heading,
      road: b.road,
      limit: b.limit,
      kind: b.kind,
      km: clamped,
    };
  }

  function nextManeuver(route, traveledKm) {
    if (!route) return null;
    for (let i = 0; i < route.maneuvers.length; i++) {
      if (route.maneuvers[i].km > traveledKm + 0.03) return route.maneuvers[i];
    }
    return route.maneuvers[route.maneuvers.length - 1];
  }

  function photonUrl(query) {
    return "https://photon.komoot.io/api/?limit=40&bbox=16.10,45.74,22.90,48.58&q=" + encodeURIComponent(query.trim());
  }

  function osrmUrl(origin, destination) {
    return "https://routing.openstreetmap.de/routed-car/route/v1/driving/"
      + origin.lon.toFixed(5) + "," + origin.lat.toFixed(5) + ";"
      + destination.lon.toFixed(5) + "," + destination.lat.toFixed(5)
      + "?overview=full&geometries=geojson&steps=true";
  }

  function foldHu(text) {
    return String(text || "").toLocaleLowerCase("hu")
      .replace(/á/g, "a").replace(/é/g, "e").replace(/í/g, "i")
      .replace(/ó/g, "o").replace(/ö/g, "o").replace(/ő/g, "o")
      .replace(/ú/g, "u").replace(/ü/g, "u").replace(/ű/g, "u");
  }

  function wordsOf(text) {
    return foldHu(text).split(/[^a-z0-9]+/).filter(Boolean);
  }

  function hasWord(text, word) {
    return wordsOf(text).indexOf(word) !== -1;
  }

  function normNumber(value) {
    return foldHu(value).replace(/[^a-z0-9]/g, "");
  }

  function parseAddressQuery(query) {
    const folded = foldHu(query);
    const postcodeMatch = folded.match(/\b\d{4}\b/);
    const postcode = postcodeMatch ? postcodeMatch[0] : "";
    const withoutPost = folded.replace(/\b\d{4}\b/g, " ");
    const streetMatch = withoutPost.match(/\b([a-z0-9]+(?:\s+[a-z0-9]+)*)\s+(sugarut|setany|korut|rakpart|utca|dulo|liget|park|koz|sor|ter|krt|ut)\b/);
    let streetTokens = [];
    let streetKind = "";
    let rest = withoutPost;
    if (streetMatch) {
      streetKind = streetMatch[2];
      streetTokens = streetMatch[1].split(/\s+/).filter(Boolean);
      rest = withoutPost.replace(streetMatch[0], " ");
    }
    const houseNumbers = (rest.match(/\b\d+[a-z]?\b/g) || []).map(normNumber).filter(function (value) { return value && value !== postcode; });
    rest = rest.replace(/\b\d+[a-z]?\b/g, " ");
    const placeTokens = rest.split(/[^a-z0-9]+/).filter(function (token) { return token.length > 1; });
    return { postcode: postcode, streetTokens: streetTokens, streetKind: streetKind, houseNumbers: houseNumbers, placeTokens: placeTokens };
  }

  function fieldHasToken(field, token) {
    if (!field) return false;
    if (field === token) return true;
    return wordsOf(field).indexOf(token) !== -1;
  }

  function placeMatches(props, parsed) {
    const city = foldHu(props.city);
    const locality = foldHu(props.locality);
    const name = foldHu(props.name);
    if (parsed.postcode && String(props.postcode || "") !== parsed.postcode) return false;
    if (!parsed.placeTokens.length) return true;
    return parsed.placeTokens.every(function (token) {
      return fieldHasToken(city, token) || fieldHasToken(locality, token) || fieldHasToken(name, token);
    });
  }

  function streetMatches(props, parsed) {
    if (!parsed.streetTokens.length) return true;
    const blob = [props.street, props.name].filter(Boolean).join(" ");
    const words = wordsOf(blob);
    const namesOk = parsed.streetTokens.every(function (token) { return words.indexOf(token) !== -1; });
    if (!namesOk) return false;
    if (!parsed.streetKind) return true;
    return words.indexOf(parsed.streetKind) !== -1;
  }

  function rankAddresses(features, query) {
    const parsed = parseAddressQuery(query);
    const folded = foldHu(query);
    const tokens = folded.split(/[^a-z0-9]+/).filter(function (token) { return token.length > 2; });
    let missingHouseNumber = false;
    let pool = features.filter(function (feature) {
      const props = feature.properties || {};
      return placeMatches(props, parsed) && streetMatches(props, parsed);
    });
    if (parsed.houseNumbers.length) {
      const numbered = pool.filter(function (feature) {
        const house = normNumber((feature.properties || {}).housenumber || "");
        return parsed.houseNumbers.indexOf(house) !== -1;
      });
      if (numbered.length) pool = numbered;
      else if (pool.length) missingHouseNumber = true;
    }
    const wantsStreet = !!parsed.streetKind;
    const ranked = pool.map(function (feature, index) {
      const props = feature.properties || {};
      const name = foldHu(props.name);
      const street = foldHu(props.street);
      const city = foldHu(props.city);
      let score = 0;
      tokens.forEach(function (token) {
        if (name === token) score += 18;
        else if (hasWord(name, token)) score += 6;
        if (hasWord(street, token)) score += 4;
        if (hasWord(city, token)) score += 8;
      });
      const settlement = props.osm_key === "place" && (props.osm_value === "city" || props.osm_value === "town" || props.osm_value === "village" || props.osm_value === "hamlet");
      if (settlement && parsed.placeTokens.some(function (token) { return name === token; })) score += 16;
      if (parsed.houseNumbers.indexOf(normNumber(props.housenumber || "")) !== -1) score += 22;
      if (parsed.postcode && String(props.postcode || "") === parsed.postcode) score += 10;
      if (wantsStreet && props.osm_key === "highway") score += 8;
      if (wantsStreet && (props.osm_key === "amenity" || props.osm_key === "shop" || props.osm_key === "tourism" || props.osm_key === "historic")) score -= 3;
      return { feature: feature, score: score, index: index };
    });
    ranked.sort(function (a, b) {
      if (b.score !== a.score) return b.score - a.score;
      return a.index - b.index;
    });
    const seen = {};
    const picked = [];
    ranked.forEach(function (item) {
      if (picked.length >= 6) return;
      const label = addressLabel(item.feature.properties || {});
      if (seen[label]) return;
      seen[label] = true;
      picked.push(item.feature);
    });
    picked.missingHouseNumber = missingHouseNumber;
    return picked;
  }

  function addressLabel(properties) {
    const kind = properties.osm_value;
    if (properties.osm_key === "place" && properties.name && (kind === "city" || kind === "town" || kind === "village" || kind === "hamlet" || kind === "suburb")) {
      if (kind === "city" || kind === "town") return properties.name;
      const where = properties.city || properties.county || properties.state || "";
      if (where && foldHu(where) !== foldHu(properties.name)) return properties.name + ", " + where;
      return properties.name;
    }
    const street = properties.street || "";
    const number = properties.housenumber ? " " + properties.housenumber : "";
    const place = properties.name && properties.name !== street ? properties.name : "";
    const city = properties.city || properties.county || properties.state || "";
    const line = (street ? street + number : place) || place;
    const district = properties.district || properties.locality || "";
    const parts = [];
    if (line) parts.push(line);
    if (district && foldHu(district) !== foldHu(line) && foldHu(district) !== foldHu(city)) parts.push(district);
    if (city && foldHu(city) !== foldHu(line)) parts.push(city);
    if (properties.postcode && parts.length) parts.push(String(properties.postcode));
    return parts.join(", ") || "Cím";
  }

  function maneuverText(modifier, street) {
    const where = street ? " · " + street : "";
    if (modifier === "left" || modifier === "slight left" || modifier === "sharp left") return "Balra" + where;
    if (modifier === "right" || modifier === "slight right" || modifier === "sharp right") return "Jobbra" + where;
    if (modifier === "uturn") return "Fordulj vissza" + where;
    return street ? "Egyenesen · " + street : "Egyenesen";
  }

  function maneuverAction(modifier) {
    if (modifier === "left" || modifier === "slight left" || modifier === "sharp left") return "left";
    if (modifier === "right" || modifier === "slight right" || modifier === "sharp right" || modifier === "uturn") return "right";
    return "straight";
  }

  function routeFromGeometry(originName, destination, coordinates, steps, distanceMeters, durationSeconds) {
    const points = [];
    let km = 0;
    coordinates.forEach(function (pair) {
      const geo = { lon: pair[0], lat: pair[1] };
      if (points.length) km += haversine(points[points.length - 1], geo);
      const xy = project(geo.lon, geo.lat);
      points.push({
        lon: geo.lon,
        lat: geo.lat,
        x: xy.x,
        y: xy.y,
        km: km,
        road: "",
        limit: 70,
        kind: "address",
        nodeId: null,
      });
    });
    const totalKm = distanceMeters > 0 ? distanceMeters / 1000 : km;
    const scale = km > 0 ? totalKm / km : 1;
    points.forEach(function (point) { point.km *= scale; });

    const maneuvers = [];
    let walked = 0;
    (steps || []).forEach(function (step) {
      const modifier = step.maneuver && step.maneuver.modifier;
      const type = step.maneuver && step.maneuver.type;
      const street = step.name || "";
      const stepKm = (step.distance || 0) / 1000;
      const startKm = walked;
      const meaningful = type === "arrive" || type === "roundabout" || type === "rotary"
        || (modifier && modifier !== "straight" && step.distance > 80);
      if (meaningful) {
        const roundabout = type === "roundabout" || type === "rotary";
        maneuvers.push({
          km: type === "arrive" ? totalKm : walked,
          action: type === "arrive" ? "arrive" : maneuverAction(modifier),
          text: type === "arrive"
            ? "Érkezés · " + destination.label
            : roundabout
              ? "Körforgalom" + (street ? " · " + street : "")
              : maneuverText(modifier, street),
          road: street,
          place: destination.label,
          nodeId: null,
        });
      }
      walked += stepKm;
      if (street) {
        const endKm = walked;
        points.forEach(function (point) {
          if (!point.road && point.km >= startKm - 0.01 && point.km <= endKm + 0.01) point.road = street;
        });
      }
    });
    if (!maneuvers.length || maneuvers[maneuvers.length - 1].action !== "arrive") {
      maneuvers.push({
        km: totalKm,
        action: "arrive",
        text: "Érkezés · " + destination.label,
        road: "",
        place: destination.label,
        nodeId: null,
      });
    }
    return {
      fromId: originName,
      toId: "address",
      points: points,
      totalKm: totalKm,
      maneuvers: maneuvers,
      roads: ["cím"],
      durationHours: durationSeconds > 0 ? durationSeconds / 3600 : null,
      addressLabel: destination.label,
    };
  }

  function boot() {
    const mapCanvas = document.getElementById("map");
    const miniCanvas = document.getElementById("minimap");
    const windCanvas = document.getElementById("wind");
    const atlas = document.getElementById("atlas");
    const cab = document.getElementById("cab");
    const fromInput = document.getElementById("from-q");
    const toSelect = document.getElementById("to");
    const headline = document.getElementById("headline");
    const subline = document.getElementById("subline");
    const startButton = document.getElementById("start");

    const cityIds = Object.keys(PLACES).filter(function (id) { return PLACES[id].city; })
      .sort(function (a, b) { return PLACES[a].name.localeCompare(PLACES[b].name, "hu"); });

    cityIds.forEach(function (id) {
      const option = document.createElement("option");
      option.value = id;
      option.textContent = PLACES[id].name;
      toSelect.appendChild(option);
    });

    const state = {
      fromId: null,
      toId: "",
      useGraph: false,
      route: null,
      origin: null,
      destDraft: false,
      traveledKm: 0,
      speedKmh: 0,
      driving: false,
      paused: false,
      arrived: false,
      timeScale: 40,
      simMinutes: 8 * 60 + 20,
      address: null,
      inkKm: null,
      fly: null,
      routing: false,
      status: "",
      view: "atlas",
      zoom: 1,
      panX: 0,
      panY: 0,
      fitted: false,
    };

    function fitCountry(width, height) {
      const bounds = SCENERY.bounds;
      const pad = 36;
      const zx = (width - pad * 2) / Math.max(1, bounds.maxX - bounds.minX);
      const zy = (height - pad * 2) / Math.max(1, bounds.maxY - bounds.minY);
      state.zoom = Math.max(0.05, Math.min(zx, zy));
      state.panX = 0;
      state.panY = 0;
      state.fitted = true;
      state.fly = null;
    }

    function syncSelects() {
      if (toSelect.value !== state.toId) toSelect.value = state.toId;
    }

    function replanParked() {
      state.route = planRoute(state.fromId, state.toId);
      state.traveledKm = 0;
      state.speedKmh = 0;
      state.arrived = false;
      state.driving = false;
      state.paused = false;
      state.inkKm = null;
      state.routing = false;
      state.status = "";
      renderSummary();
    }

    function originLabel() {
      if (state.origin) return state.origin.label;
      if (state.fromId && PLACES[state.fromId]) return PLACES[state.fromId].name;
      return "";
    }

    function destinationPoint() {
      if (state.address) return state.address;
      if (state.destDraft) return null;
      if (state.toId && PLACES[state.toId]) {
        const place = PLACES[state.toId];
        return { lon: place.lon, lat: place.lat, label: place.name };
      }
      return null;
    }

    function renderSummary() {
      const route = state.route;
      const startName = originLabel();
      const destination = destinationPoint();
      if (!route) {
        if (startName || destination) {
          headline.textContent = (startName || "Indulás") + " → " + (destination ? destination.label : "Cél");
          subline.textContent = state.status || (startName ? "Keress célt is." : "Keress induló helyet.");
        } else {
          headline.textContent = "Keress induló helyet és célt";
          subline.textContent = "Mindkét mezőbe címet írhatsz.";
        }
        startButton.disabled = true;
        return;
      }
      startButton.disabled = !!state.routing;
      const destName = route.addressLabel || (destination ? destination.label : "Cél");
      headline.textContent = (startName || "Indulás") + " → " + destName;
      const via = route.addressLabel ? "címkeresés" : route.roads.join(" · ");
      subline.textContent = route.totalKm.toFixed(0) + " km · " + via + " · " + formatDuration(remainingHours(route, 0));
    }

    const queryInput = document.getElementById("q");
    const hits = document.getElementById("hits");
    const fromHits = document.getElementById("from-hits");
    const note = document.getElementById("search-note");
    let routeAbort = null;
    let routeToken = 0;

    function hideList(list) {
      list.hidden = true;
      list.innerHTML = "";
    }

    function placeFromFeature(feature) {
      const coordinates = feature.geometry && feature.geometry.coordinates;
      if (!coordinates || coordinates.length < 2) return null;
      return {
        lon: coordinates[0],
        lat: coordinates[1],
        label: addressLabel(feature.properties || {}),
      };
    }

    function bindSearch(input, list, selected, onPick, onClear) {
      let timer = null;
      let abort = null;
      let token = 0;
      input.addEventListener("keydown", function (event) {
        if (event.key !== "Enter") return;
        event.preventDefault();
        const first = list.querySelector("button");
        if (first && !list.hidden) first.click();
      });
      input.addEventListener("input", function () {
        const query = input.value.trim();
        const current = selected();
        if (timer) clearTimeout(timer);
        if (current && query !== current.label) onClear();
        if (query.length < 2) {
          hideList(list);
          if (!query) note.textContent = "";
          else if (!selected()) note.textContent = "Válassz a találatok közül.";
          return;
        }
        timer = setTimeout(function () { run(query); }, 350);
      });
      function run(query) {
        if (abort) abort.abort();
        abort = new AbortController();
        const my = ++token;
        hideList(list === hits ? fromHits : hits);
        note.textContent = "Keresés…";
        fetch(photonUrl(query), { signal: abort.signal })
          .then(function (response) {
            if (response.status === 429) throw new Error("limit");
            if (!response.ok) throw new Error("search");
            return response.json();
          })
          .then(function (data) {
            if (my !== token) return;
            const features = rankAddresses((data.features || []).filter(function (feature) {
              const code = feature.properties && feature.properties.countrycode;
              return code === "HU";
            }), query);
            if (!features.length) {
              hideList(list);
              note.textContent = "Nincs ilyen cím az országban.";
              return;
            }
            note.textContent = features.missingHouseNumber
              ? "A házszám nincs a térképen. Válassz az utca találatai közül."
              : (selected() ? "" : "Válassz a találatok közül.");
            list.innerHTML = "";
            features.forEach(function (feature) {
              if (!feature.geometry || !feature.geometry.coordinates) return;
              const item = document.createElement("li");
              const button = document.createElement("button");
              button.type = "button";
              button.textContent = addressLabel(feature.properties || {});
              button.addEventListener("click", function () { onPick(feature); });
              item.appendChild(button);
              list.appendChild(item);
            });
            if (!list.children.length) {
              hideList(list);
              note.textContent = "Nincs ilyen cím az országban.";
              return;
            }
            list.hidden = false;
          })
          .catch(function (error) {
            if (error && error.name === "AbortError") return;
            if (my !== token) return;
            hideList(list);
            note.textContent = error && error.message === "limit"
              ? "A keresés túl gyakori. Próbáld újra egy pillanat múlva."
              : "A keresés nem sikerült.";
          });
      }
    }

    function frameRoute(route) {
      const box = canvasBox(mapCanvas);
      if (!route || !route.points.length || box.w < 2) return;
      let minX = Infinity;
      let minY = Infinity;
      let maxX = -Infinity;
      let maxY = -Infinity;
      route.points.forEach(function (point) {
        minX = Math.min(minX, point.x);
        minY = Math.min(minY, point.y);
        maxX = Math.max(maxX, point.x);
        maxY = Math.max(maxY, point.y);
      });
      const pad = 48;
      const zx = (box.w - pad * 2) / Math.max(8, maxX - minX);
      const zy = (box.h - pad * 2) / Math.max(8, maxY - minY);
      state.fitted = true;
      state.fly = {
        t: 0,
        dur: 0.9,
        zoom0: state.zoom,
        zoom1: Math.max(0.05, Math.min(12, Math.min(zx, zy))),
        panX0: state.panX,
        panY0: state.panY,
        panX1: (minX + maxX) / 2 - SCENERY.bounds.cx,
        panY1: (minY + maxY) / 2 - SCENERY.bounds.cy,
      };
    }

    function routeBetween(origin, destination) {
      if (!origin || !destination) {
        state.routing = false;
        state.route = null;
        renderSummary();
        return;
      }
      if (routeAbort) routeAbort.abort();
      if (haversine(origin, destination) < 0.4) {
        state.routing = false;
        state.route = null;
        state.inkKm = null;
        state.status = "Ez már az indulásnál van.";
        note.textContent = state.status;
        renderSummary();
        return;
      }
      routeAbort = new AbortController();
      const token = ++routeToken;
      state.routing = true;
      state.route = null;
      state.inkKm = null;
      state.driving = false;
      state.arrived = false;
      state.traveledKm = 0;
      state.status = "Útvonal készül…";
      note.textContent = state.status;
      renderSummary();
      fetch(osrmUrl(origin, destination), { signal: routeAbort.signal })
        .then(function (response) {
          if (!response.ok) throw new Error("route");
          return response.json();
        })
        .then(function (data) {
          if (token !== routeToken) return;
          const found = data.routes && data.routes[0];
          const coordinates = found && found.geometry && found.geometry.coordinates;
          const steps = found && found.legs && found.legs[0] && found.legs[0].steps;
          if (data.code !== "Ok" || !coordinates || coordinates.length < 2 || !(found.distance > 0)) {
            state.routing = false;
            state.route = null;
            state.status = "Ehhez a címhez nincs útvonal.";
            note.textContent = state.status;
            renderSummary();
            return;
          }
          state.route = routeFromGeometry(origin.label, destination, coordinates, steps, found.distance, found.duration);
          state.traveledKm = 0;
          state.speedKmh = 0;
          state.arrived = false;
          state.driving = false;
          state.paused = false;
          state.inkKm = 0;
          state.routing = false;
          state.status = "";
          note.textContent = "";
          frameRoute(state.route);
          renderSummary();
        })
        .catch(function (error) {
          if (error && error.name === "AbortError") return;
          if (token !== routeToken) return;
          state.routing = false;
          state.route = null;
          state.status = "Ehhez a címhez nincs útvonal.";
          note.textContent = state.status;
          renderSummary();
        });
    }

    function planNow() {
      if (state.useGraph && state.fromId && state.toId && !state.address) {
        replanParked();
        return;
      }
      const destination = destinationPoint();
      if (!state.origin || !destination) {
        if (routeAbort) routeAbort.abort();
        routeToken += 1;
        state.routing = false;
        state.route = null;
        state.inkKm = null;
        state.status = "";
        note.textContent = "";
        renderSummary();
        return;
      }
      routeBetween(state.origin, destination);
    }

    bindSearch(fromInput, fromHits, function () { return state.origin; }, function (feature) {
      const place = placeFromFeature(feature);
      if (!place) return;
      state.origin = place;
      state.fromId = null;
      state.useGraph = false;
      fromInput.value = place.label;
      hideList(fromHits);
      note.textContent = "";
      planNow();
    }, function () {
      if (!state.origin && !state.fromId) return;
      state.origin = null;
      state.fromId = null;
      state.useGraph = false;
      planNow();
    });

    bindSearch(queryInput, hits, function () { return state.address; }, function (feature) {
      const place = placeFromFeature(feature);
      if (!place) return;
      state.address = place;
      state.destDraft = false;
      state.toId = "";
      toSelect.value = "";
      queryInput.value = place.label;
      hideList(hits);
      note.textContent = "";
      planNow();
    }, function () {
      if (!state.address) return;
      state.address = null;
      planNow();
    });
    queryInput.addEventListener("input", function () {
      const typed = queryInput.value.trim();
      const drafting = typed.length > 0 && !(state.address && state.address.label === typed);
      if (drafting === state.destDraft) return;
      state.destDraft = drafting;
      if (!state.address) planNow();
    });

    toSelect.addEventListener("change", function () {
      state.address = null;
      state.destDraft = false;
      queryInput.value = "";
      hideList(hits);
      state.toId = toSelect.value;
      if (state.useGraph && state.driving && !state.arrived && state.route) {
        const ahead = upcomingNode(state.route, state.traveledKm);
        const rest = ahead && ahead.nodeId && PLACES[ahead.nodeId] ? planRoute(ahead.nodeId, state.toId) : null;
        if (ahead && rest) {
          state.route = joinPartial(state.route, state.traveledKm, ahead, rest);
          state.traveledKm = 0;
          state.arrived = false;
          renderSummary();
          return;
        }
      }
      planNow();
    });

    document.getElementById("panel").addEventListener("submit", function (event) {
      event.preventDefault();
      if (event.submitter && event.submitter !== startButton) return;
      if (state.routing || !state.route) return;
      state.driving = true;
      state.paused = false;
      state.arrived = false;
      showCab();
    });
    document.getElementById("to-map").addEventListener("click", showAtlas);
    document.getElementById("pause").addEventListener("click", function () {
      if (state.arrived) return;
      state.paused = !state.paused;
      document.getElementById("pause").textContent = state.paused ? "Tovább" : "Szünet";
    });
    document.getElementById("scale-actions").addEventListener("click", function (event) {
      const button = event.target.closest("button");
      if (!button) return;
      state.timeScale = Number(button.dataset.scale);
      document.querySelectorAll("#scale-actions button").forEach(function (item) {
        item.setAttribute("aria-pressed", item === button ? "true" : "false");
      });
    });
    document.getElementById("zin").addEventListener("click", function () {
      state.fly = null;
      state.zoom = Math.min(12, state.zoom * 1.25);
    });
    document.getElementById("zout").addEventListener("click", function () {
      state.fly = null;
      state.zoom = Math.max(0.05, state.zoom / 1.25);
    });
    document.getElementById("zfit").addEventListener("click", function () {
      const box = canvasBox(mapCanvas);
      fitCountry(box.w, box.h);
    });

    let drag = null;
    mapCanvas.addEventListener("pointerdown", function (event) {
      drag = { x: event.clientX, y: event.clientY, panX: state.panX, panY: state.panY, moved: false };
      mapCanvas.setPointerCapture(event.pointerId);
    });
    mapCanvas.addEventListener("pointermove", function (event) {
      if (!drag) return;
      const dx = event.clientX - drag.x;
      const dy = event.clientY - drag.y;
      if (Math.hypot(dx, dy) > 6) {
        drag.moved = true;
        state.fly = null;
      }
      state.panX = drag.panX - dx / state.zoom;
      state.panY = drag.panY - dy / state.zoom;
    });
    mapCanvas.addEventListener("pointerup", function (event) {
      if (!drag) return;
      const moved = drag.moved;
      drag = null;
      if (moved || state.driving) return;
      const rect = mapCanvas.getBoundingClientRect();
      const camera = atlasCamera(canvasBox(mapCanvas).w, canvasBox(mapCanvas).h);
      const world = screenToWorld(event.clientX - rect.left, event.clientY - rect.top, camera);
      const hit = nearestCity(world.x, world.y, 26 / state.zoom);
      if (!hit) return;
      state.address = null;
      state.destDraft = false;
      queryInput.value = "";
      hideList(hits);
      state.toId = hit;
      toSelect.value = hit;
      planNow();
    });
    mapCanvas.addEventListener("pointercancel", function () { drag = null; });
    mapCanvas.addEventListener("wheel", function (event) {
      event.preventDefault();
      const factor = event.deltaY < 0 ? 1.12 : 0.89;
      state.fly = null;
      state.zoom = Math.max(0.05, Math.min(12, state.zoom * factor));
    }, { passive: false });

    function showCab() {
      state.view = "drive";
      atlas.hidden = true;
      cab.hidden = false;
      document.getElementById("pause").textContent = "Szünet";
    }
    function showAtlas() {
      state.view = "atlas";
      cab.hidden = true;
      atlas.hidden = false;
    }

    let last = performance.now();
    function frame(now) {
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      step(dt);
      draw();
      requestAnimationFrame(frame);
    }

    function step(dt) {
      if (state.route && state.inkKm != null && state.inkKm < state.route.totalKm) {
        state.inkKm = Math.min(state.route.totalKm, state.inkKm + (state.route.totalKm / 1.35) * dt);
      }
      if (state.fly) {
        state.fly.t += dt / state.fly.dur;
        const t = Math.min(1, state.fly.t);
        const e = t < 0.5 ? 2 * t * t : 1 - ((-2 * t + 2) ** 2) / 2;
        state.zoom = state.fly.zoom0 + (state.fly.zoom1 - state.fly.zoom0) * e;
        state.panX = state.fly.panX0 + (state.fly.panX1 - state.fly.panX0) * e;
        state.panY = state.fly.panY0 + (state.fly.panY1 - state.fly.panY0) * e;
        if (t >= 1) state.fly = null;
      }
      if (!state.driving || state.paused || state.arrived || !state.route) return;
      const here = sampleRoute(state.route, state.traveledKm);
      const target = here ? here.limit : 80;
      state.speedKmh += (target - state.speedKmh) * Math.min(1, dt * 1.4);
      const add = (state.speedKmh * dt * state.timeScale) / 3600;
      state.traveledKm = Math.min(state.route.totalKm, state.traveledKm + add);
      state.simMinutes += (dt * state.timeScale) / 60;
      if (state.traveledKm >= state.route.totalKm - 0.02) {
        state.traveledKm = state.route.totalKm;
        state.speedKmh = 0;
        state.arrived = true;
        state.paused = true;
      }
    }

    function draw() {
      live.route = state.route;
      live.km = state.traveledKm;
      live.minutes = state.simMinutes;
      live.inkKm = state.inkKm;
      live.address = state.address;
      live.origin = state.origin;
      activeRoute = state.route;
      activeKm = state.traveledKm;
      if (state.view === "atlas") {
        const box = canvasBox(mapCanvas);
        if (!state.fitted && box.w > 0) fitCountry(box.w, box.h);
        if (box.w > 0) drawMap(box.ctx, box.w, box.h, atlasCamera(box.w, box.h), "atlas");
      } else {
        const glass = canvasBox(windCanvas);
        const mini = canvasBox(miniCanvas);
        if (glass.w > 0) drawWindshield(glass.ctx, glass.w, glass.h);
        if (mini.w > 0) drawMap(mini.ctx, mini.w, mini.h, driveCamera(mini.w, mini.h), "drive");
        updateDash();
      }
    }

    function updateDash() {
      const route = state.route;
      const maneuver = nextManeuver(route, state.traveledKm);
      const remainKm = route ? Math.max(0, route.totalKm - state.traveledKm) : 0;
      const hours = remainingHours(route, state.traveledKm);
      document.getElementById("speed-num").textContent = String(Math.round(state.speedKmh));
      document.getElementById("remain").textContent = remainKm.toFixed(0) + " km";
      document.getElementById("eta").textContent = state.arrived
        ? "Megérkeztél"
        : "érkezés " + formatClock(state.simMinutes + hours * 60);
      const dist = maneuver ? Math.max(0, maneuver.km - state.traveledKm) : 0;
      document.getElementById("man-km").textContent = state.arrived ? "0 km" : dist.toFixed(dist < 10 ? 1 : 0) + " km";
      document.getElementById("man-where").textContent = maneuver ? maneuver.text : "";
      document.getElementById("man-road").textContent = hereRoad();
      drawArrow(document.getElementById("arrow"), maneuver ? maneuver.action : "straight");
    }

    function hereRoad() {
      const here = sampleRoute(state.route, state.traveledKm);
      if (!here) return "";
      if (here.kind === "address") return (here.road || "út") + " · " + here.limit + " km/h";
      const kind = here.kind === "motorway" ? "autópálya" : "főút";
      return here.road + " " + kind + " · " + here.limit + " km/h";
    }

    function atlasCamera(width, height) {
      return {
        focusX: SCENERY.bounds.cx + state.panX,
        focusY: SCENERY.bounds.cy + state.panY,
        zoom: state.zoom,
        rot: 0,
        originX: width / 2,
        originY: height / 2,
        width: width,
        height: height,
      };
    }

    function driveCamera(width, height) {
      const here = sampleRoute(state.route, state.traveledKm) || { x: SCENERY.bounds.cx, y: SCENERY.bounds.cy, heading: -Math.PI / 2 };
      const ahead = 0.62;
      return {
        focusX: here.x,
        focusY: here.y,
        zoom: Math.max(width, height) / (22 * PX_PER_KM),
        rot: -here.heading - Math.PI / 2,
        originX: width / 2,
        originY: height * ahead,
        width: width,
        height: height,
        truck: here,
      };
    }

    renderSummary();
    requestAnimationFrame(frame);
    window.addEventListener("resize", function () { state.fitted = false; });

    const params = new URLSearchParams(location.search);
    if (params.get("from") && PLACES[params.get("from")]) {
      const place = PLACES[params.get("from")];
      state.fromId = params.get("from");
      state.useGraph = true;
      state.origin = { lon: place.lon, lat: place.lat, label: place.name };
      fromInput.value = place.name;
    }
    if (params.get("to") && PLACES[params.get("to")]) {
      state.toId = params.get("to");
      toSelect.value = state.toId;
    }
    if (state.useGraph && state.fromId && state.toId) replanParked();
    if (params.get("km")) state.traveledKm = Math.min(state.route ? state.route.totalKm : 0, Number(params.get("km")) || 0);
    if (params.get("drive") === "1" && state.route) {
      state.driving = true;
      state.speedKmh = sampleRoute(state.route, state.traveledKm).limit;
      showCab();
    }
    syncSelects();
  }

  function upcomingNode(route, traveledKm) {
    if (!route) return null;
    for (let i = 0; i < route.points.length; i++) {
      const point = route.points[i];
      if (point.nodeId && point.km > traveledKm + 0.05 && point.nodeId !== route.fromId) {
        return { nodeId: point.nodeId, km: point.km };
      }
    }
    return { nodeId: route.toId, km: route.totalKm };
  }

  function joinPartial(route, traveledKm, ahead, rest) {
    if (!rest) return route;
    const prefix = [];
    const here = sampleRoute(route, traveledKm);
    prefix.push({
      lon: 0, lat: 0, x: here.x, y: here.y, km: 0,
      road: here.road, limit: here.limit, kind: here.kind, nodeId: null,
    });
    route.points.forEach(function (point) {
      if (point.km <= traveledKm + 0.001) return;
      if (point.km > ahead.km + 0.001) return;
      const shift = point.km - traveledKm;
      prefix.push(Object.assign({}, point, { km: shift }));
    });
    const offset = prefix.length ? prefix[prefix.length - 1].km : 0;
    rest.points.forEach(function (point, index) {
      if (index === 0) return;
      prefix.push(Object.assign({}, point, { km: point.km + offset }));
    });
    const maneuvers = rest.maneuvers.map(function (maneuver) {
      return Object.assign({}, maneuver, { km: maneuver.km + offset });
    });
    return {
      fromId: route.fromId,
      toId: rest.toId,
      points: prefix,
      totalKm: prefix.length ? prefix[prefix.length - 1].km : 0,
      maneuvers: maneuvers,
      roads: rest.roads,
    };
  }

  function canvasBox(canvas) {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = canvas.clientWidth;
    const h = canvas.clientHeight;
    if (w < 2 || h < 2) return { ctx: null, w: 0, h: 0 };
    const width = Math.floor(w * dpr);
    const height = Math.floor(h * dpr);
    if (canvas.width !== width || canvas.height !== height) {
      canvas.width = width;
      canvas.height = height;
    }
    const ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    return { ctx: ctx, w: w, h: h };
  }

  function screenToWorld(sx, sy, camera) {
    const rx = (sx - camera.originX) / camera.zoom;
    const ry = (sy - camera.originY) / camera.zoom;
    const cos = Math.cos(camera.rot);
    const sin = Math.sin(camera.rot);
    return {
      x: camera.focusX + rx * cos + ry * sin,
      y: camera.focusY - rx * sin + ry * cos,
    };
  }

  function worldToScreen(x, y, camera) {
    const dx = x - camera.focusX;
    const dy = y - camera.focusY;
    const cos = Math.cos(camera.rot);
    const sin = Math.sin(camera.rot);
    return {
      x: camera.originX + (dx * cos - dy * sin) * camera.zoom,
      y: camera.originY + (dx * sin + dy * cos) * camera.zoom,
    };
  }

  function nearestCity(x, y, maxDist) {
    let best = null;
    let bestDist = maxDist;
    Object.keys(PLACES).forEach(function (id) {
      if (!PLACES[id].city) return;
      const xy = project(PLACES[id].lon, PLACES[id].lat);
      const dist = Math.hypot(xy.x - x, xy.y - y);
      if (dist < bestDist) {
        bestDist = dist;
        best = id;
      }
    });
    return best;
  }

  function drawMap(ctx, width, height, camera, mode) {
    const px = function (screenPx) { return screenPx / Math.max(0.05, camera.zoom); };
    ctx.clearRect(0, 0, width, height);
    ctx.fillStyle = mode === "drive" ? "#d7c49a" : "#cbb892";
    ctx.fillRect(0, 0, width, height);
    paintGrain(ctx, width, height);

    ctx.save();
    ctx.translate(camera.originX, camera.originY);
    ctx.rotate(camera.rot);
    ctx.scale(camera.zoom, camera.zoom);
    ctx.translate(-camera.focusX, -camera.focusY);

    tracePoly(ctx, SCENERY.land);
    ctx.fillStyle = "#d5e2b4";
    ctx.fill();
    ctx.save();
    ctx.clip();
    SCENERY.patches.forEach(function (patch) {
      ctx.fillStyle = patch.color;
      ctx.globalAlpha = 0.55;
      ctx.beginPath();
      ctx.ellipse(patch.x, patch.y, patch.rx, patch.ry, patch.rot, 0, Math.PI * 2);
      ctx.fill();
    });
    ctx.globalAlpha = 1;
    SCENERY.trees.forEach(function (tree) { drawTree(ctx, tree.x, tree.y, px(8 * tree.s)); });
    ctx.restore();

    strokeLine(ctx, SCENERY.danube, px(11), "#9fd0e4");
    strokeLine(ctx, SCENERY.danube, px(6), "#3e93b8");
    strokeLine(ctx, SCENERY.tisza, px(8), "#9fd0e4");
    strokeLine(ctx, SCENERY.tisza, px(4.5), "#3e93b8");
    tracePoly(ctx, SCENERY.lake);
    ctx.fillStyle = "#4aa3c8";
    ctx.fill();
    ctx.strokeStyle = "#2f6f8d";
    ctx.lineWidth = px(2);
    ctx.stroke();

    tracePoly(ctx, SCENERY.land);
    ctx.strokeStyle = "rgba(42, 36, 28, 0.28)";
    ctx.lineWidth = px(7);
    ctx.stroke();
    ctx.strokeStyle = "#2a241c";
    ctx.lineWidth = px(2.4);
    ctx.stroke();

    ROAD_LINES.forEach(function (line) {
      const wide = line.kind === "motorway";
      strokeLine(ctx, line.points, px(wide ? 8 : 5), "#2a241c");
      strokeLine(ctx, line.points, px(wide ? 5 : 2.6), wide ? "#6d675d" : "#c4894a");
    });

    if (camera.route) {
      const ink = camera.inkKm == null ? camera.route.totalKm : camera.inkKm;
      const shown = routePointsUntil(camera.route, ink);
      strokeLine(ctx, camera.route.points, px(7), "rgba(168, 91, 18, 0.28)");
      if (shown.length > 1) {
        strokeLine(ctx, shown, px(10), "#a85b12");
        strokeLine(ctx, shown, px(5.5), "#ff9f1c");
      }
      const progress = routePointsUntil(camera.route, Math.min(camera.traveledKm, ink));
      if (progress.length > 1) strokeLine(ctx, progress, px(2.2), "rgba(255, 246, 224, 0.9)");
      if (!camera.address && ink > camera.route.totalKm - 0.4) drawFlag(ctx, camera.route.points[camera.route.points.length - 1], camera.zoom);
    }
    if (camera.address) {
      const pin = project(camera.address.lon, camera.address.lat);
      drawFlag(ctx, pin, camera.zoom);
    }
    if (camera.origin) {
      drawStart(ctx, project(camera.origin.lon, camera.origin.lat), camera.zoom);
    }

    Object.keys(PLACES).forEach(function (id) {
      if (!PLACES[id].city) return;
      const xy = project(PLACES[id].lon, PLACES[id].lat);
      drawTown(ctx, xy.x, xy.y, !!PLACES[id].capital, camera.zoom);
    });

    if (camera.truck) drawTruck(ctx, camera.truck.x, camera.truck.y, camera.truck.heading, camera.zoom);

    ctx.restore();
    drawLabels(ctx, camera, mode);
    drawScale(ctx, width, height, camera);
  }

  function routePointsUntil(route, km) {
    const points = [];
    for (let i = 0; i < route.points.length; i++) {
      if (route.points[i].km <= km) points.push(route.points[i]);
    }
    const here = sampleRoute(route, km);
    if (here) points.push(here);
    return points;
  }

  function tracePoly(ctx, points) {
    ctx.beginPath();
    ctx.moveTo(points[0].x, points[0].y);
    for (let i = 1; i < points.length; i++) ctx.lineTo(points[i].x, points[i].y);
    ctx.closePath();
  }

  function strokeLine(ctx, points, width, color) {
    if (!points || points.length < 2) return;
    ctx.beginPath();
    ctx.moveTo(points[0].x, points[0].y);
    for (let i = 1; i < points.length; i++) ctx.lineTo(points[i].x, points[i].y);
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.lineJoin = "round";
    ctx.lineCap = "round";
    ctx.stroke();
  }

  function drawTree(ctx, x, y, size) {
    ctx.fillStyle = "#6b4a2e";
    ctx.fillRect(x - size * 0.12, y, size * 0.24, size * 0.45);
    ctx.fillStyle = "#2f6b45";
    ctx.beginPath();
    ctx.moveTo(x, y - size);
    ctx.lineTo(x + size * 0.55, y + size * 0.15);
    ctx.lineTo(x - size * 0.55, y + size * 0.15);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = "#1e3d28";
    ctx.lineWidth = 0.8;
    ctx.stroke();
  }

  function drawTown(ctx, x, y, capital, zoom) {
    ctx.save();
    ctx.translate(x, y);
    const scale = (capital ? 1.35 : 1.05) / zoom;
    ctx.scale(scale, scale);
    ctx.fillStyle = "rgba(70, 55, 30, 0.22)";
    ctx.beginPath();
    ctx.ellipse(0, 6, 12, 4, 0, 0, Math.PI * 2);
    ctx.fill();
    const houses = capital ? [[-9, 0], [0, -2], [8, 1]] : [[-5, 1], [3, 0]];
    houses.forEach(function (house) {
      ctx.fillStyle = "#f7edd4";
      ctx.strokeStyle = "#2a241c";
      ctx.lineWidth = 1.1;
      ctx.beginPath();
      ctx.rect(house[0], house[1], 7, 6);
      ctx.fill();
      ctx.stroke();
      ctx.fillStyle = capital ? "#c4493a" : "#b85a32";
      ctx.beginPath();
      ctx.moveTo(house[0] - 1, house[1]);
      ctx.lineTo(house[0] + 3.5, house[1] - 4);
      ctx.lineTo(house[0] + 8, house[1]);
      ctx.closePath();
      ctx.fill();
      ctx.stroke();
    });
    if (capital) {
      ctx.fillStyle = "#f0d48a";
      ctx.beginPath();
      ctx.arc(0, -1, 4.2, Math.PI, 0);
      ctx.fill();
      ctx.stroke();
    }
    ctx.restore();
  }

  function drawTruck(ctx, x, y, heading, zoom) {
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(heading);
    ctx.scale(1.2 / zoom, 1.2 / zoom);
    ctx.fillStyle = "rgba(36, 28, 20, 0.28)";
    ctx.beginPath();
    ctx.ellipse(0, 3, 16, 8, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = "#f4efe4";
    ctx.strokeStyle = "#2a241c";
    ctx.lineWidth = 1.2;
    roundRect(ctx, -16, -6.5, 18, 13, 2);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = "#d94f30";
    roundRect(ctx, 2, -6.5, 13, 13, 2);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = "#f2d7a4";
    ctx.fillRect(10, -4.5, 3.5, 9);
    ctx.fillStyle = "#1c1915";
    [[-12, -7.5], [-12, 5], [3, -7.5], [3, 5], [10, -7.5], [10, 5]].forEach(function (wheel) {
      ctx.fillRect(wheel[0], wheel[1], 4.5, 2.6);
    });
    ctx.restore();
  }

  function drawStart(ctx, point, zoom) {
    ctx.save();
    ctx.translate(point.x, point.y);
    ctx.scale(1 / zoom, 1 / zoom);
    ctx.fillStyle = "#f4efe4";
    ctx.strokeStyle = "#2a241c";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.arc(0, 0, 7, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
    ctx.restore();
  }

  function drawFlag(ctx, point, zoom) {
    ctx.save();
    ctx.translate(point.x, point.y);
    ctx.scale(1 / zoom, 1 / zoom);
    ctx.strokeStyle = "#2a241c";
    ctx.lineWidth = 1.4;
    ctx.beginPath();
    ctx.moveTo(0, 2);
    ctx.lineTo(0, -16);
    ctx.stroke();
    ctx.fillStyle = "#d94f30";
    ctx.beginPath();
    ctx.moveTo(0, -16);
    ctx.lineTo(12, -11);
    ctx.lineTo(0, -6);
    ctx.fill();
    ctx.stroke();
    ctx.restore();
  }

  function roundRect(ctx, x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  function drawLabels(ctx, camera, mode) {
    const boxes = [];
    if (camera.route && mode === "atlas") {
      const shields = {};
      ROAD_LINES.forEach(function (line) {
        if (line.kind !== "motorway" || line.points.length < 2) return;
        let length = 0;
        for (let i = 1; i < line.points.length; i++) {
          length += Math.hypot(line.points[i].x - line.points[i - 1].x, line.points[i].y - line.points[i - 1].y);
        }
        if (!shields[line.road] || length > shields[line.road].length) {
          shields[line.road] = { length: length, point: line.points[Math.floor(line.points.length / 2)] };
        }
      });
      Object.keys(shields).forEach(function (name) {
        const screen = worldToScreen(shields[name].point.x, shields[name].point.y, camera);
        if (screen.x < 24 || screen.y < 18 || screen.x > camera.width - 24 || screen.y > camera.height - 18) return;
        ctx.font = "600 11px Sora, sans-serif";
        const width = ctx.measureText(name).width + 12;
        boxes.push({ l: screen.x - width / 2, t: screen.y - 10, r: screen.x + width / 2, b: screen.y + 10, priority: 2 });
        drawShield(ctx, screen.x, screen.y, name);
      });
    }

    ctx.font = '15px "Patrick Hand", "Segoe Print", cursive';
    ctx.textAlign = "left";
    ctx.textBaseline = "middle";
    const ids = Object.keys(PLACES).filter(function (id) { return PLACES[id].city; });
    ids.sort(function (a, b) { return (PLACES[b].capital ? 1 : 0) - (PLACES[a].capital ? 1 : 0); });
    ids.forEach(function (id) {
      const place = PLACES[id];
      const xy = project(place.lon, place.lat);
      const screen = worldToScreen(xy.x, xy.y, camera);
      if (screen.x < -40 || screen.y < -20 || screen.x > camera.width + 40 || screen.y > camera.height + 20) return;
      const priority = place.capital ? 3 : (camera.route && (id === camera.route.fromId || id === camera.route.toId) ? 2 : 1);
      if (mode === "drive" && priority < 2 && Math.hypot(screen.x - camera.originX, screen.y - camera.originY) > Math.min(camera.width, camera.height) * 0.72) return;
      const label = place.name;
      const width = ctx.measureText(label).width;
      const lift = place.capital ? 30 : 20;
      const left = screen.x - width / 2;
      const box = { l: left, t: screen.y - lift - 10, r: left + width, b: screen.y - lift + 8, priority: priority };
      const blocked = boxes.some(function (other) {
        return !(box.r < other.l || box.l > other.r || box.b < other.t || box.t > other.b) && other.priority >= priority;
      });
      if (blocked && priority < 3) return;
      boxes.push(box);
      ctx.lineWidth = 4;
      ctx.strokeStyle = "rgba(244, 230, 196, 0.92)";
      ctx.strokeText(label, left, screen.y - lift);
      ctx.fillStyle = "#241c14";
      ctx.fillText(label, left, screen.y - lift);
    });
  }

  function drawShield(ctx, x, y, text) {
    ctx.font = "600 11px Sora, sans-serif";
    const width = ctx.measureText(text).width + 10;
    ctx.fillStyle = "#1f7a43";
    ctx.strokeStyle = "#f4e6c4";
    ctx.lineWidth = 1.5;
    roundRect(ctx, x - width / 2, y - 8, width, 16, 4);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = "#f7f1e4";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(text, x, y + 0.5);
  }

  function drawScale(ctx, width, height, camera) {
    const pxPerKm = PX_PER_KM * camera.zoom;
    let km = 50;
    let px = km * pxPerKm;
    while (px > 130 && km > 1) { km /= 2; px /= 2; }
    while (px < 48) { km *= 2; px *= 2; }
    const x = 16;
    const y = 18;
    ctx.strokeStyle = "#241c14";
    ctx.fillStyle = "#241c14";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(x, y);
    ctx.lineTo(x + px, y);
    ctx.moveTo(x, y - 4);
    ctx.lineTo(x, y + 4);
    ctx.moveTo(x + px, y - 4);
    ctx.lineTo(x + px, y + 4);
    ctx.stroke();
    ctx.font = "12px Sora, sans-serif";
    ctx.textAlign = "left";
    ctx.fillText((km >= 1 ? km : km.toFixed(1)) + " km", x, y + 16);
    void height;
  }

  let grainCanvas = null;
  function paintGrain(ctx, width, height) {
    if (!grainCanvas) {
      grainCanvas = document.createElement("canvas");
      grainCanvas.width = 180;
      grainCanvas.height = 180;
      const g = grainCanvas.getContext("2d");
      const image = g.createImageData(180, 180);
      const rnd = mulberry32(9);
      for (let i = 0; i < image.data.length; i += 4) {
        const shade = 180 + Math.floor(rnd() * 60);
        image.data[i] = shade;
        image.data[i + 1] = shade - 10;
        image.data[i + 2] = shade - 30;
        image.data[i + 3] = Math.floor(rnd() * 38);
      }
      g.putImageData(image, 0, 0);
    }
    ctx.save();
    ctx.globalAlpha = 0.35;
    const pattern = ctx.createPattern(grainCanvas, "repeat");
    ctx.fillStyle = pattern;
    ctx.fillRect(0, 0, width, height);
    ctx.restore();
  }

  function drawWindshield(ctx, width, height) {
    const hour = ((live.minutes || 8 * 60) / 60) % 24;
    const day = hour >= 6 && hour < 20;
    const horizon = height * 0.4;
    const sky = ctx.createLinearGradient(0, 0, 0, horizon);
    sky.addColorStop(0, day ? "#8ecae6" : "#141b2e");
    sky.addColorStop(1, day ? "#d7f0c8" : "#243044");
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, width, height);
    if (day) {
      ctx.fillStyle = "#ffe08a";
      ctx.beginPath();
      ctx.arc(width * 0.8, horizon * 0.42, Math.max(18, width * 0.03), 0, Math.PI * 2);
      ctx.fill();
      ctx.strokeStyle = "#2a241c";
      ctx.lineWidth = 2;
      ctx.stroke();
    }
    drawCloud(ctx, width * 0.22, horizon * 0.35, 1);
    drawCloud(ctx, width * 0.55, horizon * 0.22, 0.75);

    ctx.fillStyle = day ? "#6f9a62" : "#243028";
    ctx.beginPath();
    ctx.moveTo(0, horizon + 10);
    for (let x = 0; x <= width; x += 16) {
      const y = horizon - 10 + Math.sin(x * 0.012) * 18 + Math.sin(x * 0.004 + 2) * 10;
      ctx.lineTo(x, y);
    }
    ctx.lineTo(width, height);
    ctx.lineTo(0, height);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = "#2a241c";
    ctx.lineWidth = 2;
    ctx.stroke();

    const here = sampleRoute(currentRoute(), currentKm());
    if (here) drawRoadAhead(ctx, width, height, horizon, here, day);
    drawHood(ctx, width, height);
  }

  function currentRoute() { return bootStateRoute(); }
  function currentKm() { return bootStateKm(); }

  let activeRoute = null;
  let activeKm = 0;
  function bootStateRoute() { return activeRoute; }
  function bootStateKm() { return activeKm; }

  function drawRoadAhead(ctx, width, height, horizon, here, day) {
    const samples = samplesAhead(activeRoute, activeKm, here, 1400, 18);
    const left = [];
    const right = [];
    const mid = [];
    samples.forEach(function (sample) {
      if (sample.forwardM < 8) return;
      left.push(projectGround(width, height, horizon, sample.forwardM, sample.rightM - 7.2));
      right.push(projectGround(width, height, horizon, sample.forwardM, sample.rightM + 7.2));
      mid.push(Object.assign(projectGround(width, height, horizon, sample.forwardM, sample.rightM), { dash: sample.dash }));
    });
    if (left.length < 2) return;
    const grassL = [];
    const grassR = [];
    samples.forEach(function (sample) {
      if (sample.forwardM < 8) return;
      grassL.push(projectGround(width, height, horizon, sample.forwardM, sample.rightM - 46));
      grassR.push(projectGround(width, height, horizon, sample.forwardM, sample.rightM + 46));
    });
    if (grassL.length > 1) {
      ctx.beginPath();
      ctx.moveTo(grassL[0].x, grassL[0].y);
      grassL.forEach(function (point) { ctx.lineTo(point.x, point.y); });
      for (let i = grassR.length - 1; i >= 0; i--) ctx.lineTo(grassR[i].x, grassR[i].y);
      ctx.closePath();
      ctx.fillStyle = day ? "#b7cf86" : "#243224";
      ctx.fill();
    }
    ctx.beginPath();
    ctx.moveTo(left[0].x, left[0].y);
    left.forEach(function (point) { ctx.lineTo(point.x, point.y); });
    for (let i = right.length - 1; i >= 0; i--) ctx.lineTo(right[i].x, right[i].y);
    ctx.closePath();
    ctx.fillStyle = day ? "#5c615b" : "#323632";
    ctx.fill();
    ctx.strokeStyle = "#2a241c";
    ctx.lineWidth = 3;
    ctx.stroke();

    ctx.beginPath();
    ctx.moveTo(left[0].x, left[0].y);
    left.forEach(function (point) { ctx.lineTo(point.x, point.y); });
    ctx.strokeStyle = "#f4efe4";
    ctx.lineWidth = 2;
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(right[0].x, right[0].y);
    right.forEach(function (point) { ctx.lineTo(point.x, point.y); });
    ctx.stroke();

    ctx.strokeStyle = "rgba(255, 159, 28, 0.95)";
    ctx.lineWidth = 5;
    ctx.lineCap = "round";
    ctx.beginPath();
    ctx.moveTo(mid[0].x, mid[0].y);
    mid.forEach(function (point) { ctx.lineTo(point.x, point.y); });
    ctx.stroke();

    ctx.strokeStyle = "rgba(255,246,224,0.9)";
    ctx.lineWidth = 3;
    for (let i = 0; i < mid.length - 1; i++) {
      if (!mid[i].dash) continue;
      ctx.beginPath();
      ctx.moveTo(mid[i].x, mid[i].y);
      ctx.lineTo(mid[i + 1].x, mid[i + 1].y);
      ctx.stroke();
    }

    const trees = [];
    samples.forEach(function (sample) {
      if (sample.forwardM < 24 || sample.forwardM > 700 || !sample.tree) return;
      [-1, 1].forEach(function (side) {
        if (sample.treeSide !== side && sample.treeSide !== 0) return;
        trees.push({
          forwardM: sample.forwardM,
          point: projectGround(width, height, horizon, sample.forwardM, sample.rightM + side * (16 + sample.treeSpread)),
        });
      });
    });
    trees.sort(function (a, b) { return b.forwardM - a.forwardM; });
    trees.forEach(function (tree) {
      const size = Math.max(8, 1400 / tree.forwardM);
      drawTree(ctx, tree.point.x, tree.point.y, size);
    });
  }

  function samplesAhead(route, km, here, meters, step) {
    const fx = Math.cos(here.heading);
    const fy = Math.sin(here.heading);
    const rx = -fy;
    const ry = fx;
    const list = [];
    for (let dist = 0; dist <= meters; dist += step) {
      const at = sampleRoute(route, km + dist / 1000);
      if (!at) break;
      const dx = (at.x - here.x) / PX_PER_KM * 1000;
      const dy = (at.y - here.y) / PX_PER_KM * 1000;
      const forwardM = dx * fx + dy * fy;
      const rightM = dx * rx + dy * ry;
      const seed = Math.floor(at.km * 20);
      list.push({
        forwardM: forwardM,
        rightM: rightM,
        dash: seed % 4 < 2,
        tree: seed % 3 === 0,
        treeSide: seed % 2 === 0 ? -1 : 1,
        treeSpread: (seed % 5) * 1.4,
      });
    }
    return list;
  }

  function projectGround(width, height, horizon, forwardM, rightM) {
    const depth = Math.max(forwardM, 7);
    const focal = Math.max(width, height, 520) * 0.92;
    return {
      x: width / 2 + (rightM * focal) / depth,
      y: horizon + (7.4 * focal) / depth,
    };
  }

  function drawCloud(ctx, x, y, scale) {
    ctx.fillStyle = "rgba(255,255,255,0.9)";
    ctx.strokeStyle = "rgba(42,36,28,0.45)";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.ellipse(x, y, 34 * scale, 16 * scale, 0, 0, Math.PI * 2);
    ctx.ellipse(x + 24 * scale, y + 6 * scale, 24 * scale, 13 * scale, 0, 0, Math.PI * 2);
    ctx.ellipse(x - 22 * scale, y + 7 * scale, 20 * scale, 12 * scale, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
  }

  function drawHood(ctx, width, height) {
    ctx.fillStyle = "#c4493a";
    ctx.beginPath();
    ctx.moveTo(0, height);
    ctx.lineTo(width * 0.18, height * 0.84);
    ctx.quadraticCurveTo(width * 0.5, height * 0.76, width * 0.82, height * 0.84);
    ctx.lineTo(width, height);
    ctx.closePath();
    ctx.fill();
    ctx.strokeStyle = "#2a241c";
    ctx.lineWidth = 3;
    ctx.stroke();
    ctx.strokeStyle = "rgba(255, 224, 160, 0.45)";
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(width * 0.3, height * 0.9);
    ctx.quadraticCurveTo(width * 0.5, height * 0.84, width * 0.7, height * 0.9);
    ctx.stroke();
  }

  function drawArrow(svg, action) {
    const rotation = action === "left" ? -90 : action === "right" ? 90 : action === "arrive" ? 0 : 0;
    const color = action === "arrive" ? "#8fd18a" : "#ff9f1c";
    svg.innerHTML = '<g transform="translate(23 23) rotate(' + rotation + ')">' +
      '<path d="M0 16 V-8 M0 -8 L-10 2 M0 -8 L10 2" fill="none" stroke="' + color + '" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>' +
      "</g>";
  }

  const live = { route: null, km: 0, minutes: 8 * 60 + 20 };
  const baseDrawMap = drawMap;
  drawMap = function (ctx, width, height, camera, mode) {
    camera.route = live.route;
    camera.traveledKm = live.km;
    camera.inkKm = live.inkKm;
    camera.address = live.address;
    camera.origin = live.origin;
    camera.truck = sampleRoute(live.route, live.km);
    activeRoute = live.route;
    activeKm = live.km;
    baseDrawMap(ctx, width, height, camera, mode);
  };

  return {
    PLACES: PLACES,
    planRoute: planRoute,
    project: project,
    citiesInsideCountry: citiesInsideCountry,
    pointInPoly: pointInPoly,
    HUNGARY: HUNGARY,
    photonUrl: photonUrl,
    osrmUrl: osrmUrl,
    addressLabel: addressLabel,
    rankAddresses: rankAddresses,
    routeFromGeometry: routeFromGeometry,
    boot: boot,
  };
});
