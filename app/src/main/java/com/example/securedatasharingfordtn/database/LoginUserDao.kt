package com.example.securedatasharingfordtn.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update


@Dao
interface LoginUserDao {
    @Insert
     fun insert(user: LoginUserData) : Long?

    @Update
     fun update(user: LoginUserData)

    @Query("SELECT * from login_user_data_table LIMIT 1")
     fun getLoginData() : LoginUserData?

    @Query("SELECT * from login_user_data_table WHERE user_name = :username AND user_password = :password LIMIT 1" )
     fun tryLogin(username: String, password: String) : LoginUserData?

    @Query("DELETE FROM login_user_data_table")
     fun clear()



}