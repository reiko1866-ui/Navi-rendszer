# ToonNav megvalósítási terv

Készült: 2026. november 18., 13:40, Európa/Budapest.

Ez a terv a `cursor/product-gap-audit-bc7e` ág kódjára és a #28-as auditra épül. Ebben a lépésben nem módosult alkalmazáskód, nem készült új APK, és nem kapcsolt be kikapcsolt grafikai útvonal.

A termékcél egy prémium, F1-versenyjáték kamerájú, valós közúti navigáció. A most futó alkalmazás ennél kevesebb: egy MapLibre tervező, amely a megkezdett utat egy unlit Filament-folyosónak adja át. A terv ezt a folyosót tartja meg, és csak olyan lépésben enged új látványt, amelyet egy Redmi telepítés után lehet megítélni.

## 1. Ami most tényleg fut

Ezek forráskódból olvasott tények, nem tesztjelentés.

| Szerep | Ami fut | Ami a projektben van, és nem fut |
|---|---|---|
| Indítás | `MainActivity`, MapLibre térkép | `PlainDriveActivity` nincs a launcherben |
| Vezetés | `RacePrototypeActivity` → `RaceRenderer.startPlain`, mert `LegacyBackdrop.enabled` hamis | `RaceRenderer.start`, `RoadGpu`, `noon-sky.hdr`, KTX2 aszfalt/fű/kavics |
| Világ | `PlainWorld` + `PlainGpu`, `plain_solid.mat`, unlit, árnyék ki | OSM fák és épületek: `Scenery`, csak a kikapcsolt ágon |
| Autó | `models/formula-car.glb`, nyolc festés, `GarageStore` | `car.glb` (304 háromszög) nincs betöltve a vezetéshez |
| Kamera | Szem 1,88 m magasan, 1,72 m-rel a pilóta mögött, előre 46 m | A szem nem dől a lejtővel; a kormánycsomópont nincs elrejtve |
| Magasság | `TerrainTiles` zoom 13, ha a csempe megjön. Ha nem, `roll()` körülbelül 2 m-es hullámot rajzol, üzenet nélkül | A 16 m-es díszdombok már nincsenek |
| Navigáció | `NavigationOwner` egy ülést ad a térképnek vagy a 3D-nek. Mindkét activitynek saját `GuidanceSession`, `OsrmClient` és `VoiceGuide` példánya van | Nincs harmadik útvonalmotor |
| Csomag | `hu.toonnav.map`. A közzétett release ugyanazzal a tanúsítvánnyal van aláírva, mint a telefonon lévő build | A telefon által elutasított fájl debug APK volt |

A JVM-teszt, a szoftveres raszter és a Gradle `BUILD SUCCESSFUL` nem Android-bizonyíték. A Redmi a korábbi plain-world builden szürke utat, zöld talajt, padkát, eget, horizontot, orrot és kerekeket mutatott. Ez felhasználói megfigyelés, nem újranyitott képernyőkép. Az elevation debug APK-t a telefon érvénytelen csomagnak látta. Az új release APK archívuma rendben van, és még nincs róla telepítés.

## 2. Hol tér el a kód az új követelménytől

Ezeket a terv tudomásul veszi. Nem oldja meg őket az 1. szakaszban, kivéve ha a táblázat azt írja.

