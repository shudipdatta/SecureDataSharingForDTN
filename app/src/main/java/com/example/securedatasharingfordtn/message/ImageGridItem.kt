package com.example.securedatasharingfordtn.message
import android.graphics.Bitmap

class ImageGridItem(
    var image: Bitmap,
    var imageid: String,
    var isowned: Boolean,
    var path: String,
    var caption: String,
    var keywords: String,
    var from: String,
    var isencrypted: Boolean,
    var policy: String,
    var isrevoked: Boolean,
    var mission: String)
