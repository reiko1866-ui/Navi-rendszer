# ToonNav product gap analysis

Audit date: 18 November 2026, 13:00 Europe/Budapest.

This is an audit of the repository as it stands on `cursor/installable-apk-bc7e` (`88ca57cb64c80d50cea43d3727c1a1ea36f9a228`). No application code, graphics, or APK was changed to produce it.

The intended product is a premium Formula-style 3D navigation application: a convincing outdoor world, a real road with elevation, a detailed car, and a working navigator on a phone and on a wide Android car display.

The application that exists is a working navigation shell that hands a live trip to a Filament prototype. The prototype that actually draws on the phone is an unlit, untextured corridor. A second, textured renderer is still in the project and is switched off, because turning it on made the phone draw a black or magenta world.

## How to read the evidence

| Tag | Meaning |
|---|---|
| SOURCE | Read from the current Kotlin, material, manifest, or asset. A class existing is not treated as the feature being on. |
| JVM TEST | A unit test encodes the behavior. The last recorded run in this project was 167 tests, 0 failures, before the release-packaging edit. This audit did not re-run them. A JVM test does not draw with Filament and does not run on a phone. |
| DESKTOP RENDER | A PNG under the investigation artifacts. The `road-flat`, `road-uphill`, `road-curve`, `road-beside`, and `plain-world-*` images are software rasters of `PlainWorld` colors. They are not Filament and not a phone. |
| ANDROID HARDWARE | A check against an APK with `unzip`, `zipalign`, `apksigner`, or `aapt`, or a real install. |
| USER OBSERVATION | What was reported from the Redmi after the plain-world build. There is no Redmi screenshot or driving video in the repository. |
| UNKNOWN | Not established. |

---

## 1. Executive summary

The navigator is real. `MainActivity` is the launcher. It searches, asks OSRM for a route, then starts `RacePrototypeActivity` for the drive. GPS follow, reroute, spoken prompts, maneuver text, remaining distance, ETA, arrival, and STOP are implemented on that drive screen and covered by unit tests. Whether the latest APK still does all of that on a phone is UNKNOWN, because that APK has not been installed.

The 3D world the phone is allowed to draw is `PlainWorld` through `PlainGpu`. `LegacyBackdrop.enabled` is `false`. While that flag stays false, the drive does not load `noon-sky.hdr`, does not build the textured road glTF, and does not place OpenStreetMap trees or buildings. Road, shoulder, grass, markings, and sky are flat colors on an unlit material (`plain_solid.mat`, shading model `unlit`). Shadows and ambient occlusion are off. Vertex normals are `(0, 1, 0)` and texture coordinates are `(0, 0)`, so a slope cannot shade and a texture cannot tile even if one were attached later without a geometry change.

Elevation is half-finished. The road can follow Mapzen terrarium heights at zoom 13, smoothed over about 16 m, and the car pitches to that grade. The same heights can lift the terrain grid. A failed download leaves the road flat and the surroundings as a sine wave of about two meters, and the failure is not shown to the driver. Zoom 13 is on the order of 10–20 m per sample in Hungary, so this is a coarse grade, not a landscape of hills and valleys. The old 16 m decorative hills were removed because a later bend drove through them.

The car mesh is a real Formula model: `models/formula-car.glb`, about 32,774 vertices and 46,816 triangles, with named body, wings, and four wheels. Eight liveries recolor existing `baseColorFactor` parameters. Wheels do not rotate. The garage screen's supplied screenshot shows a flat 2D icon; a Filament garage stage exists in source and is UNVERIFIED on a device. Carbon, tire, and clearcoat are whatever the glTF ubershader does with the file. That has not been judged on a phone beyond the report that the nose and the front wheels were visible.

The camera is a chase camera 1.88 m up and 1.72 m behind the cockpit, looking 46 m down the road, with the field of view widened or narrowed from the screen aspect. Bearing and pitch are damped. The eye rig itself stays level in world space; only the look height and the car's pitch follow the grade.

Visual quality is a prototype, not a racing game. Honest scores are in section 4. The largest visible gap is that the environment is flat-shaded geometry with no land cover, no vegetation, no asphalt material, and no light response. The largest technical risk is turning the old textured path back on. That path is what produced the black world and the pink and white materials.

The APK the phone rejected (`toonnav-plain-ec4eb4e7900d.apk`, 114,967,856 bytes) is a complete, zip-aligned, v1+v2 signed debug package. It matches the local debug build byte for byte. It is not a truncated download, not an App Bundle, and not a split. It carries `android:debuggable` and has no v3 signature. The certificate is the same one already on the phone. Do not uninstall. A newer non-debuggable universal APK has been published and passes the same archive checks, including v3. No Android install of that file has succeeded: the emulator stayed offline and no phone was attached.

The foundation to keep is the navigation behavior and the unlit corridor that the phone could actually show. The foundation to change, carefully and only after a device proves each step, is the world model: shared elevation, then unlit detail, and only then any new texture. Filament does not need to be replaced to do the next geometric step. It does need a device proof before any material that previously failed is used again.

---

## 2. Expected versus actual

Severity is the cost of the gap against the premium navigation product: Critical means the phone cannot install or cannot see a world; High means a core promised quality is missing on the path the phone runs; Medium means the feature exists in a reduced form; Low means a polish item.

