package com.example.securedatasharingfordtn.database


import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface OtherUserDao {
    @Insert
    fun insert(user: OtherUserData)

    @Update
    fun update(user: OtherUserData)

//    @Query("SELECT endpoint_id from other_user_data_table WHERE endpoint_id = :endpointid LIMIT 1")
//    fun ifVisited(endpointid: String): OtherUserData?

    @Delete
    fun clear(otheruserdata: OtherUserData)
}