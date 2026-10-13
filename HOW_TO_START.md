# ToonNav3D — do this next

## On your phone, open this

The moving picture runs in the phone browser. Unity Editor does not run on Android.

Open this link in Chrome:

https://htmlpreview.github.io/?https://github.com/reiko1866-ui/Navi-rendszer/blob/cursor/toonnav3d-milestone-1-b5ab/index.html

If that page stays blank, open this one instead:

https://cdn.jsdelivr.net/gh/reiko1866-ui/Navi-rendszer@cursor/toonnav3d-milestone-1-b5ab/index.html

Day and night follow the phone clock. No button is required.

- 7:00–19:00 is day (sun, 22°C).
- 20:00–6:00 is night (moon, 12°C).
- 6:00–7:00 and 19:00–20:00 fade from one to the other.

The word on the bottom pill (Éjszaka or Nappal) only previews the other look for 12 seconds, then the clock takes over again.

The picture we are aiming for is `Reference/celkep.png`. Rain and snow are not in this phone page yet.

The 3D Unity scene still needs a computer. Those steps are below. Do not install a Unity app from the Play Store.

## On a computer

1. Install **Unity Hub** from https://unity.com/download and sign in.
2. In Hub, open **Installs → Install Editor**. Pick the newest **LTS** version (Unity 6 LTS or 2022.3 LTS). You can add **Windows Build Support**. Skip Android for now.
3. Install **GitHub Desktop** from https://desktop.github.com and sign in.
4. **File → Clone repository**. This GitHub repo is named `Navi-rendszer`. Clone it to a folder you can find, for example `C:\Projects\Navi-rendszer`.
5. In GitHub Desktop, click **Current Branch** and select `cursor/toonnav3d-milestone-1-b5ab`. If you stay on `main`, the scripts will be missing.
6. In Unity Hub, click **New project → Universal 3D** (or **3D (URP)**). Name it `ToonNav3D`. Choose an **empty** folder, for example `C:\Projects\ToonNav3D`. Hub usually refuses a folder that already has files, so do not point it at the clone.
7. Wait until the editor is fully open. Then choose **File → Exit**.
8. Copy the whole `Assets\Scripts` folder from the clone into the Unity project, so this file exists:
   `C:\Projects\ToonNav3D\Assets\Scripts\World\CarMovement.cs`
9. Open `ToonNav3D` from Unity Hub again. Wait until the spinner at the bottom right finishes.
10. Choose **Edit → Project Settings → Player → Other Settings**. Set **Active Input Handling** to **Both**. If Unity asks to restart, let it restart.
11. Choose **File → Save As**. Inside `Assets`, create a folder named `Scenes` if it is not there. Save the scene as `TestEnvironment.unity`.
12. In the Project window, right-click `Assets` → **Create → Folder**. Create `Materials` and `Prefabs` if they are missing.

### Build the test scene

Use **GameObject → 3D Object → Cube** for each object. Set **Transform** in the Inspector exactly.

| Name | Position | Scale |
| --- | --- | --- |
| Road | 0, 0, 0 | 8, 0.2, 100 |
| Ground | 0, -0.2, 0 | 80, 0.2, 120 |
| PlayerCar | 0, 0.6, -35 | 1.8, 1, 3.2 |

Rename each cube in the top of the Inspector. The car's name must be exactly `PlayerCar`.

Colors:

1. In `Assets/Materials`, right-click → **Create → Material**.
2. Name them `RoadMaterial`, `GrassMaterial`, and `CarMaterial`.
3. Leave the **Shader** as it is (it should mention Universal Render Pipeline).
4. Click the color square. Make the road gray, the ground green, and the car blue.
5. Drag each material onto its object in the Hierarchy.

Camera and light (these already exist in a new URP scene):

1. Select **Main Camera**. Set Position to `0, 6, -43` and Rotation to `15, 0, 0`. Do not change its Tag. It should stay `MainCamera`.
2. Select **Directional Light**. Set Rotation to `50, -30, 0` and Intensity to `1`.

Scripts:

1. Select **PlayerCar**. Click **Add Component**. Search for `CarMovement`. Add it.
2. Select **Main Camera**. Add Component → `CameraFollow`. Drag **PlayerCar** from the Hierarchy into the **Target** slot.
3. In the Hierarchy, right-click → **Create Empty**. Name it `EnvironmentManager`.
4. Add Component → `DayNightController`. Drag **Directional Light** into **Sun**. Drag **Main Camera** into **Sky Camera**.

Press **Ctrl+S** to save.

### Press Play

1. Click the **Game** tab (not only Scene).
2. Click inside the Game view once, so the keyboard reaches the game.
3. Press the **Play** button.
4. Drive with **W A S D** or the arrow keys.
5. Click **Switch to night**, or press **N**. Click again, or press **N**, to return to day.

You should see a blue cube car on a gray road, a camera that stays behind the car, and the sky and light changing.

Open **Window → General → Console**. If a line is red, copy that text and send it to me. Do not add GPS or weather yet.

### After it works, save it to GitHub

Copy these folders from `C:\Projects\ToonNav3D` into the clone `C:\Projects\Navi-rendszer`, merging with what is already there:

- `Assets`
- `Packages`
- `ProjectSettings`

Do **not** copy `Library`, `Logs`, `Temp`, or `UserSettings`.

In GitHub Desktop, commit the result and push the same branch. The scene file `Assets/Scenes/TestEnvironment.unity` should be in the commit.