| Követelmény | Most | Mikor nyúlunk hozzá |
|---|---|---|
| Versenyjátékos külső kamera, orr és első szárny lent, út a középpontban | A szem a pilóta mögött van. A korábbi Filament-képkockákon a kormány és a glória töltötte a kép alját. Azok a képkockák nem a mostani plain világ. | 1b, csak az 1a Redmi-eredménye után |
| Ne legyen kormány, műszerfal, belső nézet | A glTF-ben van `steering_wheel` és `steering_wheel_shell`. A HUD nem műszerfal-utánzat, de sűrű. | 1b: csak a kormánycsomópont, ha a képen látszik. A karosszéria, az orr, a szárny és a kerekek maradnak. |
| Részletes aszfalt, textúra, természetes árnyék | Két unlit szürke. A KTX2 készlet a kikapcsolt ághoz tartozik, és a telefonon fekete vagy magenta világot csinált. | 3. szakasz, egyetlen unlit alapszín, eszközfotó után. Árnyék csak a 4. szakaszban, ha a textúra a telefonon megmaradt. |
| Piros-fehér szegély csak indokolt kanyarban | Már így van: `RoadProfile.kerbs` és `abs(turn) > 0.02`. Autópályán nincs szegély. | 3. szakaszban a küszöb marad. Nem kerül szegély egyenesre. |
| Valós domborzat, ne mesterséges hullám | A csempe valós, durva. Hiányzó csempénél a `roll()` kitalált hullám. | 2. szakasz: hiányzó adatnál sík talaj, látható állapot. A `roll()` kikerül. |
| Fák, erdő, bokor, szikla, térképi növényzet | Nincs az aktív úton. | 3c, külön APK, unlit geometria. Nem a `LegacyBackdrop` bekapcsolásával. |
| Nyolc festés, mentés, 3D garázs | A nyolc festés és a `GarageStore` megvan. `RaceGarageStage` kódban elindul. A meglévő garázskép 2D ikont mutat; a SurfaceView gyakran nem látszik az ilyen képen. | A festéseket nem cseréljük. A 3D előnézet igazolása 4. szakasz, telefonon. |
| Kerekek forognak | Nincs ilyen kód. | 4. szakasz. |
| Egy navigációs állapot | Az ülés egy. A két activity külön példányokat tart, és a `DriveSessionMemory` adja át az utat. | 1. szakasz: teszt, hogy aktív úton a térkép nem beszél és nem vezet. A két példány összeolvasztása nem része az 1. szakasznak, mert az a működő hangot és az újratervezést kockáztatná. |
| A térkép útvonala és a „Nincs útvonal” felirat | Kész, el nem indított tervnél a felirat `hud_nav_ready`: „Útvonal kész”. Az „Nincs útvonal” az üres állapot (`hud_nav_idle`). | Védjük. Nem írjuk vissza. |
| Egy univerzális APK, álló és fekvő, telefon és autós kijelző | Egy APK, négy ABI, `DriveDisplay` képarányfüggő látószög és gombméret. Autós kijelzőn nincs bizonyíték. | 1a a Redmin. Az autós kijelző az 5. szakasz kapuja, nem az 1. szakaszé. |
| `index.html`, `elozo-terkep.html` | Kívül esnek az Android úton. | Nem módosulnak egyetlen szakaszban sem. |

Amit ez az architektúra egy telefonbiztos unlit anyaggal el tud érni: olvasható utat, valós durva lejtőt, külső kamerát, forgó kerekeket és óvatos textúrát. Amit nem ígér: fotóreal hegyvidéket, dinamikus időjárást, vagy egy mai AAA versenyjáték anyagkészletét. A magassági forrás körülbelül 10–20 m-es raszter. A biztonságos anyag unlit. Ezt a korlátot a 3. és 4. szakasz sem lépi át egyetlen PR-ben.

## 3. Szabály minden szakasz előtt

1. A módosítás előtt újra meg kell nézni, hogy `LegacyBackdrop.enabled` hamis-e, és hogy a vezetés `startPlain`-t hív-e.
2. A kikapcsolt ág (`RoadGpu`, HDR, KTX2, `Scenery` a legacy ágon) nem kapcsolható be „hogy szebb legyen”.
3. Egy JVM-teszt geometriát és döntést bizonyít. Filamentet és Redmit nem.
4. A GPS, az OSRM, az újratervezés, a hang, a manőver, a távolság, az érkezés, a STOP és a mentett festés megmarad.
5. A plain világ nem cserélhető le egy üresebb demóra, és a `formula-car.glb` nem cserélhető le a `car.glb`-re.
6. Szakaszonként fut a `:app:testDebugUnitTest`. A jelentés külön sorolja a JVM-et, az emulátort és a Redmit.
7. Ami nincs eszközön megnézve, az nyitott marad. Nem kerül „kész” állapotba.
8. Új APK csak release, univerzális, ugyanazzal a tanúsítvánnyal (`e71ce1188cde57995c924a0b392e630c2d8502fd0016b0aa3387c4f03d116052`), növekvő `versionCode`-dal. Debug APK nem kerül ki. A felhasználónak nem kell törölnie a telepített ToonNavot.
9. A közzétett fájl SHA-256-ja a letöltött másolatéval egyezzen. `unzip -t`, `zipalign -c -v 4`, `apksigner verify --verbose --print-certs` és `aapt dump badging` a letöltött fájlon fut, nem csak a helyi Gradle-kimeneten.

## 4. Első szakasz, ahogyan biztonságosan megépül

Az 1. szakasz két kapu. Az 1a nem nyúl a képhez. Az 1b csak akkor kezdődik, ha az 1a a Redmin lefutott.

### 4.1. 1a — a már meglévő release igazolása

Nincs új build. A telepítendő fájl a már közzétett univerzális release:

https://github.com/reiko1866-ui/Navi-rendszer/releases/download/toonnav-universal-88ca57cb64c8/toonnav-universal-88ca57cb64c8.apk

