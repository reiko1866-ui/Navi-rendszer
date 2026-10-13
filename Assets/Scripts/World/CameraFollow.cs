using UnityEngine;

// Attach this to Main Camera.
// Drag PlayerCar into Target. If you forget, the script looks for an object named PlayerCar.
public class CameraFollow : MonoBehaviour
{
    [SerializeField] private Transform target;

    [Tooltip("Camera position relative to the car. Negative Z means behind the car.")]
    [SerializeField] private Vector3 offset = new Vector3(0f, 5f, -8f);

    [Tooltip("Seconds the camera takes to catch up. Smaller is snappier.")]
    [SerializeField] private float smoothTime = 0.2f;

    [Tooltip("How high on the car the camera looks.")]
    [SerializeField] private float lookHeight = 1.2f;

    private Vector3 velocity;

    private void Start()
    {
        if (target == null)
        {
            GameObject car = GameObject.Find("PlayerCar");
            if (car != null)
                target = car.transform;
        }
    }

    private void LateUpdate()
    {
        if (target == null)
            return;

        // offset is rotated with the car, so the camera stays behind it in turns.
        Vector3 desiredPosition = target.position + target.rotation * offset;
        float smooth = Mathf.Max(0.01f, smoothTime);
        transform.position = Vector3.SmoothDamp(transform.position, desiredPosition, ref velocity, smooth);

        Vector3 lookPoint = target.position + Vector3.up * lookHeight;
        transform.LookAt(lookPoint);
    }
}
