package com.example.securedatasharingfordtn.message

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.securedatasharingfordtn.database.StoredImageDao
import com.example.securedatasharingfordtn.database.StoredImageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MessageViewModel
    (val database: StoredImageDao, application: Application): ViewModel() {

    lateinit var imageList: List<StoredImageData>

    // selected image fragment variables
    private var _position = MutableLiveData<Int>()
    val position: LiveData<Int>
        get() = _position

    private var _image = MutableLiveData<ImageGridItem>()
    val image: LiveData<ImageGridItem>
        get() = _image

    fun setImagePosition(position:Int) {
        _position.value = position
    }

    fun setImageItem(image:ImageGridItem) {
        _image.value = image
    }


    //database variables
    private var _doneLoad = MutableLiveData<Boolean>()
    val doneLoad: LiveData<Boolean>
        get() = _doneLoad

    private var _doneStore = MutableLiveData<Boolean>()
    val doneStore: LiveData<Boolean>
        get() = _doneStore

    private var _doneDelete = MutableLiveData<Boolean>()
    val doneDelete: LiveData<Boolean>
        get() = _doneDelete

    init {
        _doneLoad.value = false
        _doneStore.value = false
        _doneDelete.value = false
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

    //insert a captured image
    private suspend fun insert(data: StoredImageData) {
        withContext(Dispatchers.IO) {
            database.insert(data)
        }
    }
    fun storeImage(item: ImageGridItem) {
        runBlocking {
            var image = StoredImageData(
                imageid = item.imageid,
                isowned = item.isowned,
                path = item.path,
                caption = item.caption,
                keywords = item.keywords,
                from = item.from,
                isencrypted = item.isencrypted,
                policy = item.policy,
                isrevoked = item.isrevoked,
                mission = item.mission
            )
            insert(image)
        }
        _doneStore.value = true
    }
    fun doneStoreEvent() {
        _doneStore.value = false
    }

    //delete a selected image
    private suspend fun delete(imageid: String) {
        withContext(Dispatchers.IO) {
            database.deleteImageById(imageid)
        }
    }
    fun deleteImage(imageid:String) {
        runBlocking {
            delete(imageid)
        }
        _doneDelete.value = true
    }
    fun doneDeleteEvent() {
        _doneDelete.value = false
    }
}