| | |
|---|---|
| Méret | 106 385 480 bájt |
| SHA-256 | `9a92f81637a7339d123fd8aaa9168fc40df84b964bc335d88217ca090cf1d243` |
| Csomag | `hu.toonnav.map`, versionCode 2, versionName 1.1 |
| Aláírás | v1, v2 és v3. A tanúsítvány a telefonon lévő buildé. |
| ABI | arm64-v8a, armeabi-v7a, x86, x86_64 |

Ezt a fájlt az előző vizsgálat a GitHubról töltötte le, és bájtra egyezett a helyi release-szel. Az archívum rendben volt. Telepítés nem történt: az emulátor adb-je offline maradt, telefon nem volt a gépen.

Ezen a felhőgépen nincs Redmi, és egy x86 emulátor akkor sem Redmi, ha később elindul. Az 1a ezért így zárul:

1. Itt újra lefutnak a navigációs és a plain-világ JVM-tesztek. Eredményük a jelentésben „JVM” címkét kap.
2. Ha az emulátor adb-je online lesz, `adb install -r` lefut, és az eredmény „emulátor, nem Redmi” címkét kap. Az 1a ettől még nem kész.
3. A Redmi-checklistát a telefon tulajdonosa futja. Az 1a csak akkor kész, ha a checklista válaszai megvannak. Két-három fénykép elég: a térkép előnézete indulás előtt, a 3D kép indulás után, és a képernyő STOP után.

Redmi-checklista, igen vagy nem:

1. Telepítés törlés nélkül. A már lévő ToonNav frissül, az adatai megmaradnak.
2. Az alkalmazás a térképre nyílik, nem omlik össze.
3. Egy közeli címre készült útvonal vonala látszik, és a felirat „Útvonal kész”, nem „Nincs útvonal”.
4. Indítás után a 3D nézet jön fel, és a képernyő nem alszik ki.
5. Látszik út, talaj, ég, és az autó orra. A világ nem fekete, nem rózsaszín és nem fehér. Külön megjegyzés: látszik-e kormánykerék.
6. Sebesség, következő manőver, manőver távolsága, hátralévő út és érkezési idő olvasható, és nem takarja ki a teljes utat.
7. A STOP, a hang és a garázs gomb elérhető.
8. A STOP megállítja a navigációt, és visszaadja a térképet.
9. Újraindítás után a kiválasztott festés megmarad.
10. Álló helyzetben a gombok a rendszer sávja felett maradnak.

Amit az 1a szándékosan nem kérdez: aszfaltminőség, fák, árnyék, autós kijelző. Azok későbbi kapuk.

Ha a telepítés megint „érvénytelen csomag”, megállunk. Nem készül új grafika. A következő vizsgálat a telefon telepítőjének üzenete és az, hogy a fájl a fenti SHA-256-e. Második APK-t csak ennek a tisztázása után szabad kiadni.

Ha a 3D kép fekete, rózsaszín vagy fehér, a plain ág a telefonon regresszált. Akkor sem kapcsol be a legacy ág. A javítás a plain indítás hibája, és újra az 1a checklistán megy át.

### 4.2. 1b — kamera, csak az 1a fotója után

Akkor nyílik meg, ha az 1a ötödik pontja azt mondja, hogy a kormány vagy a belső nézet uralja a képet, vagy az orr kilóg.

Engedett módosítás:

- `RaceCamera` szemének emelése és hátrébb vitele úgy, hogy a nézet a karosszéria fölül és mögül nézzen előre.
- Ha a kormány ennek ellenére a képben marad: a `steering_wheel` és `steering_wheel_shell` entitás elrejtése. Más csomópont nem tűnik el. Az orr, az első szárny, a kerekek és a karosszéria marad.
- A JVM keretezés továbbra is megköveteli, hogy az orr normalizált képernyő-helyzete a képben legyen. A 0,64-es lookLocalY korábban −1,01-re tette az orrot, és elbukott. Ez a bukás nem jöhet vissza.

Tilos ebben a lépésben: új anyag, textúra, HDR, árnyék, `LegacyBackdrop`, terepmódosítás, navigációs újraírás.

Fájlok: `RaceCamera.kt`, és ha az elrejtés kell, `RacePrototypeActivity.kt` vagy `RaceLivery.kt` egy rövid hide. Teszt: `PlainWorldTest` vagy a kamera keretező tesztje.

Bizonyíték: a JVM keretezés, majd egy új release APK (versionCode 3, ugyanaz a tanúsítvány), majd ugyanaz a Redmi-checklista az 5. ponttal: orr és első szárny lent, kormány nincs a kép közepén, az út látszik. Emulátorkép nem zárja a kaput.

