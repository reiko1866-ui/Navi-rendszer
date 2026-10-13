# ToonNav3D — mi van meg, és mi a következő lépés

Ez a fájl a telefonos állapot. A régebbi leírásokat nem cseréli le.

## Mi készült el

A telefonos oldal az `index.html`. A kép a három célfotó szerint néz ki: piros autó hátulról, egyenes út, színes fák, nappal zöld sáv, éjjel világító kék sáv. Fent a kanyar: 250 m, Fő út / Váci út. Lent az 50-es tábla és a sebesség.

A nappal és az éjszaka a telefon óráját követi. 7-től 19 óráig nappal, 20-tól reggel 7-ig éjszaka. Nyitáskor először a másik hangulat látszik, pár másodperc múlva magától átvált a valódira. Utána is figyeli az órát.

A számítógépre szánt vázlatok:

- `Assets/Scripts/World/CarMovement.cs`
- `Assets/Scripts/World/CameraFollow.cs`
- `Assets/Scripts/Core/DayNightController.cs`

Ezek szövegfájlok. Unity-jelenet nincs belőlük összerakva. A játékot senki nem indította el, és a telefonon nem is lehet.

## Mit ne csinálj a telefonon

Ne telepíts Unityt a Play Áruházból. A Unity szerkesztő számítógépen fut.

## A következő lépés a telefonon

1. Zárd be a régi ToonNav lapokat.
2. Nyisd meg Chrome-ban. A pontos link a beszélgetésben van.
3. Várd meg a váltást. A zöld nappali kép magától sötét éjszakai képre vált, vagy fordítva, ahogy az óra kéri.