| Feature | Expected premium result | Actual implementation | Gap | Severity | Evidence | Recommended solution |
|---|---|---|---|---|---|---|
| 3D rendering architecture | One outdoor renderer, lit, stable on Mali and Adreno. | Filament 1.75, OpenGL, glTF ubershader. The drive calls `RaceRenderer.startPlain` because `LegacyBackdrop.enabled` is false. `RoadGpu`, HDR, and `Scenery` run only in the other branch. | Two renderers share one activity. The one that looks more like a game is the one that failed on the phone. The safe one is unlit flat color. | High | SOURCE: `LegacyBackdrop`, `RacePrototypeActivity` engine start, `PlainGpu`. USER OBSERVATION: plain world visible; textured world black or magenta. | Keep the plain path as the only release path. Do not flip the flag. Quarantine the legacy branch so a release build cannot compile it in. |
| Terrain and elevation | Natural hills, valleys, and slopes from real ground. | `TerrainTiles` fetches Mapzen terrarium zoom 13. Height is `red*256 + green + blue/256 - 32768`, stored relative to the first route point, then `RouteElevation.smooth` over 16 m. A failed tile returns null and the caller returns without a message. With no field, terrain is `roll()`, a sine of at most about 2.3 m. | Real data exists and is coarse. Failure looks like a flat road. There is no valley or mountain mesh beyond a 200 m grid. | High | SOURCE: `TerrainTiles.load`, `PlainWorld.heightAt`, `PlainWorld.roll`. JVM TEST: `PlainWorldTest` elevation cases. ANDROID HARDWARE: UNKNOWN whether a live route on the Redmi received tiles. | Keep the DEM. Surface a failure. Use a finer zoom near the car, and keep the coarse tile only as fallback. Do not invent hills beside the carriageway. |
| Road geometry | Smooth curves, width that matches the road, continuous surface. | OSRM lon/lat becomes local meters in `RaceTrack.samplesFor` (2.5 m step, 4 m on routes over 40 km). `PlainWorld.refine` Catmull-Rom's the plan and linearly blends elevation so the curve cannot overshoot in height. Width comes from `RoadProfile.of`. | The centerline is the route overview, not surveyed lanes. Curves are smoother than the raw polyline. Junctions, bridges, and tunnels are not modeled. | Medium | SOURCE: `RaceTrack`, `PlainWorld.refine`, `RoadProfile`. JVM TEST: ribbon and hairpin tests. | Keep the ribbon. Add bridge and tunnel as omissions to accept for now, or as later explicit gaps in the mesh. Do not add a parallel invented road. |
| Road and terrain alignment | One height field. The car never meets the ground. Terrain never crosses the asphalt. | Inside the carriageway, terrain Y is road elevation minus 0.08 m. The shoulder blends down. Past the shoulder, height blends over 16 m to the DEM or to `roll()`. The car Y is sample elevation plus `deckTop` (0.06 m). | The shared model exists. Grid cells of 3.2 m and 18 m can stair-step a slope. A cell uses the nearest route sample, so a bend can still pick a neighbor grade. The old dome hills that the car drove through are gone. | Medium | SOURCE: `PlainWorld.heightAt`, `fillGrid`, `RacePrototypeActivity.draw`. JVM TEST: corridor and hairpin asserts. ANDROID HARDWARE: UNKNOWN. | Keep one field. Tighten the near grid only as far as a phone frame time allows. Reject any lateral dome. |
| Hills and mountains | Distant ridges and a landscape that reads as land. | Far terrain stops at 200 m and is colored by a sine patch, then by seven haze bands. Sky rings sit at a 340 m radius. No mountain mesh. | The horizon is a color fade, not land. A real hill exists only as the coarse DEM under that fade. | High | SOURCE: `farReach`, `materialFor`, `PlainGpu.buildSky`. DESKTOP RENDER: `road-beside.png` shows a low side slope and then a flat horizon. | A wider, coarser DEM drape can suggest ridges without trees or new shaders. It will not look like a game landscape until land cover exists, and land cover is a later, riskier step. |
| Asphalt appearance | Detailed asphalt. | Two unlit greys, `plain-road` `(0.36, 0.36, 0.35)` and a slightly darker shade on every fifth 18 m segment. UVs are zero. The KTX2 asphalt set is in the APK and is not sampled on this path. | The road reads as a grey polygon. The shade band is a stripe, not aggregate, wear, or wetness. | High | SOURCE: `PlainWorld.meshes`, `Strip.addPoint`, `plain_solid.mat`. DESKTOP RENDER: `road-flat.png`. USER OBSERVATION: grey asphalt on the phone. | Stay unlit. A single base-color asphalt image is the first texture worth trying, and only after a Mali and an Adreno screenshot. No normal map and no ORM map on this path. |
| Grass and vegetation | Natural ground cover, trees, verges. | Grass is four flat colors chosen by `sin(east)*cos(north)`. `Scenery` can fetch OSM trees and buildings and is not called while the legacy flag is false. | No trees, no buildings, no blades, no land-cover map. The green is a mathematical patch. | High | SOURCE: `PlainWorld.materialFor`, `refreshScenery` returns immediately. DESKTOP RENDER: flat green in the software rasters. | Do not enable `Scenery` by flipping the legacy flag; that flag also loads HDR and the textured road. If trees return, they need their own unlit meshes and a device test. |
| Road shoulders and kerbs | Shoulders and kerbs that follow the road class. | Shoulder and verge are flat bands from `RoadProfile` (motorway half-width 5.25 m and a 0.6 m shoulder; a main road 3.75 m and 1.4 m). Kerbs are red and white quads only when the class allows them and the turn exceeds 0.02. | The geometry is there. It is a solid brown strip and a hard red/white block. No gravel texture, no curb profile. | Medium | SOURCE: `RoadProfile`, `PlainWorld.meshes`. DESKTOP RENDER: `road-curve.png`. | Keep the profiles. A shoulder cross-section and a shorter kerb period are geometry-only improvements. |
| Sky and atmosphere | A sky with depth, and haze that matches the horizon. | Five untextured cylindrical bands, horizon color first. Fog color is the horizon, density 0.0032, max opacity 0.70, `fogColorFromIbl` false. `noon-sky.hdr` is not opened on this path. Night swaps the band colors. | The sky is five flat rings. No clouds, no sun disc, no aerial perspective beyond one fog color. | High | SOURCE: `PlainGpu.buildSky`, `startPlain` fog setup. DESKTOP RENDER: a flat blue upper half. | Keep the untextured sky. More bands and a sun disc as geometry are safe. The HDR file stays off the drive path. |
| Lighting and shadows | Sun, shade, and contact under the car. | Unlit material. Sun intensity is 3.2 lux and `castShadows` is false. AO is off. `reduceDetail()` can turn dynamic resolution down to 0.45 after four frames slower than 42 ms. The legacy branch uses a 100,000 lux sun and sunny-16 exposure; that branch is not started. | The sun does not change the road or the ground. The car is shaded only by whatever the ubershader does with a flat indirect light. | High | SOURCE: `plain_solid.mat`, `startPlain`, `PlainGpu.upload`. | Do not enable shadow maps on the unlit world; they have nothing to receive. Lit materials are a separate experiment from the world color, one object at a time, on a device. |
| Car model quality | A convincing Formula car, with nose, front wing, and front wheels in view. | The loaded file is `formula-car.glb` (CC0, glTF-Transform). Nodes include body, both wings, DRS flap, steering wheel, and four wheel meshes. Materials include paint, carbon, tire, rim, visor, and clearcoat and emissive extensions. `car.glb` (304 triangles, unlit) is in the APK and is not the drive model. Wheels are not animated. | The mesh is good enough to read as a Formula car. The presented materials, the still wheels, and the lack of a device beauty shot keep it from looking premium. One desktop failure image shows the tires as flat white. | High | SOURCE: glTF header of `formula-car.glb`; `RacePrototypeActivity` loads that path. DESKTOP RENDER: `road-nose-filament.png` is an older Filament capture, not the current unlit world. USER OBSERVATION: nose and wheels visible on the plain-world phone build. | Keep this mesh. Do not replace it with `car.glb`. Animate the wheels from speed. Judge paint on a phone screenshot before changing any material parameter. |
| Car paint and color selection | Several premium liveries, saved, and used on the drive and the map. | Eight cars in `GarageFleet`: piros, kek, zold, lila, ezust, fekete, narancs, turkiz. `LiveryPainter` sets `baseColorFactor` on paint, wings, rims, and nuts. Carbon, tire, and wheel cover are not recolored. `GarageStore` persists the id. Legacy ids map onto the new ones. | The colors exist. The supplied garage image is a 2D icon, so the 3D preview is UNVERIFIED. Liveries are tints, not a new paint finish. | Medium | SOURCE: `GarageFleet`, `RaceLivery`, `GarageStore`. DESKTOP RENDER: `liveries.png` is a swatch list; `garage-piros.png` shows the 2D marker. | Keep the eight liveries and the store. Prove the garage `SurfaceView` on a device before restyling it. |
| Cockpit and camera positioning | Nose, front wing, and front wheels visible, with a clear view of the road. | Eye at local `(0, 1.88, -1.72)`, look height 0.42, look-ahead 46 m, near plane 0.08 m. `DriveDisplay.verticalFov` targets about 72 degrees across the width, clamped from 34 to 92 degrees vertical. | The constants were set so the nose stays inside the frame in the JVM framing test. A look height of 0.64 put the nose at NDC y about -1.01 and failed that test; 0.42 passed. Device framing of the current camera is UNKNOWN. | Medium | SOURCE: `RaceCamera`. JVM TEST: `PlainWorldTest` framing. | Keep the nose in frame. Do not raise the camera to hide the car. Confirm on a phone and on a wide display before moving the rig again. |
| Camera movement | Smooth follow of the car and the grade. | Bearing is damped with a 0.12 s time constant and a 170 deg/s cap. Pitch uses the elevation 5 m behind and 7 m ahead, damped at 0.18 s, and is applied to the car. Look height is damped at 0.22 s. The camera matrix is not pitched with the chassis; world up stays up. | Follow exists. There is no speed FOV, no lean, no curb-to-curb look, and no camera pitch on the eye itself. | Medium | SOURCE: `RacePrototypeActivity.draw`, `dampBearing`, `dampPitch`, `RaceCamera.cameraModel`. ANDROID HARDWARE: UNKNOWN. | Add eye pitch with the same grade before any new camera behavior. Verify it does not push the nose out of frame. |
| Navigation UI | A polished, coherent interface. | Planning is MapLibre in `MainActivity`. Driving is a Filament view plus `DriveChrome`: speed, maneuver, remaining distance, ETA, STOP, voice, map, garage, night, and livery. Portrait splits the buttons. Insets pad the Android navigation bar. Text is Hungarian. | Two visual languages. The 3D HUD is functional and dense. It is not a racing-game instrument panel. Wide-car layout is specified in code and UNVERIFIED on a car display. | Medium | SOURCE: `DriveDisplay`, `DriveChrome`, `activity` layouts. JVM TEST: `DriveDisplayTest`. | Keep one HUD implementation. Do not redesign it until the world is worth framing. Check a portrait phone and a wide display when the next APK is installed. |
| Phone compatibility | Installs and draws on a current phone, including a Redmi. | `minSdk` 21, `targetSdk` 35, OpenGL ES 3 requested and not required. GLES 1–2 shows a failure string and does not start the engine. The plain path is what the Redmi could draw. The rejected file is a debug APK. | Install of the elevation debug APK failed. Install of the newer release APK is UNKNOWN. Drawing of the plain world was reported on an earlier plain-world build. | Critical for install, High for the visual target | ANDROID HARDWARE: archive checks below. USER OBSERVATION: plain world visible on the earlier build; elevation APK refused. | Ship the non-debuggable universal APK. Confirm install on the Redmi before any further graphics change. |
| Wide-screen Android compatibility | The same APK, with a framing that uses the width. | One APK, no split. `DriveDisplay` changes vertical FOV with aspect and enlarges touch targets when the short side is at least 600 dp or the width is at least 1000 dp. `configChanges` includes screen size and smallest width. | The policy is in source. No car-display install or screenshot exists. | Medium | SOURCE: `DriveDisplay.layout`, manifest. ANDROID HARDWARE: UNKNOWN. | Keep one APK. The first wide-screen proof is a screenshot of the drive HUD and the road edges, not a second package. |
| Rendering performance | Smooth on a phone and a car unit. | Unlit, no shadows, position-only vertices, FXAA, post-processing on. Chunks cover the car plus the road ahead (`window` is 220 m behind and 1,700 m ahead). A near grid step of 3.2 m coarsens if an axis would exceed 320 cells. Four slow frames drop resolution to 0.45. | The cheap path is the one that ships. Frame time on a Redmi or a car unit is UNKNOWN. The elevation rebuild runs when tiles return and can hitch. | Medium | SOURCE: `PlainGpu`, `publishFrame`, `reduceDetail`. ANDROID HARDWARE: UNKNOWN. | Measure frame time on the phone before adding triangles. The grid step is the first knob. |
| GPS and routing reliability | A route that follows the driver and recalculates. | GPS updates in both activities. Trust is 40 m accurate, 120 m usable, lost after 8 s. OSRM is `https://routing.openstreetmap.de/routed-car/...` with overview full and steps. A third coordinate is ignored. Reroute has its own session and a cooldown. The car is placed by `RouteMotion` on the polyline. | The logic is complete in source and tests. It depends on a public router and on a GPS fix. The 3D car follows the matched polyline, so a bad match looks like a wrong road. Live behavior of the latest APK is UNKNOWN. | Medium | SOURCE: `OsrmClient`, `GpsTrustGate`, `RerouteSession`, `RouteMotion`. JVM TEST: `OsrmClientTest`, `RerouteSessionTest`, `RouteProgressTest`. ANDROID HARDWARE: UNKNOWN for this APK. | Protect this path. Do not change the router or the match while the world is being rebuilt. Show the driver when the GPS is lost; that string path already exists. |
| Voice guidance | Spoken maneuvers, including reroute and arrival. | `VoiceGuide` plays clips from `hang/hozzarendeles.json` through `MediaPlayer`, usage `ASSISTANCE_NAVIGATION_GUIDANCE`. Start, turn, wrong direction, and reroute have call sites. A missing clip returns without speech. | The player and the event map exist. Which clips are actually in the APK, and whether the Redmi plays them on the latest build, is UNKNOWN from this audit. | Medium | SOURCE: `VoiceGuide`, `RacePrototypeActivity` speak calls. JVM TEST: maneuver event tests. ANDROID HARDWARE: UNKNOWN. | Keep the clip map. A device check is a short drive that must speak the first maneuver, a reroute, and arrival. |
| APK packaging and installation | One signed universal APK that a phone and a car unit can install over the existing app. | Release variant: `hu.toonnav.map`, versionCode 2, versionName 1.1, not debuggable, v1+v2+v3, same debug certificate, four ABIs, `extractNativeLibs` on. The file the phone rejected was the debug variant, versionCode 1, debuggable, v1+v2 only. | The rejected file is a valid debug APK. The replacement has not been installed on Android. The address database in the published file is not in git, so a clean checkout does not rebuild the same APK. | Critical | ANDROID HARDWARE: section 6. | Install the release file named in section 6. Do not uninstall first. Commit or drop the address database so the next build is reproducible. |