Ha az 1a fotóján a kormány eleve nincs a képben, és az orr látszik, az 1b nem készül el. Nincs céltalan kameramozgatás.

### 4.3. Amit az 1. szakasz jelentése tartalmaz

- Érintett fájlok. Az 1a-nál: nincs.
- JVM-tesztek: parancs, sikeres és bukott esetek.
- Emulátor, ha volt: `adb install` kimenete, és a mondat, hogy ez nem Redmi.
- Redmi: a tíz pont igen/nem, és a fényképek.
- Nyitott hibák, köztük a kormány, ha látszik.
- APK: a 2-es verzió változatlan azonosítója, vagy 1b esetén az új fájl URL-je, bájtmérete és SHA-256-ja.

## 5. Második szakasz — valós terep

Csak kész 1a után. Nem hoz textúrát.

Cél: az út, a talaj és az autó egy magasságon legyen, a hiányzó adat ne rajzoljon hamis dombot, és a vezető lássa, ha a domborzat nem jött meg.

Megvalósítás:

- `TerrainTiles.load` eredménye egy állapot lesz: töltés, kész, vagy nincs adat. A csendes `return@Thread` megszűnik.
- A HUD egy rövid sort kap, amíg tölt vagy nincs adat, majd eltűnik. Nem kerül ki csempeszám, nyers méter, vagy fejlesztői napló. A 10. követelmény miatt a sebesség, a manőver, a távolság és az érkezés marad a fő adat.
- `PlainWorld.roll()` kikerül a vezetési útból. Nincs adatnál a talaj az út magasságán, a padka alatt marad, és nem emel kitalált hullámot. A meglévő 60 m-es tüske-szűrő és a 16 m-es vállon kívüli átmenet marad.
- A kocsi dőlése a már meglévő `gradeRadians` (5 m hátra, 7 m előre). A szem dőlését ehhez a lejtőhöz kötjük, és a keretező teszt újra lefut, hogy az orr a képben maradjon.
- A rács marad. Finomabb közeli cella csak akkor, ha a Redmi képkockaideje a 42 ms-os határ alatt marad. A 320 cellás durvítás nem kerül ki előbb.

Fájlok: `TerrainTiles.kt`, `PlainWorld.kt`, `RaceTrack.kt`, `RacePrototypeActivity.kt` (`requestElevation`, `draw`), `PlainWorldTest.kt`, egy rövid string a `strings.xml`-ben.

JVM: sík út hiányzó mezőnél; a talaj nem metszi az aszfaltot; egy hajtűkanyar nem kap oldalsó dombot; a tüske nem dobja fel az autót.

Android: egy ismert emelkedős útvonal a Redmin, hálózattal. A második próba ugyanaz az útvonal repülőgépes módban a terv után, hogy a „nincs adat” sor megjelenjen, és a talaj sík maradjon. A szoftveres raszter csak geometriai ellenőrzés, és a jelentésben így szerepel.

Tilos: `LegacyBackdrop`, HDR, KTX2, díszdomb visszaépítése, OSRM-parser módosítása.

## 6. Harmadik szakasz — út és környezet

Három külön APK, nem egy.

3a. Jelzések. A meglévő padka, szélvonál és szaggatott középvonal marad. A piros-fehér szegély feltétele nem lazul. Autópálya-egyenesre nem kerül szegély. A közút nem lesz versenypálya: nincs extra pályaívek, nincs a valós vonaltól eltérő ív.

3b. Egy unlit aszfalt-alapszín. Egy kép, egy anyagparaméter, feature level 1. Nincs normál térkép, nincs ORM, nincs KTX2, nincs HDR. Ha a Redmi-fotó fekete, rózsaszín, fehér, vagy eltűnik a világ, ez az egy asset visszaáll, és a szakasz megáll.

3c. Növényzet, csak ha a 3b a telefonon megmaradt. A `Scenery` geometriai számítása átemelhető unlit `PlainGpu` meshbe. A legacy indítás nem. Üres OSM-válasz üres tájat ad, nem hibás világot. A távoli példány ködbe esik, és nem pattan be egy kockán. Darabszám-plafon a képkockaidőhöz kötött. Szikla és bokor csak ott, ahol a forrásadat vagy a durva lejtő ad helyet. Nem szórunk díszletet a semmibe.

Fájlok 3b-nél: `plain_solid.mat` csak ha egy második unlit paraméter kell, egy új kép, `PlainGpu.kt`. 3c-nél: `Scenery.kt` olvasása, új unlit feltöltés, nem a `refreshScenery` legacy ága.

