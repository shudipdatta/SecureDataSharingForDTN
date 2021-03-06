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
import com.example.securedatasharingfordtn.GlobalApp
import com.example.securedatasharingfordtn.congestion.EndpointInfo
import com.example.securedatasharingfordtn.revoabe.PrivateKey
import com.example.securedatasharingfordtn.revoabe.PublicKey
import com.google.android.gms.common.util.IOUtils.copyStream
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import it.unisa.dia.gas.jpbc.Pairing
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory
import it.unisa.dia.gas.plaf.jpbc.util.Arrays
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/***check service condition on force stop***/

class ConnectionService : Service() {

    val TAG = "NearbyService"
    val SERVICE_ID = "AFRL_project"
    lateinit var DEVICE_ID: String
    //test
    lateinit var globalVariable: GlobalApp

    private val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    private val context: Context = this
    var endpointIDConnected: ArrayList<String> = ArrayList()
    var endpointIDNameMap: HashMap<String, String> = HashMap()
    var endpointNameIDMap: HashMap<String, String> = HashMap()
    var endpoints: HashMap<String, EndpointInfo> = HashMap() // <EndpointName, Info>

    private val conServiceBinder: IBinder = ConServiceBinder()
    private var serviceCallbacks: ServiceCallbacks? = null

    var policyMsg: String? = null
    var textMsg: String? = null
    var imageMsg: File? = null
    var rcvdFilename: String? = null
    private val incomingFilePayloads = SimpleArrayMap<Long, Payload>()
    private val completedFilePayloads = SimpleArrayMap<Long, Payload>()
    private val filePayloadFilenames = SimpleArrayMap<Long, String>()

    lateinit var privateKey: PrivateKey
    lateinit var publicKey: PublicKey
    lateinit var pairing: Pairing

    interface ServiceCallbacks {
        fun refreshConnectionList(endpoints: HashMap<String, EndpointInfo>)
    }
    fun setCallbacks(callbacks: ServiceCallbacks?) {
        if (callbacks != null) {
            serviceCallbacks = callbacks
        }
    }

    inner class ConServiceBinder : Binder() {
        // Return this instance of MyService so clients can call public methods
        fun getService(): ConnectionService = this@ConnectionService
    }

    override fun onBind(intent: Intent): IBinder {
        val bundle = intent.extras
        val keys = bundle!!.getByteArray("keys")
        val pairingDir = bundle.getString("pairingDir")
        if (keys !=null && pairingDir != null)
            bootstrap(pairingDir,keys)
        return conServiceBinder
    }
    private fun bootstrap(dirForPairingFile : String, keys: ByteArray){
        Log.i("ConnectionService", dirForPairingFile)
        this.pairing = PairingFactory.getPairing(dirForPairingFile)
        val publickeySize = ByteBuffer.wrap(keys,0,4).order(ByteOrder.nativeOrder()).getInt()
        val privatekeySize = ByteBuffer.wrap(keys,publickeySize+4,4).order(ByteOrder.nativeOrder()).getInt()
        this.publicKey = PublicKey(Arrays.copyOfRange(keys,4,publickeySize+4),this.pairing)
        this.privateKey = PrivateKey(Arrays.copyOfRange(keys,8+publickeySize,8+publickeySize+privatekeySize),this.pairing)
        Log.i("bootstrap",publicKey.getString()+privateKey.getString())

    }

    override fun onCreate() {
        Log.d(TAG, "Inside onCreate ConnectionService")
        DEVICE_ID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID)

        //test
        globalVariable = applicationContext as GlobalApp
        //val username = globalVariable.getUserName()
        //val userattrs = globalVariable.getAttributes()
        //Log.d("Name + Attrs",username!! + "|" + userattrs!!)

