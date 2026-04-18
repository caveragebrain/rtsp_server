package com.selfdox.rtspserver.util

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.selfdox.rtspserver.model.CameraInfo

object CameraEnumerator {

    fun getAvailableCameras(context: Context): List<CameraInfo> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val result = mutableListOf<CameraInfo>()
        val seenFacings = mutableSetOf<Int>()

        for (id in manager.cameraIdList) {
            val chars = try {
                manager.getCameraCharacteristics(id)
            } catch (_: Exception) { continue }

            // Skip sensors that can't produce standard YUV/JPEG output
            val caps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) ?: continue
            if (!caps.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE)) continue

            val facing = chars.get(CameraCharacteristics.LENS_FACING) ?: continue

            // DECISION: Only keep one camera per facing direction (back/front/external).
            // This eliminates duplicates like ultra-wide that show the same feed.
            if (facing in seenFacings) continue
            seenFacings.add(facing)

            val label = when (facing) {
                CameraCharacteristics.LENS_FACING_BACK -> "Back"
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                else -> "Camera $id"
            }

            result.add(CameraInfo(id = id, label = label, facing = facing))
        }

        return result
    }
}