---

## 3. Detailed technical findings

### What the process starts

`AndroidManifest.xml` launches `MainActivity`. That screen is MapLibre: search, a route line, and a 2D cockpit. Starting guidance calls `openRaceNavigation`, which stores the trip and starts `RacePrototypeActivity` with the saved car id. The map stops listening while the 3D screen owns the trip (`NavigationOwner`).

`PlainDriveActivity` is exported and draws `PlainWorld` with no car and no route. It is not the launcher. `GarageActivity` can open the 3D screen with a live trip or with `RaceTrack.samplePreview()`, a synthetic bend around longitude 19, latitude 47.

### What draws

`RacePrototypeActivity` builds the engine with `Engine.Backend.OPENGL` and `UbershaderProvider`.

When `LegacyBackdrop.enabled` is false, which it is:

- The world material is `assets/materials/plain_solid.filamat`, compiled from `android/materials/plain_solid.mat`. The fragment writes `materialParams.baseColor` and the shading model is unlit.
- `PlainGpu` uploads positions only. `castShadows` and `receiveShadows` are false. Fog is on for the ground and off for the sky rings.
- The car is `assets/models/formula-car.glb`. Its shadow flags are cleared.
- The sky is a solid Filament skybox in the zenith blue, plus five cylinders.
- Exposure is aperture 1, shutter 1 s, ISO 100, so the unlit 0–1 colors stay visible. This was chosen because a sunny-16 exposure with a 100,000 lux sun made the same colors disappear.

