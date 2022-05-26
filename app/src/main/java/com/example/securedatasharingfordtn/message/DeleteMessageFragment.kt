package com.example.securedatasharingfordtn.message

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.securedatasharingfordtn.R
import com.example.securedatasharingfordtn.database.DTNDataSharingDatabase
import com.example.securedatasharingfordtn.databinding.FragmentDeleteMessageBinding
import java.io.File

class DeleteMessageFragment : Fragment() {
    private lateinit var application: Application
    private lateinit var binding: FragmentDeleteMessageBinding
    private lateinit var messageViewModel: MessageViewModel

    companion object {
        private const val OWN_IMAGE_FOLDER = "own_images"
        private const val COLLECTED_IMAGE_FOLDER = "collected_images"
        private const val ENCRYPTED_PREFIX = "encrypted"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        application = requireNotNull(this.activity).application
        val dataSource = DTNDataSharingDatabase.getInstance(application).storedUserDao

        val messageViewModelFactory = MessageViewModelFactory(dataSource, application)
        messageViewModel = ViewModelProvider(requireActivity(), messageViewModelFactory).get(MessageViewModel::class.java)

        binding = DataBindingUtil.inflate(
            inflater, R.layout.fragment_delete_message,container,false
        )

        binding.messageViewModel = messageViewModel
        binding.lifecycleOwner = this

        //
        val item = messageViewModel.image.value as ImageGridItem

        binding.selectedImage.setImageBitmap(item.image)
        binding.selectedTitle.setText(item.imageid)

        //binding.selectedCaption.setText(item.caption)
        //test block
        val caparr = item.caption.split(' ')
        val halfsize = caparr.size / 2
        var result = ""
        for (i in 0 until halfsize) {
            result += (caparr[i] + " ")
        }
        result += "\n"
        for (i in halfsize until caparr.size) {
            result += (caparr[i] + " ")
        }
        binding.selectedCaption.setText(result)

        binding.selectedKeywords.setText(item.keywords)
        binding.selectedFrom.setText(item.from)
        binding.selectedPolicy.setText(item.policy)

        binding.deleteImageButton.setOnClickListener {
            val folder = if(item.isowned) OWN_IMAGE_FOLDER else COLLECTED_IMAGE_FOLDER
            val imageFile = getPhotoFileUri(item.imageid, folder)
            if (imageFile.exists()) {
                imageFile.delete()
            }
            if (item.isencrypted) {
                val encryptedFile = getPhotoFileUri(ENCRYPTED_PREFIX + item.imageid, folder)
                if (encryptedFile.exists()) {
                    encryptedFile.delete()
                }
            }

            messageViewModel.deleteImage(item.imageid)
            findNavController().popBackStack()
        }
        return binding.root
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