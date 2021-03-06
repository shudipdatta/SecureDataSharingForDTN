package com.example.securedatasharingfordtn.connection

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.securedatasharingfordtn.Preferences
import com.example.securedatasharingfordtn.R
import com.example.securedatasharingfordtn.congestion.EndpointInfo
import com.example.securedatasharingfordtn.database.DTNDataSharingDatabase
import com.example.securedatasharingfordtn.database.StoredImageDao
import com.example.securedatasharingfordtn.databinding.ConnectionFragmentBinding
import java.io.File


class ConnectionFragment : Fragment() {

    private lateinit var dataSource:  StoredImageDao
    private lateinit var application: Application
    private lateinit var binding: ConnectionFragmentBinding
    private lateinit var connectionViewModel: ConnectionViewModel
    private lateinit var preferences: Preferences
    private lateinit var DEVICE_ID: String

    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var listElementsArrayList: ArrayList<String>
    private lateinit var policyText: EditText
    private lateinit var keys: ByteArray
    private lateinit var pairingDir: String

    private lateinit var conServiceIntent: Intent
    private lateinit var conService: NearbyService
    private var conServiceBound: Boolean = false
    private var isServiceRunning: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        application = requireNotNull(this.activity).application
        dataSource = DTNDataSharingDatabase.getInstance(application).storedUserDao
        DEVICE_ID = Settings.Secure.getString(application.getContentResolver(), Settings.Secure.ANDROID_ID)
        preferences = Preferences(requireContext())

        val connectionViewModelFactory = ConnectionViewModelFactory(dataSource, application)
        connectionViewModel = ViewModelProvider(requireActivity(), connectionViewModelFactory).get(
            ConnectionViewModel::class.java)

        binding = DataBindingUtil.inflate(
            inflater, R.layout.connection_fragment,container,false
        )
        binding.connectionViewModel = connectionViewModel
        binding.lifecycleOwner = this

        showConnectionList()
        clickedConnectionItem()

        //get pairing info
        val bundle = activity?.intent?.extras
        pairingDir = bundle!!.getString("pairingDir").toString()
        keys = bundle.getByteArray("keys")!!

        //for connection switch
        isServiceRunning = isNearbyServiceRunning(NearbyService::class.java)
        binding.connectionOnOff.setOnCheckedChangeListener { _, isChecked -> switchConService(isChecked)}
        if (isServiceRunning) {
            binding.connectionOnOff.isChecked = true
        }

        //if conService not null, initialize functions will not be called. so call here
        // if back from delete fragment, con service is initialized, otherwise, not initialized
        if (this::conService.isInitialized) {
            refreshConnectionList()
            populateConnectionList()
            sendMessage()
        }

        return binding.root
    }

    private fun isNearbyServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = activity?.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
        for (service in manager!!.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun showConnectionList() {
        listElementsArrayList = ArrayList()
        adapter = ArrayAdapter(application, android.R.layout.simple_list_item_1, listElementsArrayList)
        binding.connectionList.adapter = adapter
    }

    private fun clickedConnectionItem() {
        binding.connectionList.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                //preferences.setPolicy(policyText.text.toString())
                var conName = binding.connectionList.getItemAtPosition(position) as String
                connectionViewModel.setConName(conName)
                connectionViewModel.setPolicy(binding.editTextPolicy.text.toString())
                findNavController().navigate(R.id.action_connectionFragment_to_conImageFragment)
            }
    }

    fun refreshConnectionList() {
        conService.updateEndpoint.observe(viewLifecycleOwner, Observer {
            if (it == true) {
                populateConnectionList()
                conService.doneUpdateEndpoint()
            }
        })
    }

    fun populateConnectionList() {
        listElementsArrayList.clear()
        val endpointNameConnected = ArrayList<String>()
        for ((name, endpoint) in conService.endpoints) {
            endpointNameConnected.add("Device ID: " + endpoint.name + "\nName: " + endpoint.username
                    + "\nAttributes: " + endpoint.userattrs+ "\nInterests: " + endpoint.userinterests) // + "\n" + EndpointInfo.StatusString[endpoint.status])
        }

        listElementsArrayList.addAll(endpointNameConnected)
        adapter.notifyDataSetChanged();
    }

    private fun switchConService(isChecked: Boolean) {
        if (isChecked) {
            Toast.makeText(activity, "Nearby Devices Searching", Toast.LENGTH_SHORT).show()
            //start service
            conServiceIntent = Intent(activity, NearbyService::class.java)
            activity?.startService(conServiceIntent)
            //bind service
            Intent(activity, NearbyService::class.java).also { intent ->
                activity?.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }
        else {
            Toast.makeText(activity, "Closing Nearby Connection", Toast.LENGTH_SHORT).show()
            activity?.unbindService(connection)
            activity?.stopService(conServiceIntent)
            //clear the connectionlist on disconnect
            listElementsArrayList.clear()
            adapter.notifyDataSetChanged();
        }
    }

    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as NearbyService.ConServiceBinder
            conService = binder.getService()
            conServiceBound = true
            //initial list load
            refreshConnectionList()
            populateConnectionList()
            //initial info send
            sendServiceInitInfo()
            //send message when selected
            sendMessage()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            conServiceBound = false
        }
    }

    private fun sendServiceInitInfo() {
        val endpoint = EndpointInfo()
        endpoint.name = DEVICE_ID
        endpoint.username = preferences.getUserName()!!
        endpoint.userattrs = preferences.getUserAttrs()!!
        endpoint.userinterests = preferences.getUserInterest()!!
        conService.serviceInitInfo(endpoint, dataSource, pairingDir, keys, preferences)
    }

    private fun sendMessage() {
        connectionViewModel.isSelected.observe(viewLifecycleOwner, Observer {
            if (it == true) {
                val item = connectionViewModel.image.value as ImageListItem
                val policyMsg = binding.editTextPolicy.text.toString()

                val conName = connectionViewModel.conName.value.toString().split("\n")[0].split(" ").last()
                val userName = connectionViewModel.conName.value.toString().split("\n")[1].split(" ").last()
                val memberList = preferences.getMembers()!!.split(";")
                var userIndexRL = -1;
                for (member in memberList) {
                    userIndexRL += 1
                    if(member.startsWith(userName)) {
                        break
                    }
                }
                conService.setImageInfo(item, policyMsg, userIndexRL)
                conService.setConnection(conName)

                connectionViewModel.isSelectedImage(false)
                Toast.makeText(activity, "Sending File: ${item.imageid}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Returns the File for a photo stored on disk given the fileName
    private fun getPhotoFileUri(fileName: String, folder: String): File {
        // Use `getExternalFilesDir` on Context to access package-specific directories. // This way, we don't need to request external read/write runtime permissions.
        val mediaStorageDir = File(application.getExternalFilesDir(Environment.DIRECTORY_PICTURES), folder)

        if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
            Log.d("MessageViewModel", "failed to create directory")
        }

        return File(mediaStorageDir.getPath() + File.separator.toString() + fileName)
    }
}