When the flag is true, the activity loads `noon-sky.hdr`, encodes a road glTF through `RaceGlb`, and uses `RoadGpu`. That branch also enables shadows unless `DriveDisplay.Quality.REDUCED` is set, and it uses sunny-16 exposure. `refreshScenery` returns immediately while the flag is false, so OSM trees and buildings are not requested.

The KTX2 files under `assets/models/road/` (asphalt, grass, and gravel color, normal, and ORM) and `noon-sky.hdr` are still packed into the APK. They are unused by the active draw.

### How the road is built

`OsrmClient.readLine` keeps longitude and latitude. A third component is not used as elevation. `RaceTrack.samplesFor` walks the polyline in local meters. `RouteRoadClass` assigns a `RoadKind` by walking OSRM step distances, and `RoadProfile` turns that into half-width, shoulder, center marking, edge lines, and kerbs. Lane count is not in the route, so no class draws extra lane dividers. A motorway is one carriageway with edge lines and no opposing center line.

`PlainWorld.meshes` builds triangle strips:

- Shoulder, lifted 0.02 m.
- Asphalt, lifted 0.06 m, alternating the darker grey when `(along / 18) % 5 == 0`.
- Edge lines, 0.30 m wide, lifted 0.09 m, when the profile asks for them.
- A center dash, 0.26 m wide, on even 8 m blocks, only for `CenterMark.DASH`.
- Kerbs, about 0.62 m wide, alternating red and white every 1.6 m, only on a turn steeper than 0.02.

`refine` subdivides to about 1.7 m, or 0.65 m on a tight corner. Horizontal position is Catmull-Rom. Elevation is a linear blend, so the height cannot ring the way a Catmull-Rom height did.

### How the terrain is built

`fillGrid` lays two axis-aligned grids: 3.2 m cells out to 42 m, then 18 m cells out to 200 m. If either axis would exceed 320 cells, the step grows by 1.5 until it fits. A cell belongs to the chunk whose nearest route sample lies in that chunk.

`heightAt`:

- At or inside the carriageway edge: road elevation minus 0.08 m.
- Across the shoulder: a short blend down to about 0.10 m below the road.
- Beyond the shoulder: a 16 m smoothstep from the road lip to the DEM sample, or to `roll()` when the DEM function is null.

`roll()` is `sin` and `cos` of the local coordinates, scaled by a fade over 24 m, with amplitudes 1.05 m and 1.25 m. It is zero at the shoulder. It is not a survey.

Color past 72 m is a haze band. Closer than that, and outside the shoulder, the color is earth, light grass, deep grass, or the base green, from the sign of another sine. That pattern does not follow land cover, slope, or the DEM.

Every terrain and road vertex normal is `(0, 1, 0)`. Combined with the unlit material, a hillside is the same brightness as flat ground. The shape can be seen only as a silhouette against the sky or as a change in the road marking.

### How elevation reaches the car

`requestElevation` runs only for a live route. It calls `TerrainTiles.load`. Zoom is 13. The loader fetches the route tiles and up to 18 neighboring tiles from `https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png`. Any missing required tile returns null, and the thread exits. The UI is not told.

On success, heights are relative to the first point, smoothed, stored on the samples, and the corridor is rebuilt with the field. Until that returns, the first upload is `PlainWorld.chunks(samples, needed)` with a null field, so the opening frames are flat plus `roll()`.

`gradeRadians` is `atan2` of the elevation change from 5 m behind the car to 7 m ahead. `draw` damps that into the car transform. Positive pitch raises the nose (local +Z). The camera look point uses the road elevation 46 m ahead, also damped. Fog height and the sky-ring translation follow the car's surface so a climb does not leave the camera above the sky cylinder.

The car does not roll into a cross-slope. The camera eye is not rotated by pitch; `RaceCamera.world` adds the surface height and then applies yaw only.

### What the car and the garage do

The glTF has 9 meshes, 51 primitives, 4 images, and 28 materials. Extensions in the file are `KHR_materials_clearcoat` and `KHR_materials_emissive_strength`. Whether the Filament 1.75 ubershader honors those extensions on the phone is UNKNOWN. The painter only writes `baseColorFactor` on paint slots. A thrown tint falls back to painting shared instances.

`RaceGarageStage` is constructed from `GarageActivity` and loads the same glTF. The supplied `garage-piros.png` shows the 2D vector marker and the label "3D PROTOTÍPUS". A `SurfaceView` often does not appear in this kind of capture, so the screenshot does not prove the 3D preview is absent. It also does not prove the preview works. Device result: UNKNOWN.

There is no wheel-spin code. The four wheel nodes are painted and transformed with the body.

### What navigation does, and what it does not

Implemented and used by the drive activity:

- Location listener, follow pose, and a trust gate.
- OSRM request, parse, and step-to-road-class assignment.
- Reroute with an in-flight guard and a cooldown.
- Voice clips for departure, turns, wrong direction, and reroute.
- HUD strings for maneuver, remaining distance, and ETA from the plan duration.
- Arrival at 30 m (`NavigationPolicy.arrivalMeters`), then a handoff back to the map.
- STOP with a confirmation that halts speech and releases the trip.
- Car id and livery id restored from `GarageStore`.
- Screen stays on (`FLAG_KEEP_SCREEN_ON`).

