package com.example.securedatasharingfordtn.profile

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.securedatasharingfordtn.Preferences
import com.example.securedatasharingfordtn.database.LoginUserDao
import com.example.securedatasharingfordtn.database.LoginUserData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ProfileViewModel
    (val database: LoginUserDao, application: Application): ViewModel() {

    lateinit var user: LoginUserData

    private var _doneLoad = MutableLiveData<Boolean>()
    val doneLoad: LiveData<Boolean>
        get() = _doneLoad

    init {
        _doneLoad.value = false
    }

    private suspend fun getLoginData(): LoginUserData?{
        return withContext(Dispatchers.IO){
            val tryUser = database.getLoginData()
            tryUser
        }
    }

    fun fetchUserData(userid: Int?) {
        Log.i("UserID", "userid$userid")

        runBlocking {
            val tryUser = getLoginData() //database.tryLogin(username.value!!,password.value!!)

            //add user with none expired date.
            if(tryUser!=null && (true || tryUser.expirationDate > System.currentTimeMillis())) {
                runBlocking {
                    user = tryUser
                }
                Log.i("User Info", "found user in the database")
            }
        }
        _doneLoad.value = true
    }


}