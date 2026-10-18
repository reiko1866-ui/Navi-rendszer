# ToonNav3D

Rajzfilmes, tengerparti 3D-s út a böngészőben. Telefonon, álló helyzetben is használható: a kereső keskeny, a találatok görgethetők, az útvonal részletei alapból csukva vannak.

Nyisd meg az `index.html` lapot. Útvonal nélkül az autó a tengerparti tájon áll. Nem indul el magától, és a felület nem mutat kitalált távolságot, érkezési időt vagy sebességet.

A felső Keresés sávban megadható az indulás és a cél. A keresés a Photon demót használja (OpenStreetMap, kulcs nélkül). A találatok a válaszból jönnek. Üres válasz, hibás cím vagy hálózati hiba esetén a lap ezt írja ki, és nem tölt be mintacímet. Ha mindkét pont ki van választva, a lap közúti útvonalat kér a FOSSGIS OSRM szerverétől (`routing.openstreetmap.de`, legfeljebb egy kérés másodpercenként, © OpenStreetMap, OSRM). A táv és a tervezett idő ebből a válaszból származik. A hátralévő idő a tervezett idő és a hátralévő szakasz aránya, nem élő forgalmi becslés.

A megjelenített út a valódi OSRM-vonal ívesített középvonala: a kanyar a sarok előtt elkezdődik, a kocsi helye és az út iránya ugyanabból a görbéből jön, és a kormányzás idő alapján simul. A GPS gomb a böngésző helyengedélyét kéri, majd figyeli a helyzetet. A kocsi csak akkor halad az úton, ha van élő, elég pontos helyzet, és az az útvonal 80 méteres közelébe esik. Két jel között a mért sebességgel gördül tovább az úton, de nem hagyja el a legutóbbi helyzetet nagy távolságra, és apró zajtól nem rángatja a kormányt. Ha a GPS az útvonalon kívül van, a kocsi nem ugrik rá, és a lap ezt kiírja. Engedély megtagadása, pontatlan hely, nem biztonságos cím (`https` vagy `localhost` kell) vagy hiányzó helyadat esetén nincs szimulált helyzet. Android WebView-ban a helyengedély és a WebView geolocation engedélyezése is kell.

A jobb alsó Garázs gomb három autót kínál: Ferrari F1 SF90 (valvetin, CC BY 4.0), egy nyitott kerekű formula-autó (3D Assets, CC0), és a saját sportkompakt. A választás a böngészőben megmarad. A panel nem nyílik meg magától, és útvonal indításakor bezárul. A források a `sports-car-license.txt` fájlban vannak. A nap/hold gomb fokozatosan vált éjszakára.

A korábbi térképes lap megmaradt: `elozo-terkep.html`.
