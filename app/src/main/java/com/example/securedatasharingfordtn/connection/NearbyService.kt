package com.example.securedatasharingfordtn.connection

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.collection.SimpleArrayMap
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.securedatasharingfordtn.GlobalApp
import com.example.securedatasharingfordtn.congestion.EndpointInfo
import com.example.securedatasharingfordtn.database.StoredImageDao
import com.example.securedatasharingfordtn.database.StoredImageData
import com.google.android.gms.common.util.IOUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets

class NearbyService : Service() {

    private val TAG = "NearbyService"
    private val SERVICE_ID = "Nearby"
    private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    private val context: Context = this

    var endpointIDNameMap: HashMap<String, String> = HashMap()
    var endpointNameIDMap: HashMap<String, String> = HashMap()

    //provided service info
    private lateinit var userProfile: EndpointInfo
    private lateinit var dataSource: StoredImageDao
    private var textMsg: String? = null
    private var imageMsg: File? = null
    private var rcvdFilename: String? = null
    private val incomingFilePayloads = SimpleArrayMap<Long, Payload>()
    private val completedFilePayloads = SimpleArrayMap<Long, Payload>()
    private val filePayloadFilenames = SimpleArrayMap<Long, String>()

    //fetched service info
    var endpoints: HashMap<String, EndpointInfo> = HashMap() // <EndpointName, Info>

    //test mutable live data
    private var _updateEndpoint = MutableLiveData<Boolean>()
    val updateEndpoint: LiveData<Boolean>
        get() = _updateEndpoint
    fun doneUpdateEndpoint() {
        _updateEndpoint.value = false
    }

    override fun onCreate() {
        _updateEndpoint.value = false
    }

    override fun onDestroy() {
        super.onDestroy()
        Nearby.getConnectionsClient(context).stopAllEndpoints()
        discoveryHandler.removeCallbacks(discoverRunnable)
        advertiserHandler.removeCallbacks(advertiserRunnable)
        stopSelf()
    }

    inner class ConServiceBinder : Binder() {
        // Return this instance of MyService so clients can call public methods
        fun getService(): NearbyService = this@NearbyService
    }

    private val conServiceBinder: IBinder = ConServiceBinder()
    override fun onBind(intent: Intent): IBinder {
        return conServiceBinder
    }

    /* Information Receive Function */

    fun serviceInitInfo(userProfile: EndpointInfo, dataSource: StoredImageDao) {
        if(!this::userProfile.isInitialized) { //initializing multiple time creates error in advertise and discovery

            this.userProfile = userProfile
            this.dataSource = dataSource

            advertiserHandler.post(advertiserRunnable)
            discoveryHandler.post(discoverRunnable)
        }
    }

    fun setImageInfo(textMsg: String, imageMsg: File) {
        this.textMsg = textMsg
        this.imageMsg = imageMsg
    }

    /* Service Active Function */

    var advertiserHandler: Handler = Handler(Looper.getMainLooper())
    var advertiserRunnable = Runnable {
        Log.d(TAG, "Inside advertiser runnable")
        startAdvertising()
    }

    var discoveryHandler: Handler = Handler(Looper.getMainLooper())
    var discoverRunnable = Runnable {
        Log.d(TAG, "Inside Runnable searching")
        startDiscovery()
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        val initdata = userProfile.name + "\t" + userProfile.username!! + "\t" + userProfile.userattrs!!
        Nearby.getConnectionsClient(context)
            .startAdvertising(
                initdata, SERVICE_ID, connectionLifeCycleCallback, advertisingOptions
            )
            .addOnSuccessListener(
                OnSuccessListener { unused: Void? ->
                    Log.d(TAG, "We're advertising!")
                })
            .addOnFailureListener(
                OnFailureListener { e: Exception? ->
                    Log.d(TAG, "We were unable to start advertising.")
                })
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(context)
            .startDiscovery(SERVICE_ID, endpointDiscoveryCallback, discoveryOptions)
            .addOnSuccessListener { unused: Void? ->
                Log.d(TAG, "We're discovering!")
            }
            .addOnFailureListener { e: Exception? ->
                Log.d(TAG, "We were unable to start discovering!")
            }
    }

