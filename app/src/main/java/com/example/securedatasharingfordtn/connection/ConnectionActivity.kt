package com.example.securedatasharingfordtn.connection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.securedatasharingfordtn.databinding.ActivityConnectionBinding

class ConnectionActivity : AppCompatActivity() {
    companion object {
        private const val LOCATION_PERMISSION_CODE = 100
        private const val READ_PERMISSION_CODE = 101
    }
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, READ_PERMISSION_CODE)
    }

    //Enables back button support. Simply navigates one element up on the stack
    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    private fun createFragment() {
        val binding = ActivityConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun goBack() {
        finish()
    }

    private fun checkPermission(permission: String, requestCode: Int) {
        if (ContextCompat.checkSelfPermission(this,permission) == PackageManager.PERMISSION_DENIED) {
            // Requesting the permission
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
        }
        else {
            if (requestCode == LOCATION_PERMISSION_CODE) {
                createFragment()
            }

            if (requestCode == READ_PERMISSION_CODE) {
                checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, LOCATION_PERMISSION_CODE)
            }
        }
    }

    /** This function is called when the user accepts or decline the permission.*/
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                createFragment()
            } else {
                Toast.makeText(this,"Location Permission Denied", Toast.LENGTH_SHORT).show()
                goBack()
            }
        } else if (requestCode == READ_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkPermission(Manifest.permission.ACCESS_FINE_LOCATION, LOCATION_PERMISSION_CODE)
            } else {
                Toast.makeText(this,"Read Storage Permission Denied", Toast.LENGTH_SHORT).show()
                goBack()
            }
        }
    }
}