using UnityEngine;

// Attach this to the PlayerCar cube.
// W / Up drives forward. S / Down drives backward. A / D or the arrow keys turn.
public class CarMovement : MonoBehaviour
{
    [Tooltip("How fast the car moves, in meters per second.")]
    [SerializeField] private float moveSpeed = 8f;

    [Tooltip("How fast the car turns, in degrees per second.")]
    [SerializeField] private float turnSpeed = 80f;

    private bool inputBlocked;

    private void Update()
    {
        if (inputBlocked)
            return;

        float steering;
        float throttle;
        try
        {
            // Horizontal is A/D and the left/right arrows.
            // Vertical is W/S and the up/down arrows.
            // These names come from Unity's default Input Manager.
            steering = Input.GetAxis("Horizontal");
            throttle = Input.GetAxis("Vertical");
        }
        catch (System.InvalidOperationException)
        {
            inputBlocked = true;
            Debug.LogError(
                "ToonNav3D: the keyboard is turned off. " +
                "Edit > Project Settings > Player > Other Settings > Active Input Handling > Both. " +
                "Then restart Unity.");
            return;
        }

        transform.Rotate(0f, steering * turnSpeed * Time.deltaTime, 0f);
        transform.Translate(Vector3.forward * throttle * moveSpeed * Time.deltaTime, Space.Self);
    }

    private void OnGUI()
    {
        if (inputBlocked)
        {
            GUI.Label(
                new Rect(16f, 64f, 640f, 40f),
                "Keyboard is off. Set Active Input Handling to Both, then restart Unity.");
            return;
        }

        GUI.Label(new Rect(16f, 64f, 420f, 24f), "Drive: W A S D or the arrow keys");
    }
}