    private val endpointDiscoveryCallback: EndpointDiscoveryCallback =
        object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                Log.d(TAG,"An endpoint was found. We request a connection to it.")

                if (info.endpointName != userProfile.name && !endpointIDNameMap.contains(endpointId)) {
                    val initData = info.endpointName.split("\t")
                    //create a endpoint object, however, it doesn't have any data till now
                    val endpoint = EndpointInfo()
                    endpoint.name = initData[0]
                    endpoint.username = initData[1]
                    endpoint.userattrs = initData[2].replace(',',' ')
                    endpoints[initData[0]] = endpoint

                    endpointIDNameMap[endpointId] = initData[0]
                    endpointNameIDMap[initData[0]] = endpointId

                    _updateEndpoint.value = true
                }
            }

            override fun onEndpointLost(endpointId: String) {
                Log.d(TAG, "A previously discovered endpoint has gone away.")

                if (endpointIDNameMap.contains(endpointId)) {
                    val endpointName = endpointIDNameMap[endpointId]
                    endpoints.remove(endpointName)

                    endpointIDNameMap.remove(endpointId) //so many ids will be generated in every second
                    endpointNameIDMap.remove(endpointName)

                    _updateEndpoint.value = true
                }
            }
        }

    fun setConnection(endpointName: String) {
        Nearby.getConnectionsClient(context).requestConnection(
            "",
            endpointNameIDMap[endpointName]!!,
            connectionLifeCycleCallback
        )
            .addOnSuccessListener { unused: Void? ->
                Log.d(TAG,"We successfully requested a connection. Now both sides must accept before the connection is established.")
            }
            .addOnFailureListener { e: java.lang.Exception? ->
                Log.d(TAG, "Nearby Connections failed to request the connection.")
            }
    }

    private val connectionLifeCycleCallback: ConnectionLifecycleCallback =
        object : ConnectionLifecycleCallback() {

            override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
                // Automatically accept the connection on both sides.
                Log.d(TAG, "Inside onConnectionInitiated")
                Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                when (result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        Log.d(TAG, "We're connected! Can now start sending and receiving data.")
                        sendPayload(endpointId, -1)

                    }

                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED ->
                        Log.d(TAG,"The connection was rejected by one or both sides.")
                    ConnectionsStatusCodes.STATUS_ERROR ->
                        Log.d( TAG,"The connection broke before it was able to be accepted.")
                    else ->
                        Log.d(TAG, "Unknown status code")
                }
            }

            override fun onDisconnected(endpointId: String) {
                Log.d(TAG,"We've been disconnected from this endpoint. No more data can be sent or received.")
                val endpointName = endpointIDNameMap[endpointId]
                endpoints.remove(endpointName)
            }
        }

    private fun sendPayload(endpointId: String, msgType: Int) {
        if (textMsg != null) {
            val filenamePayload = Payload.fromBytes(textMsg!!.toByteArray())
            Nearby.getConnectionsClient(context).sendPayload(endpointId, filenamePayload)
        }
        if (imageMsg != null) {
            val pfd = contentResolver.openFileDescriptor(imageMsg!!.toUri(), "r")
            val filePayload = Payload.fromFile(pfd!!)
            Nearby.getConnectionsClient(applicationContext).sendPayload(endpointId, filePayload)
        }
    }

    private val payloadCallback: PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            // A new payload is being sent over.
            Log.d(TAG, "Payload Received")
            when (payload.type) {
                Payload.Type.BYTES -> {
                    rcvdFilename = String(payload.asBytes()!!, StandardCharsets.UTF_8)

                    //val encryptedFilename = ReVo_ABE.decrypt(context.pairing,context.publicKey, rcvdFilename, context.privateKey)

//                    val rcvdPayload = String(payload.asBytes()!!, StandardCharsets.UTF_8)
//                    val msgType = (rcvdPayload.split("|")[0]).toInt()
//                    val rcvdData = rcvdPayload.split("|")[1]
//                    //rcvdFilename = String(ReVo_ABE.decrypt(pairing,publicKey, Ciphertext(payload.asBytes()!!,pairing),privateKey) )
//                    when (msgType) {
//                        EndpointInfo.MsgInitInfo -> {
//                            val username = rcvdData.split("\t")[0]
//                            val userattrs = rcvdData.split("\t")[1]
//                            val endpointName = endpointIDNameMap[endpointId]
//                            endpoints.get(endpointName)!!.status = 1 //connected
//                            endpoints.get(endpointName)!!.username = username
//                            endpoints.get(endpointName)!!.userattrs = userattrs
//                            //initial info is there, so reload the gui
//                            serviceCallbacks?.refreshConnectionList(endpoints)
//                        }
//                        EndpointInfo.MsgDirectory -> {
//                        }
//                        EndpointInfo.MsgReward -> {
//                        }
//                    }
                }
                Payload.Type.FILE -> {
                    // Add this to our tracking map, so that we can retrieve the payload later.
                    incomingFilePayloads.put(payload.id, payload);
                }
                Payload.Type.STREAM -> {
                    Log.d(TAG, "Inside file mode")
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
                val payloadId = update.payloadId
                val payload = incomingFilePayloads.remove(payloadId)
                completedFilePayloads.put(payloadId, payload)
                if (payload != null && payload.type == Payload.Type.FILE) {
                    val isDone = processFilePayload(payloadId)
                    if (isDone) {
                        //Nearby.getConnectionsClient(context).disconnectFromEndpoint(endpointId)
                    }
                }
            }
        }

        private fun processFilePayload(payloadId: Long): Boolean {
            // BYTES and FILE could be received in any order, so we call when either the BYTES or the FILE
            // payload is completely received. The file payload is considered complete only when both have
            // been received.
            val filePayload = completedFilePayloads[payloadId]
            if (filePayload != null && rcvdFilename != null) {
                completedFilePayloads.remove(payloadId)
                filePayloadFilenames.remove(payloadId)

                // Get the received file (which will be in the Downloads folder)
                // Because of https://developer.android.com/preview/privacy/scoped-storage, we are not
                // allowed to access filepaths from another process directly. Instead, we must open the
                // uri using our ContentResolver.
                val uri: Uri? = filePayload.asFile()!!.asUri()
                try {
                    // Copy the file to a new location.
                    val `in`: InputStream? = context.contentResolver.openInputStream(uri!!)
                    //copyStream(`in`, FileOutputStream(File(context.cacheDir, rcvdFilename)))
                    val movedFile = getPhotoFileUri(rcvdFilename!!, "collected_images")
                    IOUtils.copyStream(`in`, FileOutputStream(movedFile))
                    //store in database
                    storeImage(rcvdFilename!!, false, movedFile.path, "", "")
                } catch (e: IOException) {
                    // Log the error.
                } finally {
                    // Delete the original file.
                    context.contentResolver.delete(uri!!, null, null)
                    Toast.makeText(applicationContext, "File Received $rcvdFilename", Toast.LENGTH_LONG).show()
                    rcvdFilename = null
                }

                return true
            }
            return false
        }

    }
    // Returns the File for a photo stored on disk given the fileName
    fun getPhotoFileUri(fileName: String, folder: String): File {
        // Use `getExternalFilesDir` on Context to access package-specific directories. // This way, we don't need to request external read/write runtime permissions.
        val mediaStorageDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), folder)

        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            Log.d("ConnectionActivity", "failed to create directory")
        }

        return File(mediaStorageDir.getPath() + File.separator.toString() + fileName)
    }

    //insert a received image
    private suspend fun insert(data: StoredImageData) {
        withContext(Dispatchers.IO) {
            dataSource.insert(data)
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
    }
}