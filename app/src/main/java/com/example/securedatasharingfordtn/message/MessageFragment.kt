package com.example.securedatasharingfordtn.message

import android.app.Application
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import com.example.securedatasharingfordtn.Preferences
import com.example.securedatasharingfordtn.R
import com.example.securedatasharingfordtn.database.DTNDataSharingDatabase
import com.example.securedatasharingfordtn.databinding.MessageFragmentBinding
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


class MessageFragment : Fragment() {

    companion object {
        private const val CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 1
        private const val OWN_IMAGE_FOLDER = "own_images"
        private const val COLLECTED_IMAGE_FOLDER = "collected_images"
        private const val photoName = "photo.jpg" //initial save after photo is taken
    }

    private lateinit var application: Application
    private lateinit var binding: MessageFragmentBinding
    private lateinit var messageViewModel: MessageViewModel
    private lateinit var preferences: Preferences
    private var dialog: ProgressDialog? = null
    private lateinit var captioner: Captioner

    private lateinit var photolist_own: ArrayList<ImageGridItem>
    private lateinit var adapter_own: GridViewAdapter
    private lateinit var DEVICE_ID: String
    private lateinit var photoFile: File
    private lateinit var capturedItem: ImageGridItem

    private lateinit var photolist_collected: ArrayList<ImageGridItem>
    private lateinit var adapter_collected: GridViewAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        application = requireNotNull(this.activity).application
        val dataSource = DTNDataSharingDatabase.getInstance(application).storedUserDao
        preferences = Preferences(requireContext())
        DEVICE_ID = Settings.Secure.getString(application.getContentResolver(), Settings.Secure.ANDROID_ID)

        photolist_own = ArrayList()
        photolist_collected = ArrayList()

        val messageViewModelFactory = MessageViewModelFactory(dataSource, application)
        messageViewModel = ViewModelProvider(requireActivity(), messageViewModelFactory).get(MessageViewModel::class.java)
        //messageViewModel = ViewModelProvider(this,messageViewModelFactory).get(MessageViewModel::class.java)

        binding = DataBindingUtil.inflate(
            inflater, R.layout.message_fragment,container,false
        )
        binding.messageViewModel = messageViewModel
        binding.lifecycleOwner = this

        messageViewModel.fetchStoredImages()
        showAllPhotos()

        //when user click capture photo button
        binding.capturePhotoButton.setOnClickListener {
            onLaunchCamera()
        }

        //initialize captioner and detector
        initImageProcessing()

        //when user click on an image
        clickedImageItem()

        //if user delete any photo
        deleteFromGridView()
        //if user capture any photo
        addToGridView()

