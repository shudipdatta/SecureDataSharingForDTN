package com.example.securedatasharingfordtn.connection
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.securedatasharingfordtn.GlobalApp
import com.example.securedatasharingfordtn.Preferences
import com.example.securedatasharingfordtn.R
import com.example.securedatasharingfordtn.congestion.EndpointInfo
import com.example.securedatasharingfordtn.revoabe.PrivateKey
import com.example.securedatasharingfordtn.revoabe.PublicKey
import it.unisa.dia.gas.jpbc.Pairing
import it.unisa.dia.gas.plaf.jpbc.pairing.PairingFactory
import it.unisa.dia.gas.plaf.jpbc.util.Arrays
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ConnectionActivity_v1 : AppCompatActivity(), ConnectionService.ServiceCallbacks {

    companion object {
        private const val SEND_IMAGE_ACTIVITY_REQUEST_CODE = 1
    }

    private lateinit var conService: ConnectionService
    private var conServiceBound: Boolean = false

    //lateinit var listElementsArrayList: List<String>
    private lateinit var selectedEndPointName: String

    lateinit var adapter: ArrayAdapter<String>
    lateinit var listview: ListView
    var listElementsArrayList: ArrayList<String> = ArrayList()
    lateinit var preferences:Preferences
    lateinit var privateKey: PrivateKey
    lateinit var publicKey: PublicKey
    lateinit var pairing: Pairing
    lateinit var policyText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_connection_v1)
        preferences = Preferences(this)
        val bundle = intent.extras
        val keys = bundle!!.getByteArray("keys")
        val pairingDir = bundle.getString("pairingDir")
        if (keys !=null && pairingDir != null)
            bootstrap(pairingDir,keys)

        //test
        //val username = bundle.getString("username")
        //val userattrs = bundle.getString("userattrs")
        val globalVariable: GlobalApp = applicationContext as GlobalApp
        val username = globalVariable.getUserName()
        val userattrs = globalVariable.getAttributes()
        //Log.d("Name + Attrs",username!! + "|" + userattrs!!)

        //Switch to Main activity
//        val backConActButton: Button = findViewById(R.id.back_connection_activity);
//        backConActButton.setOnClickListener {
//            backConAct()
//        }
        policyText = findViewById(R.id.editTextPolicy)
        policyText.setText(preferences.getPolicy())
        //declare the connected devices listview
        listview = findViewById(R.id.connection_list)
        adapter = ArrayAdapter(this,android.R.layout.simple_expandable_list_item_1,listElementsArrayList)
        listview.adapter = adapter
        listview.onItemClickListener = object : AdapterView.OnItemClickListener {
            override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                preferences.setPolicy(policyText.text.toString())
                switchImgAct(position)
            }
        }

        //test: see if you can retrieve login information here by userid


    }
    private fun bootstrap(dirForPairingFile : String, keys: ByteArray){

        this.pairing = PairingFactory.getPairing(dirForPairingFile)
        val publickeySize = ByteBuffer.wrap(keys,0,4).order(ByteOrder.nativeOrder()).getInt()
        val privatekeySize = ByteBuffer.wrap(keys,publickeySize+4,4).order(ByteOrder.nativeOrder()).getInt()
        this.publicKey = PublicKey(Arrays.copyOfRange(keys,4,publickeySize+4),this.pairing)
        this.privateKey = PrivateKey(Arrays.copyOfRange(keys,8+publickeySize,8+publickeySize+privatekeySize),this.pairing)
    }
    /** Called when the user taps the back button  */
    private fun backConAct() {
        finish()
    }

    // select a receiver to see image list
    private fun switchImgAct(position: Int) {
        selectedEndPointName = listview.getItemAtPosition(position) as String
        selectedEndPointName = selectedEndPointName.split("\n")[0].split(" ").last()

        val img_act_intent = Intent(this, ImageActivity::class.java)
        img_act_intent.putExtra("parent", "ConnectionActivity")
        img_act_intent.putExtra("recipient", selectedEndPointName)

        startActivityForResult(img_act_intent, SEND_IMAGE_ACTIVITY_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SEND_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                val fileName = data!!.getStringExtra("fileName")!!
                val folder = data!!.getStringExtra("folder")!!
                val photoFile = getPhotoFileUri(fileName, folder)
                sendImageFile(fileName, photoFile)
            }
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

    override fun refreshConnectionList(endpoints: HashMap<String, EndpointInfo>) {
        val endpointNameConnected = ArrayList<String>()
        for ((name, endpoint) in endpoints) {
//            if (endpoint.status > 0) {
                endpointNameConnected.add("Device ID: " + endpoint.name + "\nName: " + endpoint.username + "\nAttributes: " + endpoint.userattrs) // + "\n" + EndpointInfo.StatusString[endpoint.status])
//            }
        }

        listElementsArrayList.clear()
        listElementsArrayList.addAll(endpointNameConnected)
        adapter.notifyDataSetChanged();
    }

    private fun sendImageFile(fileName: String, photoFile: File) {
        Toast.makeText(applicationContext, "Sending to $selectedEndPointName", Toast.LENGTH_LONG).show()
        conService.textMsg = fileName
        conService.imageMsg = photoFile
        var policyText : EditText  = findViewById(R.id.editTextPolicy)
        conService.policyMsg = policyText.text.toString()

        var selectedEndpointId = conService.endpointNameIDMap.get(selectedEndPointName)
        conService.setConnection(selectedEndpointId!!)
    }

    override fun onStart() {
        super.onStart()
        // Bind to LocalService
        Intent(this, ConnectionService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        if (conServiceBound) {
            conService.setCallbacks(null) //unregister
            unbindService(connection)
            conServiceBound = false;
        }
    }


    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as ConnectionService.ConServiceBinder
            conService = binder.getService()
            conServiceBound = true
            conService.setCallbacks(this@ConnectionActivity_v1); // register

            //initial list load
            refreshConnectionList(conService.endpoints)
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            conServiceBound = false
        }
    }
}