`HungaryAddresses` can answer a local SQLite lookup and otherwise falls through to Photon (`https://photon.komoot.io/api/`). The database file in the working tree is 107,872,256 bytes. It is not in git. It is inside the local release APK that was uploaded, because Gradle packed the working-tree assets. A clean checkout of the same commit does not contain that file, so address search on a CI-built APK would be Photon only, after the size check fails and the lookup reports unavailable.

Public OSRM and public Photon are single points of failure. The code maps HTTP 429, network errors, and a missing route to status results. That is source behavior, not a live measurement.

### Placeholders and hardcoded values that still matter

- `LegacyBackdrop.enabled = false` is the switch for the entire old world.
- `PlainWorld` colors, fog, and the 3.2 / 18 / 42 / 200 m grid.
- `RaceCamera` eye, look, and 46 m look-ahead.
- `roll()` amplitudes.
- Sample route around (19, 47) for the garage preview.
- `car.glb`, the KTX2 set, and `noon-sky.hdr`, present and unused by the drive.
- Dynamic resolution to 0.45 after four frames over 42 ms. On the plain path this does not add shading; it lowers resolution. AO is already off.

### Screenshots and video that were actually available

| Artifact | What it is | What it is not |
|---|---|---|
| `road-flat.png`, `road-uphill.png`, `road-downhill.png`, `road-curve.png`, `road-beside.png`, `plain-world-wide.png`, `plain-world-tall.png` | JVM software raster of `PlainWorld` colors. Grey road, white markings, brown shoulder, green patches, blue sky. The uphill frame is a mild grade. The curve frame shows red and white kerbs. The beside frame shows a low side slope. | Filament, lighting, the car, or a phone. |
| `road-straight-filament.png`, `road-nose-filament.png`, `road-bend-filament.png` | Desktop Filament frames of an earlier setup: halo and front tires large in the frame, mottled asphalt, flat green, flat grey sky. | The current plain-world phone image. The current sky code is blue bands, not this grey sky. |
| `phone-path-before.png` | A failure frame: black ground, grey sky, tires drawn as flat white shapes. | The plain-world fix. |
| `liveries.png` | Eight color recipes as swatches. | A rendered car. |
| `garage-piros.png` | Garage UI with a 2D car icon. | Proof of the Filament garage stage. |
| `gyomro_*.mp4` and the Gyömrő address PNGs | Address search on the map flow. | A 3D drive. |

No driving video of the current world was in the artifact set. The Redmi observation that the plain-world build showed grey asphalt, green ground, a shoulder, a sky, a horizon, the nose, and the wheels stands as a user observation, not as a file this audit could reopen.

---

## 4. Visual quality scores

Scores are for the product a person sees, not for the amount of code behind it. 10 would be a current premium racing game. 0 would be a blank or broken frame.

### Overall visual quality: 3 / 10

The phone can be shown a road, a ground plane, a sky, and a Formula car. Those pieces do not yet form an outdoor place. The software rasters and the earlier Filament frames agree on this: large areas of one color, a hard horizon, and no light. The score is above 1 because the corridor reads as a road and the car reads as a car, which the black-frame failure did not.

### Terrain realism: 2 / 10

The ground is a grid of flat-colored quads. With a successful download it follows a coarse survey. Without one it is a two-meter wave. There are no valleys you could name, no ridges, no trees, and no change of brightness on a slope. `road-beside.png` is the most shaped of the rasters, and it still looks like a green wall beside a runway. The decorative hills that used to break the silhouette were removed because they intersected the car; the replacement is safer and visually flatter.

### Road realism: 3 / 10

Width, a shoulder, edge lines, a dashed center, and kerbs on a bend are real geometry, and the curve subdivision is enough that the ribbon is not a handful of long triangles. The surface is still one grey, plus a darker stripe every fifth block. Markings are extra quads floating a few centimeters up, not paint. There is no grain, no patching, no wet line, and no lane logic beyond the road class. `road-flat.png` is an honest picture of this: a clean diagram of a road.

### Car model quality: 4 / 10

The file is a modeled Formula car with separate wings, a steering wheel, and four wheels, at about 47 thousand triangles. That is enough mesh for a navigation foreground. It is not enough, by itself, to look premium: the wheels do not turn, the garage capture does not show the model, and the one failure frame draws the tires as white shells. The older Filament frames show a readable halo, nose, and tires, with plastic shading and no environment reflection. Four is the mesh plus that partial presentation, not a claim that the Redmi currently looks like those frames.

### Lighting and atmosphere: 2 / 10

The active world cannot respond to the sun. Fog and five sky bands stop the horizon from being a single hard cut, which is why this is not 0. There is no contact shadow under the car, no shade on the shoulder, and no time-of-day light beyond a darker set of the same bands. The HDR sky and the bright sun exist only on the disabled path, and that path is the one that failed.

### Camera experience: 4 / 10

The rig is a recognizable chase view: low, behind the cockpit, nose kept in frame by a JVM test, look-ahead long enough to read the road, field of view tied to the screen. Bearing and pitch are smoothed, so a GPS heading twitch should not snap the world. The eye does not pitch with the car, there is no sense of speed in the lens, and there is no device recording of the motion. The older Filament frames put a large amount of halo and tire in the lower third and a lot of empty sky above, which is a prototype camera, not a composed one.

### User interface quality: 5 / 10

The map, the search, the garage list, and the drive HUD are coherent enough to use: Hungarian labels, a saved livery, a stop control, speed, maneuver, distance, and ETA. Layout code accounts for portrait, a wide screen, and the system bars, and tests lock that math. The garage screenshot is a tidy dark UI with a diagram of a car, not a showroom. The jump from MapLibre to the flat 3D world is abrupt. Five is "usable and considered," not "polished racing product."

### Overall navigation reliability: 6 / 10

The chain from a destination to a spoken maneuver is implemented and split into testable pieces: search, OSRM, progress, reroute, voice cues, arrival, and stop. That is more than a stub, and it is the part of the product that must not be broken while the picture is rebuilt. It is not an 8, because this audit did not drive it, the latest APK was not installed, the elevation fetch can fail silently, and the address database in the uploaded APK is not in the git tree. Live GPS and the public router remain outside what a unit test can prove.

### Why it looks unfinished, in one pass

The environment looks artificial because every surface is a single unlit color on a coarse mesh, and the color of the ground is a sine rather than a place. The terrain does not feel like land because the far world ends in haze at 200 m, the grid is flat-shaded, and a missing DEM is a tiny wave instead of a hillside. The car does not look premium because its materials are not being shown in a lit, reflected environment, its wheels are frozen, and the pictures we have are either a 2D icon, a software raster with no car, an older grey-sky Filament grab, or a broken black frame. The camera contributes by leaving a large empty sky and by sitting at a fixed offset that the grade does not rotate. The changes with the most visible return, in order, are: a shared DEM that is actually present on a real route, unlit asphalt and ground detail that a phone has been shown to draw, then car shading and wheel motion. New shaders and the old HDR sky are the changes most likely to erase the picture again.

