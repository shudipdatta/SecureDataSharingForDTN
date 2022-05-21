package com.example.securedatasharingfordtn.connection
import android.graphics.Bitmap

class ImageListItem(var image: Bitmap,
                    var similarity: Double,
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