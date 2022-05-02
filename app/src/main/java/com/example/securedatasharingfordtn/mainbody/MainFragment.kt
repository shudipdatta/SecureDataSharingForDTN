package com.example.securedatasharingfordtn.mainbody

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.securedatasharingfordtn.R
import com.example.securedatasharingfordtn.SharedViewModel
import com.example.securedatasharingfordtn.connection.ConnectionActivity
import com.example.securedatasharingfordtn.database.DTNDataSharingDatabase
import com.example.securedatasharingfordtn.databinding.FragmentMainBinding
import com.example.securedatasharingfordtn.message.MessageActivity
import com.example.securedatasharingfordtn.profile.ProfileActivity

class MainFragment : Fragment(){

    private lateinit var binding: FragmentMainBinding
    private lateinit var sharedModel: SharedViewModel
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        binding = DataBindingUtil.inflate(
            inflater, R.layout.fragment_main,container,false
        )
        val application = requireNotNull(this.activity).application

        val dataSource = DTNDataSharingDatabase.getInstance(application).loginUserDao

        val viewModelFactory = MainViewModelFactory(dataSource, application)
        sharedModel=ViewModelProvider(requireActivity()).get(SharedViewModel::class.java)
        val mainViewModel = ViewModelProvider(
            this,viewModelFactory).get(MainViewModel::class.java)
        binding.mainViewModel = mainViewModel
        binding.lifecycleOwner = this

        startManageConnectionActivity(mainViewModel)
        startManageMembersActivity(mainViewModel)
        startManageProfileActivity(mainViewModel)
        startManageMessageActivity(mainViewModel)

        return binding.root
    }

    private fun startManageConnectionActivity(mainViewModel: MainViewModel){
        mainViewModel.manageConnection.observe(viewLifecycleOwner, Observer {
            if (it == true) { // Observed state is true.
                Log.i("mainbody","to manage connection")
                mainViewModel.doneSetupConnectionEvent()
                val intent = Intent(requireContext(), ConnectionActivity::class.java)
                intent.putExtra("keys", sharedModel.getKeys());
                intent.putExtra("pairingDir", sharedModel.getPairDir());
                startActivity(intent)
            }
        })
    }

    private fun startManageMembersActivity(mainViewModel: MainViewModel){
        mainViewModel.manageMembers.observe(viewLifecycleOwner) {
            if (it == true) {
                Log.i("mainbody", "To manage members.")
                mainViewModel.doneSetupRevocationEvent()
                val intent = Intent(requireContext(), MembersActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun startManageProfileActivity(mainViewModel: MainViewModel){
        mainViewModel.manageProfile.observe(viewLifecycleOwner) {
            if (it == true) {
                Log.i("mainbody", "To manage profile.")
                mainViewModel.doneSetupProfileEvent()
                val intent = Intent(requireContext(), ProfileActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun startManageMessageActivity(mainViewModel: MainViewModel){
        mainViewModel.manageMessage.observe(viewLifecycleOwner) {
            if (it == true) {
                Log.i("mainbody", "To manage message.")
                mainViewModel.doneSetupMessageEvent()
                val intent = Intent(requireContext(), MessageActivity::class.java)
                startActivity(intent)
            }
        }
    }
}
