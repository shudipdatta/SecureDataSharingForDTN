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

        val image = getPhotoFileUri(messageViewModel.title.value!!, messageViewModel.folder.value!!)
        val bitmap = BitmapFactory.decodeFile(image.path)
        val rotatedImage = getRotatedImage(bitmap, image.path)

        binding.selectedTitle.setText(image.name)
        binding.selectedImage.setImageBitmap(rotatedImage)

        binding.deleteImageButton.setOnClickListener {
            if (image.exists()) {
                image.delete()
            }
            messageViewModel.deleteImage(image.name)
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

    private fun getRotatedImage(image: Bitmap, path: String): Bitmap? {

//        val ei = ExifInterface(path)
//        val orientation: Int = ei.getAttributeInt(
//            ExifInterface.TAG_ORIENTATION,
//            ExifInterface.ORIENTATION_UNDEFINED
//        )
//
//        return when (orientation) {
//            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(image, 90)
//            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(image, 180)
//            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(image, 270)
//            ExifInterface.ORIENTATION_NORMAL -> image
//            else -> image
//        }
        return rotateImage(image, 0)
    }

    private fun rotateImage(image: Bitmap, angle: Int): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(angle.toFloat())
        return Bitmap.createBitmap(
            image, 0, 0, image.width, image.height,
            matrix, true
        )
    }

}