package com.example.securedatasharingfordtn
import android.app.Application

class GlobalApp: Application() {
//    companion object {
//        private lateinit var deviceId: String
//        fun setDeviceID(id:String) {
//            deviceId = id
//        }
//        fun getDeviceID(): String {
//            return deviceId
//        }
//    }

    private var username: String? = null
    private var attributes: String? = null


    fun getUserName(): String? {
        return username
    }

    fun setUserName(uname: String?) {
        username = uname
    }

    fun getAttributes(): String? {
        return attributes
    }

    fun setAttributes(attrs: String?) {
        attributes = attrs
    }

//    override fun onCreate() {
//        super.onCreate()
//        setDeviceID(Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID))
//        Log.d("Inside GLOBAL", getDeviceID())
//    }
}