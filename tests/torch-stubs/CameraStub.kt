package android.hardware
class Camera(var modes: List<String> = listOf("off", "torch")) {
    var activeFlash = "off"
    var parameters: Parameters
        get() = Parameters(modes, activeFlash)
        set(value) { activeFlash = value.flashMode }
    class Parameters(val supportedFlashModes: List<String>, var flashMode: String) {
        companion object {
            const val FLASH_MODE_TORCH = "torch"
            const val FLASH_MODE_OFF = "off"
        }
    }
}
