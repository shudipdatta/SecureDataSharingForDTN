package com.example.securedatasharingfordtn.connection

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ListView
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.securedatasharingfordtn.R
import com.example.securedatasharingfordtn.database.DTNDataSharingDatabase
import com.example.securedatasharingfordtn.databinding.FragmentConImageBinding
import java.io.File

class ConImageFragment : Fragment() {
    companion object {
        private const val OWN_IMAGE_FOLDER = "own_images"
        private const val COLLECTED_IMAGE_FOLDER = "collected_images"
    }

    private lateinit var application: Application
    private lateinit var binding: FragmentConImageBinding
    private lateinit var connectionViewModel: ConnectionViewModel

    private lateinit var photolist: ArrayList<ImageListItem>
    private lateinit var adapter: ListViewAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        application = requireNotNull(this.activity).application
        val dataSource = DTNDataSharingDatabase.getInstance(application).storedUserDao

        val connectionViewModelFactory = ConnectionViewModelFactory(dataSource, application)
        connectionViewModel = ViewModelProvider(requireActivity(), connectionViewModelFactory).get(
            ConnectionViewModel::class.java
        )

        binding = DataBindingUtil.inflate(
            inflater, R.layout.fragment_con_image, container, false
        )
        binding.connectionViewModel = connectionViewModel
        binding.lifecycleOwner = this

        connectionViewModel.fetchStoredImages()
        showImages()
        clickedImageItem()

        return binding.root
    }

    private fun showImages() {
        photolist = ArrayList()
        adapter = ListViewAdapter(application, R.layout.list_image_layout, photolist)
        binding.imageList.adapter = adapter

        connectionViewModel.doneLoad.observe(viewLifecycleOwner, Observer {
            if (it == true) {
                var conName = connectionViewModel.conName.value //selected connection
                for (image in connectionViewModel.imageList) {
                    val imgFile =
                        if (image.isowned) getPhotoFileUri(image.imageid, OWN_IMAGE_FOLDER)
                        else getPhotoFileUri(image.imageid, COLLECTED_IMAGE_FOLDER)
                    val bitmap = BitmapFactory.decodeFile(imgFile.path)
                    photolist.add(ImageListItem(bitmap!!, imgFile.name, image.caption, 0.0, image.isowned))
                }
                adapter.notifyDataSetChanged();
            }
        })
    }

    private fun clickedImageItem() {
        binding.imageList.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                val item = parent.getItemAtPosition(position)
                val image = item as ImageListItem
                connectionViewModel.setImgTitle(image.title)
                val folder =
                    if (image.isowned) OWN_IMAGE_FOLDER
                    else COLLECTED_IMAGE_FOLDER
                connectionViewModel.setImgFile(getPhotoFileUri(image.title, folder))
                connectionViewModel.isSelectedImage(true)
                findNavController().popBackStack()
            }
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