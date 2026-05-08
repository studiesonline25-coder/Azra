package com.azra

import android.annotation.SuppressLint
import android.companion.virtual.VirtualDeviceManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.Surface
import java.lang.reflect.Method

class VirtualCameraManager(private val context: Context) {

    private val TAG = "VirtualCameraManager"
    private var virtualDevice: Any? = null
    
    private var frontVirtualCamera: Any? = null
    private var backVirtualCamera: Any? = null
    
    private var frontFrameInjector: FrameInjector? = null
    private var backFrameInjector: FrameInjector? = null

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
            
            // Revert back to using 0 for associationId
            virtualDevice = createVirtualDeviceMethod.invoke(vdm, 0, params)

            if (virtualDevice == null) {
                Log.e(TAG, "Failed to create VirtualDevice")
                return
            }

            // Create both Front and Back virtual cameras
            setupVirtualCamera("AzraFrontCamera", 0) // LENS_FACING_FRONT = 0
            setupVirtualCamera("AzraBackCamera", 1)  // LENS_FACING_BACK = 1

        } catch (e: Exception) {
            Log.e(TAG, "Error starting VirtualCameraManager", e)
        }
    }

    @SuppressLint("NewApi")
    private fun setupVirtualCamera(cameraName: String, lensFacing: Int) {
        try {
            // Using Reflection for VirtualCameraConfig
            val configBuilderClass = Class.forName("android.companion.virtual.camera.VirtualCameraConfig\$Builder")
            val configBuilder = configBuilderClass.getConstructor(String::class.java).newInstance(cameraName)

            // setLensFacing
            val setLensFacingMethod = configBuilderClass.getMethod("setLensFacing", Int::class.javaPrimitiveType)
            setLensFacingMethod.invoke(configBuilder, lensFacing)
            
            // addStreamConfig(1920, 1080, android.graphics.ImageFormat.YUV_420_888, 30)
            val addStreamConfigMethod = configBuilderClass.getMethod("addStreamConfig", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            addStreamConfigMethod.invoke(configBuilder, 1920, 1080, android.graphics.ImageFormat.YUV_420_888, 30)
            
            val buildConfigMethod = configBuilderClass.getMethod("build")
            val config = buildConfigMethod.invoke(configBuilder)

            // createVirtualCamera
            val configClass = Class.forName("android.companion.virtual.camera.VirtualCameraConfig")
            val createVirtualCameraMethod = virtualDevice!!.javaClass.getMethod("createVirtualCamera", configClass)
            val virtualCamera = createVirtualCameraMethod.invoke(virtualDevice, config)
            
            if (virtualCamera != null) {
                Log.i(TAG, "VirtualCamera ($cameraName) created successfully via reflection!")
                
                if (lensFacing == 0) {
                    frontVirtualCamera = virtualCamera
                } else {
                    backVirtualCamera = virtualCamera
                }
                
                // getInputSurface()
                val getInputSurfaceMethod = virtualCamera.javaClass.getMethod("getInputSurface")
                val surface = getInputSurfaceMethod.invoke(virtualCamera) as? Surface
                
                if (surface != null) {
                    val injector = FrameInjector(surface, 1920, 1080)
                    injector.start()
                    
                    if (lensFacing == 0) {
                        frontFrameInjector = injector
                    } else {
                        backFrameInjector = injector
                    }
                } else {
                    Log.e(TAG, "VirtualCamera ($cameraName) InputSurface is null")
                }
            } else {
                Log.e(TAG, "VirtualCamera ($cameraName) is null after creation")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create VirtualCamera ($cameraName) via reflection", e)
        }
    }

    fun stop() {
        frontFrameInjector?.stop()
        frontFrameInjector = null
        
        backFrameInjector?.stop()
        backFrameInjector = null
        
        try {
            if (frontVirtualCamera != null) {
                val closeMethod = frontVirtualCamera!!.javaClass.getMethod("close")
                closeMethod.invoke(frontVirtualCamera)
                frontVirtualCamera = null
            }
            if (backVirtualCamera != null) {
                val closeMethod = backVirtualCamera!!.javaClass.getMethod("close")
                closeMethod.invoke(backVirtualCamera)
                backVirtualCamera = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing virtual cameras", e)
        }
        
        try {
            if (virtualDevice != null) {
                val closeDeviceMethod = virtualDevice!!.javaClass.getMethod("close")
                closeDeviceMethod.invoke(virtualDevice)
                virtualDevice = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error closing virtual device", e)
        }
    }
}