        advertiserHandler.post(advertiserRunnable)
        discoveryHandler.post(discoverRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Inside onDestroy ConnectionService")
        Nearby.getConnectionsClient(context).stopAllEndpoints()
        discoveryHandler.removeCallbacks(discoverRunnable)
        advertiserHandler.removeCallbacks(advertiserRunnable)
        stopSelf()
    }

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
        val username = globalVariable.getUserName()
        val userattrs = globalVariable.getAttributes()
        val initdata = DEVICE_ID + "\t" + username!! + "\t" + userattrs!!
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
                if (info.endpointName != DEVICE_ID && !endpointIDConnected.contains(endpointId)) {
                    endpointIDConnected.add(endpointId)
                    val initData = info.endpointName.split("\t")
                    if (!endpoints.containsKey(info.endpointName)) {
                        //create a endpoint object, however, it doesn't have any data till now
                        val endpoint = EndpointInfo()
                        endpoint.name = initData[0]
                        endpoint.username = initData[1]
                        endpoint.userattrs = initData[2].replace(',',' ')
                        endpoints.put(initData[0], endpoint)
                    }

                    endpointIDNameMap[endpointId] = initData[0]
                    endpointNameIDMap[initData[0]] = endpointId

//                    if (endpoints.get(info.endpointName)!!.status  != -1) {
//                        //initial info is there, so reload the gui
                        serviceCallbacks?.refreshConnectionList(endpoints)
//                    }
//                    else {
//                        setConnection(endpointId)
//                    }
                }
            }

            override fun onEndpointLost(endpointId: String) {
                Log.d(TAG, "A previously discovered endpoint has gone away.")
                if (endpointIDConnected.contains(endpointId)) {
                    endpointIDConnected.remove(endpointId)

                    val endpointName = endpointIDNameMap[endpointId]
                    endpoints.remove(endpointName)
//                    if (endpoints.get(endpointName)!!.status  != -1) {
//                        //it has initial info, just got disconnected
//                        endpoints.get(endpointName)!!.status  = 0
//                        //initial info is there, so reload the gui
                        serviceCallbacks?.refreshConnectionList(endpoints)
//                    }

                    endpointIDNameMap.remove(endpointId) //so many ids will be generated in every second
                    endpointNameIDMap.remove(endpointName) //don't bother, device is limited
                }
            }
        }

    fun setConnection(endpointId: String) {
        Nearby.getConnectionsClient(context).requestConnection(
            "",
            endpointId,
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

//                        val endpointName =  endpointIDNameMap[endpointId]
//                        if (!endpoints.get(endpointName)!!.infosent) {
//                            //initial info is not there, so need to send own info
//                            sendPayload(endpointId, EndpointInfo.MsgInitInfo)
//                            endpoints.get(endpointName)!!.infosent = true
//                        }
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
                serviceCallbacks?.refreshConnectionList(endpoints)
            }
        }

    private fun sendPayload(endpointId: String, bytes: ByteArray) {
        val payload = Payload.fromBytes(bytes)
        Nearby.getConnectionsClient(applicationContext).sendPayload(endpointId, payload)
    }

    private fun sendPayload(endpointId: String, msgType: Int) {
        when (msgType) {
//            EndpointInfo.MsgInitInfo -> {
//                val username = globalVariable.getUserName()
//                val userattrs = globalVariable.getAttributes()
//                val payload = Payload.fromBytes((EndpointInfo.MsgInitInfo.toString() + "|" +  username!! + "\t" + userattrs!!).toByteArray())
//                Nearby.getConnectionsClient(context).sendPayload(endpointId, payload)
//            }
//            EndpointInfo.MsgDirectory -> {
//                val fileDir = ""
//                val payload = Payload.fromBytes((EndpointInfo.MsgDirectory.toString() + "|" +  fileDir!!).toByteArray())
//                Nearby.getConnectionsClient(context).sendPayload(endpointId, payload)
//            }
//            EndpointInfo.MsgData -> {
//                val filedata = ""
//                //val payload = Payload.fromBytes((EndpointInfo.MsgDirectory.toString() + "|" +  fileDir!!).toByteArray())
//                //Nearby.getConnectionsClient(context).sendPayload(endpointId, payload)
//            }
//            EndpointInfo.MsgReward -> {
//                val reward = ""
//                val payload = Payload.fromBytes((EndpointInfo.MsgReward.toString() + "|" +  reward!!).toByteArray())
//                Nearby.getConnectionsClient(context).sendPayload(endpointId, payload)
//            }
        }

        if (textMsg != null) {
//            val preferences = Preferences(this)
//            val RLStringList = preferences.getRevokedMembers().toList()
//            var RL = listOf<Int>()
//            for(RLStr in RLStringList){
//                RL+= RLStr.toInt()+1
//            }
//            this.publicKey.printPublicKey()
//
//            Log.i("encrypt", "policy: "+ policyMsg)
//            val encryptedFilename = ReVo_ABE.encrypt(this.pairing
//                ,this.publicKey, textMsg!!.toByteArray(), policyMsg, RL)
//            val filenamePayload = Payload.fromBytes(encryptedFilename!!.toByteArray())

            val filenamePayload = Payload.fromBytes(textMsg!!.toByteArray())
            Nearby.getConnectionsClient(context).sendPayload(endpointId, filenamePayload)
            textMsg = null
        }
        if (imageMsg != null) {
            val pfd = contentResolver.openFileDescriptor(imageMsg!!.toUri(), "r")
            val filePayload = Payload.fromFile(pfd!!)
//
//            val preferences = Preferences(this)
//            val RLStringList = preferences.getRevokedMembers().toList()
//            var RL = listOf<Int>()
//            for(RLStr in RLStringList){
//                RL+= RLStr.toInt()+1
//            }
//            this.publicKey.printPublicKey()
//
//            val encryptedFile = ReVo_ABE.encrypt(this.pairing
//                ,this.publicKey,filePayload.asBytes(),policyMsg, RL)
//            Nearby.getConnectionsClient(applicationContext).sendPayload(endpointId, Payload.fromBytes(encryptedFile.toByteArray())) //how to make fromFile()

            Nearby.getConnectionsClient(applicationContext).sendPayload(endpointId, filePayload)
            imageMsg = null
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
//                    rcvdFilename = String(ReVo_ABE.decrypt(pairing,publicKey, Ciphertext(payload.asBytes()!!,pairing),privateKey) )
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
                    copyStream(`in`!!, FileOutputStream(movedFile))
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
}