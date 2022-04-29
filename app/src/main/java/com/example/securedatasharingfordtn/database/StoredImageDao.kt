package com.example.securedatasharingfordtn.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update


@Dao
interface StoredImageDao {
    @Insert
    fun insert(image1: StoredImageData)

    @Update
    fun update(image1: StoredImageData)

    @Query("SELECT * from stored_image_data_table")
    fun getAllImages() : List<StoredImageData>?

    @Query("SELECT * from stored_image_data_table where is_owned = 'true'")
    fun getOwnImages() : List<StoredImageData>?

    @Query("SELECT * from stored_image_data_table where is_owned = 'false'")
    fun getCollectedImages() : List<StoredImageData>?

    @Query("SELECT * from stored_image_data_table WHERE image_id = :imageid LIMIT 1" )
    fun getImageById(imageid: String) : StoredImageData?

    @Query("Delete from stored_image_data_table WHERE image_id = :imageid" )
    fun deleteImageById(imageid: String)

    @Query("DELETE FROM stored_image_data_table")
    fun clear()



}