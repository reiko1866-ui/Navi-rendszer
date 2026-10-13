using UnityEngine;
using UnityEngine.Rendering;

// Attach this to an empty object named EnvironmentManager.
// Press N, or the button in the Game view, to switch between day and night.
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

    [Tooltip("How fast the colors change. 1 means about one second.")]
    [SerializeField] private float blendSpeed = 1.5f;

    private bool isNight;
    private float blend;
    private bool inputBlocked;
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

        Apply();
    }

    private void Update()
    {
        if (!inputBlocked && WasNightKeyPressed())
            isNight = !isNight;

        float target = isNight ? 1f : 0f;
        blend = Mathf.MoveTowards(blend, target, blendSpeed * Time.deltaTime);
        Apply();
    }

    private void OnGUI()
    {
        string label = isNight ? "Switch to day" : "Switch to night";
        if (GUI.Button(new Rect(16f, 16f, 180f, 36f), label))
            isNight = !isNight;
    }

    private bool WasNightKeyPressed()
    {
        try
        {
            return Input.GetKeyDown(KeyCode.N);
        }
        catch (System.InvalidOperationException)
        {
            inputBlocked = true;
            return false;
        }
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
