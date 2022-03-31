package com.example.securedatasharingfordtn.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "other_user_data_table")
data class OtherUserData(
    @ColumnInfo(name = "endpoint_id")
    @PrimaryKey(autoGenerate = false)
    var endpointid: String = "",

    @ColumnInfo(name = "first_name")
    var firstname: String="",

    @ColumnInfo(name = "last_name")
    var lastname: String="",

    @ColumnInfo(name = "q_value")
    var qvalue: Double=0.0,

    @ColumnInfo(name = "visit_count")
    var visitcnt: Int=0,

    @ColumnInfo(name = "record_count")
    var recordcnt: Int=0,

    @ColumnInfo(name = "state")
    var state: Int=0,
)
