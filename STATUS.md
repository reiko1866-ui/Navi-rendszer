# ToonNav3D — mi van meg, és mi a következő lépés

Ez a fájl a telefonos állapot. A régebbi leírásokat nem cseréli le.

## Mi készült el

A telefonos oldal az `index.html`. Négy festett képet mutat a `Reference/celkep.png` fájlból:

- nappal
- eső
- éjszaka
- hó

A kép kinézete navigáció: kék autó, város, világoskék sáv, táblák. A kép nem mozog, nem tudja, hol van a telefon, és nem mond valódi következő utcát.

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
3. Nyisd meg Chrome-ban ezt a nappali képet:

https://htmlpreview.github.io/?https://github.com/reiko1866-ui/Navi-rendszer/blob/bc597fc/index.html?hour=14

Ha a nappali kép megvan, ezek a másik három:

- Hó: https://htmlpreview.github.io/?https://github.com/reiko1866-ui/Navi-rendszer/blob/bc597fc/index.html?weather=snow
- Eső: https://htmlpreview.github.io/?https://github.com/reiko1866-ui/Navi-rendszer/blob/bc597fc/index.html?weather=rain
- Éjszaka: https://htmlpreview.github.io/?https://github.com/reiko1866-ui/Navi-rendszer/blob/bc597fc/index.html?hour=22

Ha a linkben nincs óra és nincs időjárás, a telefon órája választ: 7:00–19:00 nappal, 20:00–6:00 éjszaka.

Ennyi a telefonos maximum. A vezethető háromdimenziós út később, számítógépen készülhet. Az sem lesz a festmény mozgó másolata.