---

## 5. Technical risk and regression audit

### Black world, missing scenery, pink and white

The textured drive sampled KTX2 color, ORM, and normal maps on the road, and it loaded `noon-sky.hdr` for image lighting. On the phone that combination produced a black view and, with a bad sample, magenta or white materials. The source comment on `LegacyBackdrop` records that path as the one the phone could not show. The repair that the phone did show was the unlit corridor: no texture, no HDR, no ORM, no normal map, exposure brought back from sunny-16, shadows off.

`phone-path-before.png` matches that failure class: the ground is black, the sky is a flat grey, and the car's tires have lost their material and read as white. It is a desktop or captured failure frame, not a new Redmi photo, and it should be treated as a picture of the bug rather than of the current build.

### What the recent elevation work changed

The 16 m hills that stood beside each segment were removed. On a bend, a later segment entered the volume of an earlier hill, so the car drove through the mesh. Terrain, road, and car now share the route elevation, with the DEM blended in only outside the shoulder. That is a structural improvement. It also removed the only strong vertical shapes in the view. The world is more correct and less dramatic. A JVM test that used to require the fake haze hill to rise more than 2.5 m was rewritten. That test no longer measures a landscape; it measures the new constraint.

### OpenGL ES and Filament

The engine is OpenGL, not Vulkan. The manifest asks for ES 3.0 and does not require it, so an ES 2 device can install and then hit the in-app failure string. Materials on the active path are feature level 1 and unlit, which is the compatible choice. The car still goes through the glTF ubershader, including clearcoat and emissive extensions in the file. A missing or rejected material parameter has asserted on debug builds before. `LiveryPainter` and `RoadGpu` both catch a rejected `setParameter` and continue. That catch is a scar from those asserts. Adding a new parameter to a material that the ubershader build does not have will hit the same class of failure.

`RoadGpu` is the dangerous side of this. It duplicates the asphalt material and writes `baseColorFactor`, `metallicFactor`, and `roughnessFactor` inside a try. A rejected write leaves the glTF texture in place. That is the path that must not be started on a phone until a device has shown the texture.

### Is the foundation stable?

The navigation modules and the unlit corridor are a stable foundation. They are separated enough that road geometry, profiles, guidance, and voice can be tested without a GPU.

The rendering architecture is not something to extend by turning features back on. It is two worlds in one activity, selected by a boolean. The unused world, its HDR file, and its KTX2 files are still in the release APK. The next visual step should add geometry and unlit color to `PlainWorld` and `PlainGpu`. It should not set `LegacyBackdrop.enabled` to true, and it should not load `noon-sky.hdr` on the drive.

A JVM raster, a desktop Filament window, and `BUILD SUCCESSFUL` do not prove the phone. The black frame was invisible to the tests that only checked mesh positions and colors.

### APK regression

Publishing the debug APK as the install file is a release-process defect. The archive itself was valid. Details are in the next section. Graphics work that lands in another debug APK repeats the install failure even if the picture is better.

---

## 6. APK installation investigation

The phone rejected `toonnav-plain-ec4eb4e7900d.apk` and reported that the package appears to be invalid. The Hungarian wording corresponds to that package-invalid message. The reported size was 114.97 MB.

Checked on the GitHub asset and on the local debug APK, which are the same bytes:

| Check | Rejected file |
|---|---|
| Size | 114,967,856 bytes |
| SHA-256 | `3c44b12e7aab9047badf1f0c767d690f85862595e5d6dce4ffcc448aee8b9e61` |
| Local file versus downloaded release | Byte-identical |
| `file` | Android package (APK), not an HTML error page |
| `unzip -t` | No errors |
| ZIP structure | A single APK. Not an Android App Bundle. Not a split. `output` metadata for this line of builds is one `SINGLE` element with an empty filter list. |
| `zipalign -c -v 4` | Verification successful. `resources.arsc` stored and aligned. |
| `aapt dump badging` | Package `hu.toonnav.map`, versionCode 1, versionName 1.0, minSdk 21, targetSdk 35, compileSdk 35, label ToonNav. The binary manifest parses. |
| Debuggable | `android:debuggable` present |
| `testOnly` | Absent |
| `apksigner verify --verbose --print-certs` | Verifies. v1 true, v2 true, v3 false, v3.1 false, v4 false. |
| Certificate | CN=Android Debug, RSA 2048. SHA-256 `e71ce1188cde57995c924a0b392e630c2d8502fd0016b0aa3387c4f03d116052`. Valid from 13 November 2026 to 5 November 2056. |
| Same certificate as the plain-world build the phone showed, and as the livery build | Yes. Do not uninstall. An update with this certificate keeps the app data. |
| Native libraries | `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`. Each has `libfilament-jni.so`, `libfilament-utils-jni.so`, `libgltfio-jni.so`, and `libmaplibre.so`. Libraries are stored compressed, with `extractNativeLibs` true, so 16 KB page alignment is not the install rule that applies. |
| `jarsigner` | Reports the jar verified, and also reports PKIX path building failed. That PKIX note is the self-signed debug certificate. The same note is produced for the older APK the phone did show. |

`apksigner` also warns that `META-INF/com/android/build/gradle/app-metadata.properties` and many `META-INF/androidx.*.version` entries are outside the v1 signature. Those androidx entries are also in the APK the phone already ran. They do not, by themselves, explain why only the later debug file was refused.

So the failure is not a short download, a corrupt ZIP, a bad manifest, a wrong package id, a missing ABI, or a changed certificate. The file is a universal debug APK. Some phone installers, including recent Xiaomi and HyperOS file-manager installs, refuse a newly sideloaded package that is marked debuggable, and the message they show is the invalid-package message. That remains a hypothesis. It fits the checks, and it was not reproduced here: no phone was attached, and the emulator's adb connection stayed offline. This audit does not claim the Redmi has accepted a replacement.

### The file that should be installed instead

Built as the release variant, signed with the same debug keystore, and uploaded:

https://github.com/reiko1866-ui/Navi-rendszer/releases/download/toonnav-universal-88ca57cb64c8/toonnav-universal-88ca57cb64c8.apk

| Check | This release file |
|---|---|
| HTTP | The public URL returns 302, then the file. |
| Size | 106,385,480 bytes |
| SHA-256 | `9a92f81637a7339d123fd8aaa9168fc40df84b964bc335d88217ca090cf1d243` |
| Downloaded asset versus local `app-release.apk` | Byte-identical |
| `unzip -t` | No errors |
| `zipalign -c -v 4` | Verification successful |
| `apksigner` | Verifies. v1 true, v2 true, v3 true. One signer. Certificate SHA-256 unchanged. |
| `aapt dump badging` | `hu.toonnav.map`, versionCode 2, versionName 1.1, minSdk 21, targetSdk 35 |
| Debuggable | Absent from the manifest dump |
| ABIs | The same four, with the same four libraries each |
| Kind | One universal APK. Baseline profile `.dm` files are beside it in the Gradle output and are not the install file. |
| Android install | Did not succeed. Emulator offline. No device. Gradle `BUILD SUCCESSFUL` is not an install. |