## 7. Negyedik szakasz — fény, autó, garázs

Csak akkor, ha a 3b anyaga a Redmin látszik.

- Árnyék először a már bizonyított anyagon, kis térképpel. Ha a képkocka a 42 ms fölé megy, az árnyék kikapcsol, a világ marad.
- Az ég továbbra is geometria: több sáv és egy napkorong. A `noon-sky.hdr` nem kerül a vezetési útra.
- A nyolc festés és a `GarageStore` változatlan. Egy festésparaméter egyszerre, telefonfotóval. A carbon, a gumi és az üveg addig marad, amíg a festékfotó megvan.
- A kerekek a GPS-sebességből forognak. Az első kerekek kanyarodása a vonal görbületéhez kötött, kis szöggel.
- A garázs `RaceGarageStage` a telefonon készül képernyőkép. Addig a 3D előnézet nincs késznek mondva. A 2D ikon maradhat tartaléknak, ha a SurfaceView nem indul.

Fájlok: `RaceLivery.kt`, `RaceGarageStage.kt`, `RacePrototypeActivity.kt` transzformja. A `formula-car.glb` geometriája nem cserélődik.

## 8. Ötödik szakasz — telefon, autós kijelző, tartós út

- Tiszta release egy olyan fából, amely csak a gitben lévő fájlokat tartalmazza. A 107 MB-os `hungary-addresses.sqlite` nincs a gitben, de a mostani közzétett APK-ban benne van, mert a munkakönyvtárból csomagolódott. A tiszta build Photonra esik vissza. Ezt a szakaszt ez előtt el kell dönteni: vagy a keresés Photon, és a fájl nem kerül az APK-ba, vagy a címadatbázis tudatosan, reprodukálhatóan kerül a fába. Nem maradhat „a gépen véletlenül ott volt” állapot.
- Ugyanaz a telepítési vizsgálat, mint az 1a-nál, a végső SHA-256-tal.
- Redmi: keresés, indulás, egy letérés és újratervezés, egy hangos manőver, érkezés, STOP, festés megmarad, nappal és este, álló és fekvő.
- Autós kijelző, ha elérhető: ugyanaz az APK, fekvő kép, a hat gomb elérhető, az út széle a képben marad. Ha a kijelző nincs kéznél, a szakasz ezt nyitottként írja, és nem helyettesíti emulátorral.
- Hosszabb út: a felhasználó jelzi, ha a kép megakad, a telefon túlmelegszik, vagy a beszéd elhallgat. A `publishFrame` 42 ms-os számlálója a jelentésbe kerül, ha a készülék ad ilyen sort. Hőmérsékletet ez a környezet nem mér.

## 9. Android-bizonyíték, ahogyan használt lesz

| Bizonyíték | Mit jelent | Mit nem jelent |
|---|---|---|
| `:app:testDebugUnitTest` | Döntés, geometria, felirat, egy ülés. | Filament, GPU, telepítés. |
| `unzip`, `zipalign`, `apksigner`, `aapt` a letöltött APK-n | A fájl egy aláírt, univerzális csomag. | A Redmi telepítője elfogadja. |
| Emulátor `adb install` | A csomag egy x86 rendszerképen települ. | Redmi vagy autós kijelző. |
| Redmi-checklista és fénykép | Az 1a, majd minden későbbi látványkapu. | PC-s kép helyettesítheti. |
| Autós kijelző | Az 5. szakasz széles képe. | Az 1. szakasz lezárása. |

A felhőgép nem éri el a Redmit. Ezért egy látványszakasz lezárása a telefonról visszajövő igen/nem és a fénykép. Amíg az nincs meg, a szakasz nyitott, akkor is, ha a Gradle sikeres.

Az 1a nem vár új kódra. A már közzétett versionCode 2 a próba. A következő kód, az 1b vagy a 2. szakasz, csak a checklista után indul.

## 10. Védendő, és ami nyitott marad

Védendő minden szakaszban: `hu.toonnav.map`, a meglévő tanúsítvány, a négy ABI, egy APK, a MapLibre indulóképernyő, az OSRM, a GPS-kapu, az újratervezés, a hang, az érkezés 30 m-nél, a STOP, a nyolc festés, a `GarageStore`, az „Útvonal kész” felirat, az unlit plain út, a `formula-car.glb`, és a két HTML fájl.

Nyitott, amíg eszköz nincs rá: a versionCode 2 telepítése, a mostani kamera a Redmin, a domborzatcsempe élő úton, a garázs 3D képe, a hang megszólalása ezen a builden, a képkockaidő, és minden textúra.
