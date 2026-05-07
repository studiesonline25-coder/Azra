package com.azra

import android.annotation.SuppressLint
import android.companion.virtual.VirtualDeviceManager
import android.companion.virtual.VirtualDeviceParams
import android.companion.virtual.camera.VirtualCamera
import android.companion.virtual.camera.VirtualCameraConfig
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log

class VirtualCameraManager(private val context: Context) {

    private val TAG = "VirtualCameraManager"
    private var virtualDevice: VirtualDeviceManager.VirtualDevice? = null
    private var virtualCamera: VirtualCamera? = null
    private var frameInjector: FrameInjector? = null

    @SuppressLint("WrongConstant")
    fun start() {
        if (Build.VERSION.SDK_INT < 34) {
            Log.e(TAG, "VirtualCamera requires API 34+")
            return
        }

        try {
            val vdm = context.getSystemService(Context.VIRTUAL_DEVICE_SERVICE) as? VirtualDeviceManager
            if (vdm == null) {
                Log.e(TAG, "VirtualDeviceManager not found")
                return
            }

            // In some environments, createVirtualDevice might require an association ID. 
            // We use a dummy or skip if there's an overloaded method without it.
            // Using VirtualDeviceParams.Builder()
            val params = VirtualDeviceParams.Builder()
                .setName("Azra_Virtual_Device")
                .build()
                
            // Note: The API signature for createVirtualDevice varies between previews and final.
            // Assuming the system allows it via CREATE_VIRTUAL_DEVICE permission.
            // We might need reflection if the exact signature is hidden or requires companion device.
            // Let's try the standard approach first.
            virtualDevice = vdm.createVirtualDevice(0, params)

            if (virtualDevice == null) {
                Log.e(TAG, "Failed to create VirtualDevice")
                return
            }

            setupVirtualCamera()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting VirtualCameraManager", e)
        }
    }

    @SuppressLint("NewApi")
    private fun setupVirtualCamera() {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var frontCameraId: String? = null

        // Find real front camera
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                frontCameraId = id
                break
            }
        }

        val configBuilder = VirtualCameraConfig.Builder(
            "AzraFrontCamera"
        ).setLensFacing(VirtualCameraConfig.LENS_FACING_FRONT)
        
        // In a full implementation, we would clone exact characteristics.
        // For the first step, we just provide the basic required config.
        // E.g., setting dimensions and format (usually ImageFormat.PRIVATE or YUV_420_888).
        // Let's use 1920x1080 for testing green frames.
        configBuilder.addStreamConfig(1920, 1080, android.graphics.ImageFormat.YUV_420_888, 30)
        
        val config = configBuilder.build()

        try {
            virtualCamera = virtualDevice?.createVirtualCamera(config)
            
            if (virtualCamera != null) {
                Log.i(TAG, "VirtualCamera created successfully!")
                
                // We retrieve the input surface to start injecting frames
                val surface = virtualCamera?.inputSurface
                if (surface != null) {
                    frameInjector = FrameInjector(surface, 1920, 1080)
                    frameInjector?.start()
                } else {
                    Log.e(TAG, "VirtualCamera InputSurface is null")
                }
            } else {
                Log.e(TAG, "VirtualCamera is null after creation")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualCamera", e)
        }
    }

    fun stop() {
        frameInjector?.stop()
        frameInjector = null
        
        try {
            // Depending on the API, virtualCamera might have a close() method
            virtualCamera?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing virtual camera", e)
        }
        
        try {
            virtualDevice?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing virtual device", e)
        }
    }
}
