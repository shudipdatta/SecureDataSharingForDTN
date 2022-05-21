package com.example.securedatasharingfordtn.message

import android.content.res.AssetManager
import android.os.Build
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class Vocabulary (val assets: AssetManager) {

    private var id2word: List<String>

    init {
        id2word = loadWords()
    }

    private fun getLabelPath(): String? {
        return "word_counts.txt"
    }

    @Throws(IOException::class)
    fun loadWords(): List<String> {
        val labelList: MutableList<String> = ArrayList()
        val reader = BufferedReader(InputStreamReader(assets.open(getLabelPath()!!)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            reader.lines().forEach {
                Log.i("test", it)
                val parts = it.split(" ".toRegex()).toTypedArray()
                labelList.add(parts[0])
            }
        }
        reader.close()
        return labelList
    }

    fun getStartIndex(): Int {
        return 1
    }

    fun getEndIndex(): Int {
        return 2
    }

    fun getWordAtIndex(index: Int): String? {
        return id2word!![index]
    }

}