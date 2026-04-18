package com.selfdox.rtspserver.model

import android.hardware.camera2.CameraCharacteristics

/**
 * Represents a camera available on the device.
 * DECISION: Per D2 spec — cameras enumerated via CameraManager at runtime.
 */
data class CameraInfo(
    val id: String,
    val label: String,  // e.g. "Back (Wide)", "Front", "Back (Ultra-wide)"
    val facing: Int     // CameraCharacteristics.LENS_FACING_BACK / FRONT / EXTERNAL
) {
    val isBack: Boolean get() = facing == CameraCharacteristics.LENS_FACING_BACK
    val isFront: Boolean get() = facing == CameraCharacteristics.LENS_FACING_FRONT
    val isExternal: Boolean get() = facing == CameraCharacteristics.LENS_FACING_EXTERNAL
}
