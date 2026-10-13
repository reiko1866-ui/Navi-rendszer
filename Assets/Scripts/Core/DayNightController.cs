using System;
using UnityEngine;
using UnityEngine.Rendering;

// Attach this to an empty object named EnvironmentManager.
// Day and night follow the clock on the device. There is no button.
// 7:00 to 19:00 is day. 20:00 to 6:00 is night.
// The hour before 7:00 and the hour before 20:00 fade between them.
public class DayNightController : MonoBehaviour
{
    [Header("Scene references")]
    [SerializeField] private Light sun;
    [SerializeField] private Camera skyCamera;

    [Header("Day")]
    [SerializeField] private Color daySky = new Color(0.45f, 0.75f, 1f);
    [SerializeField] private Color dayAmbient = new Color(0.62f, 0.72f, 0.8f);
    [SerializeField] private Color daySunColor = new Color(1f, 0.97f, 0.88f);
    [SerializeField] private float dayIntensity = 1.1f;
    [SerializeField] private float dayPitch = 50f;

    [Header("Night")]
    [SerializeField] private Color nightSky = new Color(0.04f, 0.06f, 0.16f);
    [SerializeField] private Color nightAmbient = new Color(0.14f, 0.16f, 0.32f);
    [SerializeField] private Color nightSunColor = new Color(0.62f, 0.72f, 1f);
    [SerializeField] private float nightIntensity = 0.18f;
    [SerializeField] private float nightPitch = -20f;

    private float blend;
    private bool loggedMissingSun;

    private void Start()
    {
        if (sun == null)
            sun = FindDirectionalLight();

        if (skyCamera == null)
            skyCamera = Camera.main;

        if (sun == null && !loggedMissingSun)
        {
            loggedMissingSun = true;
            Debug.LogWarning("DayNightController: drag Directional Light into the Sun slot on EnvironmentManager.");
        }

        blend = NightAmount(DateTime.Now);
        Apply();
    }

    private void Update()
    {
        blend = NightAmount(DateTime.Now);
        Apply();
    }

    // 0 is day, 1 is night.
    private static float NightAmount(DateTime time)
    {
        float minutes = time.Hour * 60f + time.Minute + time.Second / 60f;
        const float sunrise = 6f * 60f;
        const float day = 7f * 60f;
        const float sunset = 19f * 60f;
        const float night = 20f * 60f;

        if (minutes >= day && minutes < sunset)
            return 0f;
        if (minutes >= night || minutes < sunrise)
            return 1f;
        if (minutes >= sunrise && minutes < day)
            return 1f - (minutes - sunrise) / 60f;
        return (minutes - sunset) / 60f;
    }

    private static Light FindDirectionalLight()
    {
        Light[] lights = FindObjectsByType<Light>(FindObjectsSortMode.None);
        for (int i = 0; i < lights.Length; i++)
        {
            if (lights[i] != null && lights[i].type == LightType.Directional)
                return lights[i];
        }

        return null;
    }

    private void Apply()
    {
        Color sky = Color.Lerp(daySky, nightSky, blend);
        Color ambient = Color.Lerp(dayAmbient, nightAmbient, blend);

        if (sun != null)
        {
            sun.color = Color.Lerp(daySunColor, nightSunColor, blend);
            sun.intensity = Mathf.Lerp(dayIntensity, nightIntensity, blend);
            float pitch = Mathf.Lerp(dayPitch, nightPitch, blend);
            sun.transform.rotation = Quaternion.Euler(pitch, -30f, 0f);
        }

        // Flat ambient makes the day and night colors visible in URP.
        RenderSettings.ambientMode = AmbientMode.Flat;
        RenderSettings.ambientLight = ambient;

        if (skyCamera != null)
        {
            skyCamera.clearFlags = CameraClearFlags.SolidColor;
            skyCamera.backgroundColor = sky;
        }
    }
}
