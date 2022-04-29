package com.example.securedatasharingfordtn.profile

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.securedatasharingfordtn.Preferences
import com.example.securedatasharingfordtn.R
import com.example.securedatasharingfordtn.database.DTNDataSharingDatabase
import com.example.securedatasharingfordtn.database.LoginUserData
import com.example.securedatasharingfordtn.databinding.FragmentLoginBinding
import com.example.securedatasharingfordtn.databinding.ProfileFragmentBinding
import com.example.securedatasharingfordtn.login.LoginViewModel

class ProfileFragment : Fragment() {

    companion object {
        fun newInstance() = ProfileFragment()
    }

    private lateinit var preferences: Preferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val application = requireNotNull(this.activity).application
        val dataSource = DTNDataSharingDatabase.getInstance(application).loginUserDao

        val profileViewModelFactory = ProfileViewModelFactory(dataSource, application)
        val profileViewModel = ViewModelProvider(this,profileViewModelFactory).get(ProfileViewModel::class.java)

        val binding: ProfileFragmentBinding = DataBindingUtil.inflate(
            inflater, R.layout.profile_fragment,container,false
        )
        binding.profileViewModel = profileViewModel
        binding.lifecycleOwner = this
        preferences = Preferences(requireContext())

        loadUserData(binding, profileViewModel)

        profileViewModel.fetchUserData(preferences.getUserId())

        return binding.root
    }

    private fun loadUserData(binding: ProfileFragmentBinding, profileViewModel: ProfileViewModel){
        profileViewModel.doneLoad.observe(viewLifecycleOwner, Observer {
            if (it == true) {
                Log.i("User Info", "found user in the database")
                binding.textView14.setText(profileViewModel.user.username)
                binding.textView11.setText(profileViewModel.user.firstname + " " + profileViewModel.user.lastname)
                binding.textView16.setText(profileViewModel.user.attributes)
                binding.textView18.setText(profileViewModel.user.interests)
                binding.textView22.setText(profileViewModel.user.mission.toString())
            }
        })
    }
}