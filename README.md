# ToonNav3D

Rajzfilmes, tengerparti 3D-s mintaút a böngészőben. A K706 fekvő képernyőjére készül, telefonon is megnyílik.

Nyisd meg az `index.html` lapot. Egy autó megy egy rövid körön. Jobbra víz van, balra házak, fák, virágágyások, egy kápolna és egy szélmalom, távolabb hegyek és egy vár. Az úton türkiz vezetőcsík fut. A kamera az autó mögött és fölött követi.

A jobb oldali Garázs gomb megállítja a kocsit, és három autót kínál: Ferrari F1 SF90 (valvetin, CC BY 4.0), egy nyitott kerekű formula-autó (3D Assets, CC0), és a saját sportkompakt. A választás a böngészőben megmarad. Vezetés közben a panel nem látszik. A források és a licenc a `sports-car-license.txt` fájlban vannak.

A felső számok és a sebesség a mintakörhöz tartoznak. Ha az alsó autó gombbal megállítod, a sebesség 0 lesz, és a kilométer nem csökken. Ez még nem GPS-navigáció. A 18°C, a 22°C és a 90-es tábla minta, nem élő adat. A nap/hold gomb fokozatosan vált éjszakára: az út látható marad, és az autó fényszórója felkapcsol.

A bal oldali célmező címet vagy várost keres a Photon demón (OpenStreetMap, kulcs nélkül). A GPS gomb a böngésző helyengedélyét kéri. Ha van helyzet és kiválasztott cél, a lap közúti útvonalat kér a FOSSGIS OSRM szerverétől (`routing.openstreetmap.de`, legfeljebb egy kérés másodpercenként, © OpenStreetMap, OSRM). A GPS-pontnak nem kell az út közepén lennie: az indulást a 2 km-en belüli legközelebbi autóútra illeszti. Ha ennél messzebb van a legközelebbi közút, vagy nincs útvonal, a Mintaút marad, és a lap ezt kiírja. A 3D-s autó és a türkiz csík a lekért vonalat követi, a tengerparti táj pedig látható marad. Az autó eleje a haladás irányába néz. A haladás sebessége szimulált, nem a GPS-ből mért sebesség. GPS nélkül a Mintaút marad, és a cél koordinátája nem lesz indulópont.

A korábbi térképes lap megmaradt: `elozo-terkep.html`.
