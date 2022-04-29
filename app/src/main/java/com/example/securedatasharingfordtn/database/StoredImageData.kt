package com.example.securedatasharingfordtn.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stored_image_data_table")
data class StoredImageData(
    @ColumnInfo(name = "image_id")
    @PrimaryKey(autoGenerate = false)
    var imageid: String = "",

    @ColumnInfo(name = "is_owned")
    var isowned: Boolean=false,

    @ColumnInfo(name = "path")
    var path: String="",

    @ColumnInfo(name = "caption")
    var caption: String="",

    @ColumnInfo(name = "keywords")
    var keywords: String="",
)
