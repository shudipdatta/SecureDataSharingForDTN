package com.example.securedatasharingfordtn.connection

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.securedatasharingfordtn.database.StoredImageDao
import com.example.securedatasharingfordtn.database.StoredImageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class ConnectionViewModel
    (val database: StoredImageDao, application: Application) : ViewModel() {

    lateinit var imageList: List<StoredImageData>

    // selected connection
    private var _conName = MutableLiveData<String>()
    val conName: LiveData<String>
        get() = _conName

    fun setConName(conName:String) {
        _conName.value = conName
    }

    private var _policy = MutableLiveData<String>()
    val policy: LiveData<String>
        get() = _policy

    fun setPolicy(policy:String) {
        _policy.value = policy
    }

    //selected image
    private var _image = MutableLiveData<ImageListItem>()
    val image: LiveData<ImageListItem>
        get() = _image

    fun setImageItem(image:ImageListItem) {
        _image.value = image
    }

    private var _isSelected = MutableLiveData<Boolean>()
    val isSelected: LiveData<Boolean>
        get() = _isSelected

    fun isSelectedImage(isSelected: Boolean) {
        _isSelected.value = isSelected
    }

    //database variables
    private var _doneLoad = MutableLiveData<Boolean>()
    val doneLoad: LiveData<Boolean>
        get() = _doneLoad

    private var _doneStore = MutableLiveData<Boolean>()
    val doneStore: LiveData<Boolean>
        get() = _doneStore

    init {
        _doneLoad.value = false
        _doneStore.value = false
        _isSelected.value = false
    }

    //load all the images
    private suspend fun getImagesData(): List<StoredImageData> {
        return withContext(Dispatchers.IO){
            val images = database.getAllImages()
            images!!
        }
    }
    fun fetchStoredImages() {
        runBlocking {
            imageList = getImagesData()
        }
        _doneLoad.value = true
    }
    fun doneLoadEvent() {
        _doneLoad.value = false
    }
}