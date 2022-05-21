package com.example.securedatasharingfordtn.connection

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.collection.SimpleArrayMap
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.securedatasharingfordtn.Preferences
import com.example.securedatasharingfordtn.R
import com.example.securedatasharingfordtn.congestion.EndpointInfo
import com.example.securedatasharingfordtn.database.StoredImageDao
import com.example.securedatasharingfordtn.database.StoredImageData
import com.example.securedatasharingfordtn.revoabe.Ciphertext
import com.example.securedatasharingfordtn.revoabe.PrivateKey
import com.example.securedatasharingfordtn.revoabe.PublicKey
import com.example.securedatasharingfordtn.revoabe.ReVo_ABE
import com.google.android.gms.common.util.IOUtils
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import it.unisa.dia.gas.jpbc.Pairing
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory
import it.unisa.dia.gas.plaf.jpbc.util.Arrays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class NearbyService : Service() {
    companion object {
        private const val OWN_IMAGE_FOLDER = "own_images"
        private const val COLLECTED_IMAGE_FOLDER = "collected_images"
        private const val ENCRYPTED_PREFIX = "encrypted"
    }

    private val TAG = "NearbyService"
    private val SERVICE_ID = "Nearby"
    private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    private val context: Context = this

    var endpointIDNameMap: HashMap<String, String> = HashMap()
    var endpointNameIDMap: HashMap<String, String> = HashMap()

    //provided service info
    private lateinit var userProfile: EndpointInfo
    private lateinit var dataSource: StoredImageDao

    private var rcvdFilename: String? = null
    private var policyMsg: String? = null
    private var receiverRLindex: Int = -1
    private var imageItem: ImageListItem? = null

    lateinit var privateKey: PrivateKey
    lateinit var publicKey: PublicKey
    lateinit var pairing: Pairing

    private lateinit var keys: ByteArray
    private lateinit var pairingDir: String
    private lateinit var preferences: Preferences

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

    fun serviceInitInfo(userProfile: EndpointInfo, dataSource: StoredImageDao, pairingDir: String, keys: ByteArray, preferences: Preferences) {
        if(!this::userProfile.isInitialized) { //initializing multiple time creates error in advertise and discovery

            this.userProfile = userProfile
            this.dataSource = dataSource
            this.pairingDir = pairingDir
            this.keys = keys
            this.preferences = preferences

            //set encrypt/decrypt variables
            this.pairing = PairingFactory.getPairing(pairingDir)
            val publickeySize = ByteBuffer.wrap(keys,0,4).order(ByteOrder.nativeOrder()).int
            val privatekeySize = ByteBuffer.wrap(keys,publickeySize+4,4).order(ByteOrder.nativeOrder()).int
            this.publicKey = PublicKey(Arrays.copyOfRange(keys,4,publickeySize+4),this.pairing)
            this.privateKey = PrivateKey(Arrays.copyOfRange(keys,8+publickeySize,8+publickeySize+privatekeySize),this.pairing)

            advertiserHandler.post(advertiserRunnable)
            discoveryHandler.post(discoverRunnable)
        }
    }

    fun setImageInfo(item: ImageListItem, policyMsg: String, receiverRLindex: Int) {
        this.imageItem = item
        this.policyMsg = policyMsg
        this.receiverRLindex = receiverRLindex
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
        val initdata = userProfile.name + "\t" + userProfile.username!! + "\t" + userProfile.userattrs!! + "\t" + userProfile.userinterests!!
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
                    endpoint.userinterests = initData[3].replace(',', ' ')
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
        if (imageItem != null) {
            var revoked = "false"
            var revokedList = listOf<Int>()
            for(RLStr in preferences.getRevokedMembers()) { //.toList()){
                if (this.receiverRLindex == RLStr.toInt()) {
                    revoked = "true"
                }
                revokedList += RLStr.toInt()+1
            }

            var textMsg = imageItem!!.imageid + "\t" + imageItem!!.caption + "\t" + imageItem!!.keywords  +
                    "\t" + imageItem!!.mission  + "\t" + revoked + "\t"
            textMsg += if (imageItem!!.isencrypted) imageItem!!.policy  else this.policyMsg
            val filenamePayload = Payload.fromBytes(textMsg!!.toByteArray())
            Nearby.getConnectionsClient(context).sendPayload(endpointId, filenamePayload)

            val storedFile: File
            if (imageItem!!.isencrypted) {
                storedFile = getPhotoFileUri(ENCRYPTED_PREFIX + imageItem!!.imageid, COLLECTED_IMAGE_FOLDER) //if the file is encrypted, it is definitely in collected folder
            }
            else {
                val folder = if(imageItem!!.isowned) OWN_IMAGE_FOLDER else COLLECTED_IMAGE_FOLDER
                val imageFile = getPhotoFileUri(imageItem!!.imageid, folder)
                val encryptedFile = ReVo_ABE.encrypt(this.pairing, this.publicKey, imageFile!!.readBytes(), policyMsg, revokedList)
                storedFile = getPhotoFileUri("encrypted_file", OWN_IMAGE_FOLDER) //if the file is plain, keep it as encrypted in own-image folder and send this
                storedFile.writeBytes(encryptedFile.toByteArray())
            }

            val pfd = contentResolver.openFileDescriptor(storedFile!!.toUri(), "r")
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
                    val isDone = processFilePayload(payloadId, endpointId)
                    if (isDone) {
                        Nearby.getConnectionsClient(context).disconnectFromEndpoint(endpointId) //test
                    }
                }
            }
        }

        private fun processFilePayload(payloadId: Long, endpointId: String): Boolean {
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

                    val imageMetadata = rcvdFilename!!.split("\t")
                    val imageName = imageMetadata[0]
                    val imageCaption = imageMetadata[1]
                    val imageKeywords = imageMetadata[2]
                    val mission = imageMetadata[3]
                    val revoked = imageMetadata[4]
                    val policy = imageMetadata[5]

                    val isrevoked = (revoked=="true")

                    //check if policy exits in receiver attributes
                    val policyArray = policy.split(",")
                    val attributesArray = preferences.getUserAttrs()!!.split(",")
                    val intersectArray = policyArray.intersect(attributesArray)

                    val isencrypted: Boolean
                    val movedFile = getPhotoFileUri(imageName, COLLECTED_IMAGE_FOLDER)

                    if (intersectArray.isEmpty() || mission != preferences.getMission()) { //keep as encrypted
                        val encryptedFile = getPhotoFileUri(ENCRYPTED_PREFIX + imageName, COLLECTED_IMAGE_FOLDER)
                        IOUtils.copyStream(`in`!!, FileOutputStream(encryptedFile))
                        val invalidFile: Bitmap =
                            if (mission != preferences.getMission()) BitmapFactory.decodeResource(resources, R.drawable.invalid_perm)
                            else BitmapFactory.decodeResource(resources, R.drawable.invalid_attr)
                        val stream: OutputStream = FileOutputStream(movedFile)
                        invalidFile.compress(Bitmap.CompressFormat.JPEG,100,stream)
                        stream.flush()
                        stream.close()
                        isencrypted = true
                    }
                    else { //decrypt
                        if (isrevoked) { //no encrypted file is saved, just the fake file
                            val stream: OutputStream = FileOutputStream(movedFile)
                            val invalidFile = BitmapFactory.decodeResource(resources, R.drawable.revoked)
                            invalidFile.compress(Bitmap.CompressFormat.JPEG,100,stream)
                            stream.flush()
                            stream.close()
                        }
                        else {//decrypt
                            val storedFile = getPhotoFileUri("encrypted_file", COLLECTED_IMAGE_FOLDER)
                            IOUtils.copyStream(`in`!!, FileOutputStream(storedFile))
                            val decryptedByteArray = ReVo_ABE.decrypt(pairing, publicKey, Ciphertext(storedFile.readBytes(), pairing), privateKey)
                            movedFile.writeBytes(decryptedByteArray)
                        }
                        isencrypted = false
                    }

                    //store in database
                    storeImage(imageName, false, movedFile.path, imageCaption, imageKeywords,
                        endpointIDNameMap[endpointId]!!, isencrypted, policy, isrevoked, mission)

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
    fun storeImage(imageid:String, isowned:Boolean, path:String, caption:String, keywords:String,
                   from: String, isencrypted: Boolean, policy: String, isrevoked: Boolean, mission: String) {
        runBlocking {
            var image = StoredImageData(
                imageid = imageid,
                isowned = isowned,
                path = path,
                caption = caption,
                keywords = keywords,
                from = from,
                isencrypted = isencrypted,
                policy = policy,
                isrevoked = isrevoked,
                mission = mission
            )
            insert(image)
        }
    }
}