        return binding.root
    }

    private fun showAllPhotos() {
        messageViewModel.doneLoad.observe(viewLifecycleOwner, Observer {
            if (it == true) {
                for (image in messageViewModel.imageList) {
                    if (image.isowned) {
                        val imgFile = getPhotoFileUri(image.imageid, OWN_IMAGE_FOLDER)
                        val bitmap = BitmapFactory.decodeFile(imgFile.path)
                        photolist_own.add(ImageGridItem(bitmap!!, imgFile.name, image.isowned, image.path, image.caption, image.keywords, image.from, image.isencrypted, image.policy, image.isrevoked, image.mission))
                    }
                    else {
                        val imgFile = getPhotoFileUri(image.imageid, COLLECTED_IMAGE_FOLDER)
                        val bitmap = BitmapFactory.decodeFile(imgFile.path)
                        photolist_collected.add(ImageGridItem(bitmap!!, imgFile.name, image.isowned, image.path, image.caption, image.keywords, image.from, image.isencrypted, image.policy, image.isrevoked, image.mission))
                    }
                }
                adapter_own = GridViewAdapter(application, R.layout.grid_image_layout, photolist_own)
                binding.gridviewOwn.adapter = adapter_own
                adapter_collected = GridViewAdapter(application, R.layout.grid_image_layout, photolist_collected)
                binding.gridviewCollected.adapter = adapter_collected
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

    private fun onLaunchCamera() {
        // create Intent to take a picture and return control to the calling application
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Create a File reference for future access
        photoFile = getPhotoFileUri(photoName, OWN_IMAGE_FOLDER)

        // wrap File object into a content provider //required for API >= 24
        val fileProvider: Uri = FileProvider.getUriForFile(application, "com.example.securedatasharingfordtn.fileprovider", photoFile)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileProvider)

        if (intent.resolveActivity(application.packageManager) != null) {
            // Start the image capture intent to take photo
            startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE) {
            if (resultCode == AppCompatActivity.RESULT_OK) {

                // by this point we have the camera photo on disk
                val takenImage = BitmapFactory.decodeFile(photoFile.absolutePath)
                val resizedBitmap: Bitmap = BitmapScaler.scaleToFitWidth(takenImage, 1080)

                val bytes = ByteArrayOutputStream()
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 50, bytes)
                val resizedFileName = DEVICE_ID + "_" + (System.currentTimeMillis() / 1000).toString()
                val resizedFile = getPhotoFileUri(resizedFileName, OWN_IMAGE_FOLDER)
                resizedFile.createNewFile()

                val fos = FileOutputStream(resizedFile)
                fos.write(bytes.toByteArray())
                fos.close()

                //update the gridview
                val rotatedImage = getRotatedImage(resizedBitmap, resizedFile.path)
                capturedItem = ImageGridItem(rotatedImage!!, resizedFileName, true, resizedFile.path, "", "", "self", false, "", false,
                    preferences.getMission()!!
                )

                //generate caption
                dialog = ProgressDialog.show(this.activity,"Generating Caption...","please wait..",true)
                val cnnTask = CNNTask(capturedItem)
                cnnTask.execute(resizedFile.path)
            }
            else { // Result was a failure
                Toast.makeText(application, "Picture wasn't taken!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addToGridView() {
        messageViewModel.doneStore.observe(viewLifecycleOwner, Observer {
            if (it == true) {
                photolist_own.add(capturedItem)
                adapter_own.notifyDataSetChanged()
                messageViewModel.doneStoreEvent()
            }
        })
    }

    private fun clickedImageItem() {
        binding.gridviewOwn.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                val item = parent.getItemAtPosition(position) as ImageGridItem
                messageViewModel.setImageItem(item)
                messageViewModel.setImagePosition(position)
                findNavController().navigate(R.id.action_messageFragment_to_deleteMessageFragment)
            }
        binding.gridviewCollected.onItemClickListener =
            AdapterView.OnItemClickListener { parent, view, position, id ->
                val item = parent.getItemAtPosition(position) as ImageGridItem
                messageViewModel.setImageItem(item)
                messageViewModel.setImagePosition(position)
                findNavController().navigate(R.id.action_messageFragment_to_deleteMessageFragment)
            }
    }

    private fun deleteFromGridView() {
        messageViewModel.doneDelete.observe(viewLifecycleOwner, Observer {
            if (it == true) {
                //the following part not needed. because back from deleteFragment reload the images
//                val item = messageViewModel.image.value as ImageGridItem
//                val position = messageViewModel.position.value as Int
//
//                if (item.isowned) {
//                    photolist_own.removeAt(position)
//                    adapter_own.notifyDataSetChanged();
//                } else {
//                    photolist_collected.removeAt(position)
//                    adapter_collected.notifyDataSetChanged();
//                }

                messageViewModel.doneDeleteEvent()
            }
        })
    }

    private fun initImageProcessing() {
        try {
            captioner = Captioner(this.requireActivity().assets)
        } catch (e: java.lang.Exception) {
            println(e.message)
        }
    }

    inner class CNNTask(val capturedItem: ImageGridItem) : AsyncTask<String?, Void?, String>() {
        //private CNNListener listener;
        private var startTime: Long = 0

        override fun onPostExecute(caption: String) {
            Log.i("TAG",String.format("elapsed wall time: %d ms", SystemClock.uptimeMillis() - startTime))
            Log.i("TAG", caption)
            dialog?.dismiss()

            capturedItem.caption = caption
            messageViewModel.storeImage(capturedItem)

            super.onPostExecute(caption)
        }

        override fun doInBackground(vararg params: String?): String {
            startTime = SystemClock.uptimeMillis()
            return captioner.predictImage(params[0]!!)!!
        }
    }
}