versionCode 2 is what lets this file update the installed versionCode 1 in place.

### Where the problem sits

| Stage | Finding |
|---|---|
| Build | The debug variant is a valid installable package for a debug tool, and a poor file to hand to a phone installer. The release variant is the one built to be installed. |
| Signing | The signature verifies. The certificate did not change. v3 was missing on the rejected file and is present on the release file. |
| GitHub upload | The rejected asset matches the local debug bytes. The new asset matches the local release bytes. The upload did not truncate either file. |
| Downloaded file | Complete. The size the phone reported for the old file matches 114,967,856 bytes interpreted as megabytes. |

A separate reproducibility defect: the uploaded release APK contains `assets/hungary-addresses.sqlite` (107,872,256 bytes uncompressed in the archive listing) and the matching `.size` file. Those two paths are not in git. The APK was packaged from a working tree that had them. A clean build of commit `88ca57c` will not contain them, and `HungaryAddresses` will report the index unavailable and leave search on Photon.

---

## 7. Prioritized development roadmap

Effort is described as scope and risk. Calendar dates would be false precision. Nothing in phase 3 or later should start until a phone has installed the current release APK and shown the plain world again.

### Phase 1 — Critical functional and structural problems

Tasks:

- Install `toonnav-universal-88ca57cb64c8.apk` on the Redmi and record the package manager result. Do not uninstall first.
- On that same install, confirm the plain world, the nav panel, one spoken maneuver, STOP, and that the saved livery is still the one that was stored.
- Decide the address database: commit a reproducible asset, or stop packing an untracked file and accept Photon as the only search. Do not leave the next APK depending on a file that git does not have.
- Make the release build the only file that is uploaded. Name it as the universal install file.
- Keep `LegacyBackdrop.enabled` false. Add a release-time guard so the HDR and KTX2 branch cannot be started from a release build.

Files: `android/app/build.gradle.kts`, `AndroidManifest` only if a component must stop being exported, `LegacyBackdrop`, the release script that uploads the APK. No renderer rewrite.

Depends on: a phone. The emulator in this environment did not reach adb.

Risk: uploading another debug APK repeats the invalid-package failure. Uninstalling would throw away local data; the certificate match makes that unnecessary.

Success: the Redmi installs versionCode 2 over the existing app, shows the grey corridor and the car, and the previous livery is still selected.

Do not change: package id, the debug certificate, guidance, voice clip ids, `index.html`, `elozo-terkep.html`, the Formula mesh.

### Phase 2 — Correct road elevation and realistic terrain

Tasks:

- Log, on the HUD or in a status line, whether the DEM arrived, failed, or is still loading. A silent flat road is the current failure mode.
- Keep the road, the shoulder, and the car on the route elevation. Keep the 16 m blend outside the shoulder. Do not restore lateral domes.
- Raise zoom only near the car, and keep zoom 13 as the far field, so a short hill is not a single pixel.
- Give the grid real normals from its corners so a later lit material has data. The unlit material will not show them yet; the test can still check that a slope's normal is not straight up.
- Cap the cell count from a measured frame time, not from a hope. The 320-cell coarsen already exists; do not remove it until a phone frame time says so.

Files: `TerrainTiles.kt`, `PlainWorld.kt`, `RaceTrack.kt`, `RacePrototypeActivity.kt` (`requestElevation`, `draw`), `PlainWorldTest.kt`.

Depends on: phase 1, and network access to the terrarium tiles from the phone.

Risk: a finer grid hitches on the UI thread when tiles return. A DEM spike can throw the car; the 60 m spike filter exists and should stay.

Success: on a known slope, the road, the car, and the ground beside the shoulder share one profile, the car does not enter the ground, and a failed download is visible. Proof is a phone capture plus the existing JVM corridor tests. A software raster is only a geometry check.

Do not change: the unlit material, the HDR file, OSRM parsing, the livery ids.

### Phase 3 — Premium environment, road, and lighting

This phase cannot reach a commercial racing game on this architecture. It can reach a convincing unlit diorama if each new image is proven on the phone.

Tasks:

- Replace the sine grass with a small set of unlit colors tied to distance and slope, still with no texture, and look at it on the phone.
- If that holds, add one unlit asphalt base color. No normal map, no ORM, no KTX2 until a phone has shown the base color.
- Add a simple sun disc and more sky bands as geometry.
- Trees, if they come back, are separate unlit meshes with a budget, not `Scenery` behind the legacy flag.

Files: `PlainWorld.kt`, `PlainGpu.kt`, `plain_solid.mat` only if a second unlit parameter is required, new image assets only after the device proof.

Depends on: phase 2, and a Mali or Adreno capture after each asset.

Risk: this is the step that previously turned the screen black or magenta. One asset per device test. A missing shader parameter can assert.

Success: a phone screenshot where the road has grain, the shoulder is distinct, the horizon is not a single cut, and the car is still visible. If the screenshot is black or a flat error color, revert that asset and stop.

Do not change: `LegacyBackdrop` to true, `noon-sky.hdr` on the drive path, shadow maps on the unlit world, guidance.

### Phase 4 — Premium Formula car and liveries

Tasks:

- Photograph the current glTF on the phone in the garage and on the road before changing it.
- Rotate the wheel nodes from speed. Steer the front wheels from the route curvature, with a small limit.
- Keep the eight liveries and the store. Change one paint parameter at a time.
- Leave carbon, tire, and glass alone until the paint change has a phone photo.

Files: `RaceLivery.kt`, `RacePrototypeActivity.kt` car transform, `RaceGarageStage.kt`, `GarageFleet.kt` only for a new livery the product actually wants.

Depends on: phase 1. It can proceed beside phase 2 if the car change does not touch the world material.

Risk: a new material parameter on the ubershader. The fallback in `LiveryPainter` hides a failed tint and can leave the car half painted.

Success: phone photos of at least two liveries, wheels turning while the GPS speed is non-zero, nose and front wing still inside the frame.

Do not change: the mesh file's geometry, the package id, the world textures.

### Phase 5 — Camera, interface, and performance

Tasks:

- Pitch the eye with the same grade used for the car, and re-check the nose NDC on the phone aspects, not only at 16:9 in the JVM.
- Capture a portrait phone and a wide car display. Confirm the road edges and the six buttons stay on screen and clear of the system bars.
- Record frame time on both screens. Only then decide whether the near grid can be finer.

Files: `RaceCamera.kt`, `DriveDisplay.kt`, `RacePrototypeActivity.kt`.

