package com.example.securedatasharingfordtn.message

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.securedatasharingfordtn.databinding.MessageActivityBinding

class MessageActivity : AppCompatActivity() {
    companion object {
        private const val READ_PERMISSION_CODE = 101
    }
//    private var flag = false
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
        val binding = MessageActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        // Get the navigation host fragment from this Activity
//        val navHostFragment = supportFragmentManager
//            .findFragmentById(R.id.nav_message_fragment) as NavHostFragment
//        // Instantiate the navController using the NavHostFragment
//        navController = navHostFragment.navController
//        // Make sure actions in the ActionBar get propagated to the NavController
//        setupActionBarWithNavController(navController)
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
            if (requestCode == READ_PERMISSION_CODE) {
                //Toast.makeText(this, "Permission already granted", Toast.LENGTH_SHORT).show()
                createFragment()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == READ_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //Toast.makeText(this,"Read Storage Permission Granted", Toast.LENGTH_SHORT).show()
                createFragment()
            } else {
                Toast.makeText(this,"Read Storage Permission Denied", Toast.LENGTH_SHORT).show()
                goBack()
            }
        }
    }
}