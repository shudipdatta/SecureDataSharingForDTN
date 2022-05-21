package com.example.securedatasharingfordtn.database


import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LoginUserData::class, OtherUserData::class, StoredImageData::class], version = 6, exportSchema = false)
abstract class DTNDataSharingDatabase : RoomDatabase() {
    abstract val loginUserDao: LoginUserDao
    abstract val otherUserDao: OtherUserDao
    abstract val storedUserDao: StoredImageDao

    companion object {
        @Volatile
        private var INSTANCE: DTNDataSharingDatabase? = null

        fun getInstance(context: Context) : DTNDataSharingDatabase {
            synchronized(this){
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        DTNDataSharingDatabase::class.java,
                        "dtn_data_sharing_database"
                    )
                        .fallbackToDestructiveMigration()
                        .build()

                }
                return instance
            }

        }
    }


}