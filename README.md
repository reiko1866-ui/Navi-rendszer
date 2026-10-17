# ToonNav3D

Rajzfilmes, tengerparti 3D-s mintaút a böngészőben. A K706 fekvő képernyőjére készül, telefonon is megnyílik.

Nyisd meg az `index.html` lapot. Egy kék autó megy egy rövid körön. Jobbra víz van, balra házak, fák, virágágyások, egy kápolna és egy szélmalom, távolabb hegyek és egy vár. Az úton türkiz vezetőcsík fut. A kamera az autó mögött és fölött követi.

A felső számok és a sebesség a mintakörhöz tartoznak. Ha az alsó autó gombbal megállítod, a sebesség 0 lesz, és a kilométer nem csökken. Ez még nem GPS-navigáció. A 18°C, a 22°C és a 90-es tábla minta, nem élő adat. A nap/hold gomb fokozatosan vált éjszakára: az út látható marad, és az autó fényszórója felkapcsol.

A bal oldali célmező címet vagy várost keres. A keresés gomb a Photon demószolgáltatást hívja (OpenStreetMap adat, kulcs nélkül, méltányos használat, rendelkezésre állás nélkül). A GPS gomb a böngésző helyengedélyét kéri, és kiírja, ha az engedélyt megtagadják vagy a hely nem érhető el. A találat és a GPS-koordináta nem mozgatja az autót, és nem rajzol valódi útvonalat. Útvonaltervezéshez saját kiszolgáló vagy API-kulcs kell; ilyen kulcs nincs a lapban.

A korábbi térképes lap megmaradt: `elozo-terkep.html`.