Depends on: a phone and, for the wide case, the car display. Phase 2's grade should be in place so the camera is pitched against a real slope.

Risk: a higher camera or a wider look hides the nose. The 0.64 look-height failure is the regression to avoid.

Success: nose, front wing, and both front tires visible on both aspect ratios, buttons tappable, and a recorded frame time under the 42 ms tripwire on the target device.

Do not change: the navigation strings' meaning, the arrival distance, the stop confirmation.

### Phase 6 — Real Android device validation and release

Tasks:

- Clean release build from a checkout that contains only tracked files.
- `unzip -t`, `zipalign -c -v 4`, `apksigner verify --verbose --print-certs`, `aapt dump badging` on the file that was uploaded, not only on the local path.
- Compare SHA-256 of the local file and the downloaded asset.
- Install on the phone over the existing app. Exercise search, GPS follow, one reroute, voice, arrival, STOP, livery persistence, day and night, and both orientations.
- Then install the same file on the car display, or record that the display was not available.

Files: the release APK and the upload step. Code changes only for defects this pass finds.

Depends on: phases 1 through 5 for a visual release. Phase 1 alone is enough for an install-only release, which is the one that is overdue.

Risk: calling the release done because Gradle succeeded.

Success: the phone's installer accepts the file, the SHA-256 matches, and the exercise above was done on the device. A desktop render is not a substitute.

Do not change: the certificate, the package id, the set of ABIs.

What this roadmap will not produce: a photoreal mountain stage, dynamic weather, or a scanned car. The phone-safe renderer is unlit Filament, the elevation source is a public raster of roughly ten-meter samples, and the car is one CC0 glTF. That can become a clear, consistent driving toy with real grades. It will not become a current AAA driving game without a different content pipeline and a device-proven material stack.

---

## 8. Recommended architecture

Keep Filament, OpenGL, and the ubershader for the car. Replacing the engine would discard the only phone-proven draw path in order to restart the same material problem.

Split the two worlds in the build, not only with a boolean. The release variant should compile `startPlain` and should not package a code path that opens `noon-sky.hdr` or the road KTX2 set. Those assets can leave the APK when the code that reads them is gone. That removes tens of megabytes and removes the chance that a one-line flag returns the black frame.

Treat `PlainWorld` as the world model and `PlainGpu` as the only world drawer. `RoadGpu`, `RaceLighting`, and `Scenery` stay in the tree only as a quarantine, or they go. New terrain work belongs in `PlainWorld.heightAt` and the grid, where the JVM tests can see it.

Keep materials unlit and parameter-light until a specific phone has shown a new texture. The order is geometry, then one base color, then lighting. Lighting before a stable color is how the last regression happened.

Keep navigation, matching, and voice in the modules that do not import Filament. `RacePrototypeActivity` is already too large: it owns the engine, the GPS listener, the HUD, the elevation fetch, and both renderers. New world work should not add more to that file. The activity can call a world object that already exists.

One APK, four ABIs, versionCode increasing, the same certificate. No App Bundle and no ABI split, because the car display and the phone need the same file.

---

## 9. Features that already work and must be protected

These are present on the active path. "Works" means the source uses them and tests lock the decision. It does not mean this audit watched them on a phone.

- Package `hu.toonnav.map`, minSdk 21, targetSdk 35.
- The debug certificate SHA-256 `e71ce1188cde57995c924a0b392e630c2d8502fd0016b0aa3387c4f03d116052`. Keep it so an update does not look like a different app.
- MapLibre planning screen, Photon search, and the handoff into the 3D drive when guidance starts.
- OSRM route with steps, road class, and a polyline the car follows.
- GPS trust gate, reroute session, arrival at 30 m, STOP, and the return of the trip to the map.
- Voice clip player and the maneuver event rules.
- Remaining distance and ETA from the plan.
- Eight liveries, legacy id mapping, and `GarageStore`.
- The Formula glTF, not the 304-triangle stand-in.
- The unlit corridor: grey road, shoulder, markings, green ground, blue sky bands, fog in the horizon color, shadows off, HDR not loaded.
- Camera constants that keep the nose inside the JVM frame, and the aspect-based field of view.
- One universal APK with `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`.
- Screen-on during the drive, and the inset-aware button layout.

`index.html` and `elozo-terkep.html` are outside this Android path and were not part of the phone failure. Leave them alone.

---

## 10. Incomplete or unverified

| Item | State |
|---|---|
| Install of `toonnav-universal-88ca57cb64c8.apk` on the Redmi or any Android device | UNKNOWN. Archive checks passed. Install did not happen. |
| Install of the rejected debug APK | Failed on the Redmi with the invalid-package message. Archive checks passed. The debuggable-installer explanation is a hypothesis. |
| Plain world on the Redmi | USER OBSERVATION for the earlier plain-world build: grey road, green ground, shoulder, sky, horizon, nose, wheels, nav panel. Not re-confirmed for the elevation build, because that APK was not installed. |
| DEM on a live drive | SOURCE implements it. ANDROID HARDWARE UNKNOWN. A failure is invisible. |
| Car clearcoat, emissive strength, and tire materials on the phone | UNKNOWN. The glTF requests the extensions. One failure frame shows white tires. |
| Garage Filament preview | SOURCE starts `RaceGarageStage`. The supplied screenshot shows the 2D icon. Device result UNKNOWN. |
| Wheel rotation and front-wheel steer | Not implemented. |
| OSM trees and buildings | Implemented in `Scenery` and not called on the active path. |
| Asphalt, grass, and gravel KTX2, and `noon-sky.hdr` | In the APK. Not sampled on the active path. Known to be unsafe on the phone when the legacy path loads them. |
| Wide Android car display | Layout math exists. No device result. |
| Frame time on a phone | The tripwire exists. No measurement. |
| Voice clips audible on the latest build | Call sites and a player exist. Not heard in this audit. |
| Address database | In the uploaded APK, not in git. A clean build will not match. Runtime on a phone UNKNOWN. |
| JVM suite after the release-packaging edit | Not re-run for this audit. The edit is version, signing, and packaging. The last recorded result was 167 tests, 0 failures, and that result is not a phone test. |
| Software rasters and desktop Filament PNGs | They show the gaps in section 4. They are not Filament-on-Android and not a Redmi test. |
| Driving video of the current world | Not in the artifact set. |

---

## Conclusion

The product is a navigator with a safe, flat, unlit 3D corridor and a real Formula mesh, plus a disabled renderer that the phone could not draw. The premium landscape, the asphalt, the vegetation, the light, and the cinematic camera are not in the running application. The APK the phone refused was a valid debug package with the same certificate as the installed app. The replacement release package checks out as a universal APK and still has no Android install behind it.

The next useful work is to install that release file, prove the corridor and the trip on the phone, and only then raise the ground with the DEM that is already written. New textures and the old HDR sky wait until a device has earned them.
