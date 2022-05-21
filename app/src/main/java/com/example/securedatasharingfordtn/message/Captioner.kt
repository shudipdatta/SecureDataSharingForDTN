package com.example.securedatasharingfordtn.message

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.util.*


class Captioner (val assets: AssetManager) {
    private val TAG = "Captioner"
    private val tfliteOptions: Interpreter.Options = Interpreter.Options()
    private val tfliteOptions_lstm: Interpreter.Options = Interpreter.Options()

    // The loaded TensorFlow Lite model.
    private var tfliteModel: MappedByteBuffer? = null
    private var tfliteModel_lstm: MappedByteBuffer? = null

    // An instance of the driver class to run model inference with Tensorflow Lite.
    protected var tflite: Interpreter? = null
    protected var tflite_lstm: Interpreter? = null

    // A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs.
    protected var imgData: ByteBuffer? = null

    private var image_feed: Array<Array<FloatArray>>? = null
    private var input_feed: LongArray? = null
    private var state_feed: Array<FloatArray>? = null

    private var softmax: Array<FloatArray>? = null
    private var lstm_state: Array<FloatArray>? = null
    private var initial_state: Array<FloatArray>? = null

    var vocabulary: Vocabulary? = null

    init {
        val inceptionModelPath = getInceptionModelPath()
        val lstmModelPath = getLSTMModelPath()
        tfliteModel = loadModelFile(inceptionModelPath!!)
        tflite = Interpreter(tfliteModel!!, tfliteOptions)
        tfliteModel_lstm = loadModelFile(lstmModelPath!!)
        tflite_lstm = Interpreter(tfliteModel_lstm!!, tfliteOptions_lstm)

        Log.d(TAG, "Created a Tensorflow Lite Image Classifier.")
        image_feed = Array(346) {Array(346) {FloatArray(3)}}
        input_feed = LongArray(1)
        state_feed = Array(1) { FloatArray(1024) }
        softmax = Array(1) { FloatArray(12000) }
        lstm_state = Array(1) { FloatArray(1024) }
        initial_state = Array(1) { FloatArray(1024) }
        vocabulary = Vocabulary(assets)
    }


    fun classifyFrame(): String? {
        if (tflite == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.")
        }
        val startTime = SystemClock.uptimeMillis()
        val caption: String = runInference()
        val endTime = SystemClock.uptimeMillis()
        val duration = endTime - startTime
        Log.d(TAG, "Time to run model inference: $duration")
        return caption
    }

    @Throws(IOException::class)
    private fun loadModelFile(modelFileName: String): MappedByteBuffer? {
        val fileDescriptor = assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun getInceptionModelPath(): String? {
        return "inceptionv3_1.tflite"
    }

    private fun getLSTMModelPath(): String? {
        return "lstm_2.tflite"
    }

    private fun runInference(): String {
        var outputs_cnn: MutableMap<Int, Any> = TreeMap()
        var outputs_lstm: MutableMap<Int, Any> = TreeMap()
        var inputs_cnn =  arrayOfNulls<Any>(1) //Array(1){Array(346) {Array(346) {FloatArray(3)}}}
        var inputs_lstm = arrayOfNulls<Any>(2) //Array(2) {LongArray, Array<FloatArray>}

        inputs_cnn[0] = image_feed!!
        inputs_lstm[0] = input_feed
        inputs_lstm[1] = state_feed
        Log.d(TAG, "inputs complete")
        outputs_lstm[tflite_lstm!!.getOutputIndex("import/softmax")] = softmax!!
        outputs_lstm[tflite_lstm!!.getOutputIndex("import/lstm/state")] = lstm_state!!
        outputs_cnn[tflite!!.getOutputIndex("import/lstm/initial_state")] = initial_state!!
        Log.d(TAG, "outputs complete")
        tflite!!.runForMultipleInputsOutputs(inputs_cnn as Array<Any>, outputs_cnn)

        val maxCaptionLength = 20
        val words: MutableList<Int> = ArrayList()
        // TODO - replace with vocab.start_id
        words.add(vocabulary!!.getStartIndex())
        for (i in 0 until initial_state!![0].size) {
            state_feed!![0][i] = initial_state!![0][i]
        }
        input_feed!![0] = words[words.size - 1].toLong()
        var maxIdx: Int
        for (i in 0 until maxCaptionLength) {
            tflite_lstm!!.runForMultipleInputsOutputs(inputs_lstm as Array<Any>, outputs_lstm)
            maxIdx = findMaximumIdx(softmax!![0])
            words.add(maxIdx)
            // TODO - replace with vocab.end_id
            if (maxIdx == vocabulary!!.getEndIndex()) {
                break
            }
            input_feed!![0] = maxIdx.toLong()
            for (j in 0 until state_feed!![0].size) {
                state_feed!![0][j] = lstm_state!![0][j]
            }
            Log.d(TAG, "current word: $maxIdx")
            //state_feed[0] = ;
        }
        Log.d(TAG, "run inference complete")
        //Log.d(TAG, "result= " + Arrays.deepToString(initial_state));
        val sb = StringBuilder()
        for (i in 1 until words.size - 1) {
            sb.append(vocabulary!!.getWordAtIndex(words[i]))
            sb.append(" ")
        }
        return sb.toString()
    }


    private fun findMaximumIdx(arr: FloatArray): Int {
        val n = arr.size
        var maxval = arr[0]
        var maxidx = 0
        for (i in 1 until n) {
            if (maxval < arr[i]) {
                maxval = arr[i]
                maxidx = i
            }
        }
        return maxidx
    }

    fun predictImage(imgPath: String): String? {
        Log.d(TAG, "predictImage: imgPath=$imgPath")
        val origImg = BitmapFactory.decodeFile(imgPath)
        val R = 346
        val C = 346
        val img = Bitmap.createScaledBitmap(origImg, C, R, false)
        var pixelValue: Int
        for (i in 0 until R) {
            for (j in 0 until C) {
                pixelValue = img.getPixel(j, i)
                image_feed!![i][j][0] = (pixelValue shr 16 and 0xFF).toFloat() / 256.0f
                image_feed!![i][j][1] = (pixelValue shr 8 and 0xFF).toFloat() / 256.0f
                image_feed!![i][j][2] = (pixelValue and 0xFF).toFloat() / 256.0f
            }
        }
        return classifyFrame()
    }

    private fun stringToBytes(s: String): ByteArray? {
        return s.toByteArray(StandardCharsets.US_ASCII)
    }


    // Closes tflite to release resources.
    fun close() {
        tflite!!.close()
        tflite = null
        tfliteModel = null
        tflite_lstm!!.close()
        tflite_lstm = null
        tfliteModel_lstm = null
    }

}