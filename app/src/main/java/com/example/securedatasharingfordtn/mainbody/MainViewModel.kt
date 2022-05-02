package com.example.securedatasharingfordtn.mainbody

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.securedatasharingfordtn.database.LoginUserDao
import com.example.securedatasharingfordtn.database.LoginUserData
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.features.json.*
import kotlinx.coroutines.*


class MainViewModel(
    val database: LoginUserDao,
    application: Application): AndroidViewModel(application) {

    private var keys: ByteArray = byteArrayOf()
    private lateinit var user: LoginUserData
    val client = HttpClient(Android) {
        engine {
            connectTimeout = 100_000
            socketTimeout = 100_000
        }
        install(JsonFeature){
            serializer = GsonSerializer()
        }
    }

    //asyncronized job for database
    private var viewModelJob = Job()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)



    //login error snackbar indicator
    private var _manageConnection = MutableLiveData<Boolean>()
    val manageConnection: LiveData<Boolean>
        get() = _manageConnection

    private var _manageMembers = MutableLiveData<Boolean>()
    val manageMembers: LiveData<Boolean>
        get() = _manageMembers

    private var _manageProfile = MutableLiveData<Boolean>()
    val manageProfile: LiveData<Boolean>
        get() = _manageProfile

    private var _manageMessage = MutableLiveData<Boolean>()
    val manageMessage: LiveData<Boolean>
        get() = _manageMessage


    //login error snackbar indicator functions
    fun doneSetupConnectionEvent(){
        _manageConnection.value = false
    }
    fun setupConnection(){
        _manageConnection.value = true
    }

    fun setupRevocation(){
        _manageMembers.value = true
    }

    fun doneSetupRevocationEvent(){
        _manageMembers.value = false
    }

    fun setupProfile() {
        _manageProfile.value = true
    }

    fun doneSetupProfileEvent() {
        _manageProfile.value = false
    }

    fun setupMessage() {
        _manageMessage.value = true
    }

    fun doneSetupMessageEvent() {
        _manageMessage.value = false
    }
}