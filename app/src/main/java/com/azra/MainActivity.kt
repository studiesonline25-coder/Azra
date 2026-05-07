package com.azra

import android.Manifest
import android.app.Activity
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSIONS_REQUEST_CODE = 1001
    private val TAG = "MainActivity"
    private lateinit var btnToggle: Button

    private val associationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val associationInfo = result.data?.getParcelableExtra<AssociationInfo>(
                CompanionDeviceManager.EXTRA_ASSOCIATION,
                AssociationInfo::class.java
            )
            if (associationInfo != null) {
                Log.i(TAG, "Associated with device, ID: ${associationInfo.id}")
                Toast.makeText(this, "Paired successfully!", Toast.LENGTH_SHORT).show()
                startAzraService(associationInfo.id)
            }
        } else {
            Toast.makeText(this, "Device pairing cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btnToggle)
        btnToggle.setOnClickListener {
            if (checkPermissions()) {
                if (btnToggle.text == getString(R.string.start_service)) {
                    attemptStartService()
                } else {
                    stopAzraService()
                }
            } else {
                requestPermissions()
            }
        }
    }

    private fun attemptStartService() {
        val deviceManager = getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
        
        // Check if we already have an association
        val existingAssociations = deviceManager.myAssociations
        if (existingAssociations.isNotEmpty()) {
            val association = existingAssociations.first()
            Log.i(TAG, "Found existing association: ${association.id}")
            startAzraService(association.id)
            return
        }

        // We don't have an association, request one
        Log.i(TAG, "Requesting Companion Device Association for GLASSES profile")
        val request = AssociationRequest.Builder()
            .setDeviceProfile(AssociationRequest.DEVICE_PROFILE_GLASSES)
            .build()

        deviceManager.associate(
            request,
            mainExecutor,
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: android.content.IntentSender) {
                    associationLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }

                override fun onFailure(error: CharSequence?) {
                    Log.e(TAG, "Association failed: $error")
                    Toast.makeText(this@MainActivity, "Association failed: $error", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun startAzraService(associationId: Int) {
        val intent = Intent(this, AzraService::class.java)
        intent.putExtra("associationId", associationId)
        ContextCompat.startForegroundService(this, intent)
        btnToggle.text = getString(R.string.stop_service)
    }

    private fun stopAzraService() {
        val intent = Intent(this, AzraService::class.java)
        stopService(intent)
        btnToggle.text = getString(R.string.start_service)
    }

    private fun checkPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.FOREGROUND_SERVICE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_CAMERA)
        }
        
        for (p in permissions) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.FOREGROUND_SERVICE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_CAMERA)
        }

        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSIONS_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_LONG).show()
            }
        }
    }
}
