# ToonNav3D — mi van meg, és mi a következő lépés

Ez a fájl a telefonos állapot. A régebbi leírásokat nem cseréli le.

## Mi készült el

A telefonos oldal az `index.html`. Az autó egy előre megírt útvonalon megy. Az útvonal pontokból áll: utca, kanyar, távolság. A bal felső szám csökken, a kanyarnál új utca jön, a kis térkép mutatja a vonalat.

Ez a később fejleszthető navigáció váza. Most nem a telefon valódi helyét használja. A festett képek megmaradtak a `Reference/panels` mappában.

A számítógépre szánt vázlatok:

- `Assets/Scripts/World/CarMovement.cs`
- `Assets/Scripts/World/CameraFollow.cs`
- `Assets/Scripts/Core/DayNightController.cs`

Ezek szövegfájlok. Unity-jelenet nincs belőlük összerakva. A játékot senki nem indította el, és a telefonon nem is lehet.

## Mit ne csinálj a telefonon

Ne telepíts Unityt a Play Áruházból. A Unity szerkesztő számítógépen fut.

## A következő lépés a telefonon

1. Zárd be a régi ToonNav lapokat.
2. Fordítsd a telefont fektetve.
3. Nyisd meg Chrome-ban. A pontos link a beszélgetésben van, mert minden új változat új cím.

Nézd a bal felső számot. Csökken, aztán a következő utca neve kicserélődik. A végén megjelenik a Megérkeztél, és az Újra gomb elölről indítja. Alul a Nappal, Éjszaka, Eső és Hó gomb a hangulatot váltja.
