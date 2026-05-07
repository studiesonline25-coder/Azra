package com.azra

import android.annotation.SuppressLint
import android.companion.virtual.VirtualDeviceManager
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log
import android.view.Surface
import java.lang.reflect.Method

class VirtualCameraManager(private val context: Context) {

    private val TAG = "VirtualCameraManager"
    private var virtualDevice: Any? = null
    private var virtualCamera: Any? = null
    private var frameInjector: FrameInjector? = null

    @SuppressLint("WrongConstant")
    fun start(associationId: Int) {
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

            // Using Reflection for VirtualDeviceParams
            val paramsBuilderClass = Class.forName("android.companion.virtual.VirtualDeviceParams\$Builder")
            val paramsBuilder = paramsBuilderClass.newInstance()
            val setNameMethod = paramsBuilderClass.getMethod("setName", String::class.java)
            setNameMethod.invoke(paramsBuilder, "Azra_Virtual_Device")
            val buildParamsMethod = paramsBuilderClass.getMethod("build")
            val params = buildParamsMethod.invoke(paramsBuilder)

            // Using Reflection for createVirtualDevice
            val paramsClass = Class.forName("android.companion.virtual.VirtualDeviceParams")
            val createVirtualDeviceMethod = vdm.javaClass.getMethod("createVirtualDevice", Int::class.javaPrimitiveType, paramsClass)
            virtualDevice = createVirtualDeviceMethod.invoke(vdm, associationId, params)

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
        try {
            // Using Reflection for VirtualCameraConfig
            val configBuilderClass = Class.forName("android.companion.virtual.camera.VirtualCameraConfig\$Builder")
            val configBuilder = configBuilderClass.getConstructor(String::class.java).newInstance("AzraFrontCamera")

            // setLensFacing(VirtualCameraConfig.LENS_FACING_FRONT) -> LENS_FACING_FRONT is usually 0
            val setLensFacingMethod = configBuilderClass.getMethod("setLensFacing", Int::class.javaPrimitiveType)
            setLensFacingMethod.invoke(configBuilder, 0)
            
            // addStreamConfig(1920, 1080, android.graphics.ImageFormat.YUV_420_888, 30)
            val addStreamConfigMethod = configBuilderClass.getMethod("addStreamConfig", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            addStreamConfigMethod.invoke(configBuilder, 1920, 1080, android.graphics.ImageFormat.YUV_420_888, 30)
            
            val buildConfigMethod = configBuilderClass.getMethod("build")
            val config = buildConfigMethod.invoke(configBuilder)

            // createVirtualCamera
            val configClass = Class.forName("android.companion.virtual.camera.VirtualCameraConfig")
            val createVirtualCameraMethod = virtualDevice!!.javaClass.getMethod("createVirtualCamera", configClass)
            virtualCamera = createVirtualCameraMethod.invoke(virtualDevice, config)
            
            if (virtualCamera != null) {
                Log.i(TAG, "VirtualCamera created successfully via reflection!")
                
                // getInputSurface()
                val getInputSurfaceMethod = virtualCamera!!.javaClass.getMethod("getInputSurface")
                val surface = getInputSurfaceMethod.invoke(virtualCamera) as? Surface
                
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
            Log.e(TAG, "Failed to create VirtualCamera via reflection", e)
        }
    }

    fun stop() {
        frameInjector?.stop()
        frameInjector = null
        
        try {
            if (virtualCamera != null) {
                val closeMethod = virtualCamera!!.javaClass.getMethod("close")
                closeMethod.invoke(virtualCamera)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing virtual camera", e)
        }
        
        try {
            if (virtualDevice != null) {
                val closeDeviceMethod = virtualDevice!!.javaClass.getMethod("close")
                closeDeviceMethod.invoke(virtualDevice)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing virtual device", e)
        }
    }
}
