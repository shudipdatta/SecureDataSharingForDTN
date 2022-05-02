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
import java.io.File

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

    //selected image
    private var _imgTitle = MutableLiveData<String>()
    val imgTitle: LiveData<String>
        get() = _imgTitle

    fun setImgTitle(imgTitle:String) {
        _imgTitle.value = imgTitle
    }

    private var _imgFile = MutableLiveData<File>()
    val imgFile: LiveData<File>
        get() = _imgFile

    fun setImgFile(imgFile:File) {
        _imgFile.value = imgFile
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

    //insert a received image
    private suspend fun insert(data: StoredImageData) {
        withContext(Dispatchers.IO) {
            database.insert(data)
        }
    }
    fun storeImage(imageid:String, isowned:Boolean, path:String, caption:String, keywords:String) {
        runBlocking {
            var image = StoredImageData(
                imageid = imageid,
                isowned = isowned,
                path = path,
                caption = caption,
                keywords = keywords
            )
            insert(image)
        }
        _doneStore.value = true
    }
    fun doneStoreEvent() {
        _doneStore.value = false
    }
}