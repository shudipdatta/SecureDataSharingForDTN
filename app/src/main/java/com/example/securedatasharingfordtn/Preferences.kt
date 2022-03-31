package com.example.securedatasharingfordtn

import android.content.Context
import android.content.SharedPreferences
import com.example.securedatasharingfordtn.database.LoginUserData

private const val MEMBERS = "members"
private const val REVOKED = "revoked"
private const val POLICY = "policy"
private const val USERNAME = "username" //test
private const val USERATTRS = "userattrs" //test

class Preferences(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("data", Context.MODE_PRIVATE)

    fun getRevokedMembers(): MutableSet<String> =  HashSet<String>(preferences.getStringSet(REVOKED, HashSet<String>()))
    fun setRevokedMembers(revoked:Set<String>){
        preferences.edit().putStringSet(REVOKED,revoked).apply()
    }
    fun getMembers(): String? = preferences.getString(MEMBERS,"")
    fun setMembers(members: String){
        preferences.edit().putString(MEMBERS,members).apply()
    }

    fun getPolicy(): String? = preferences.getString(POLICY,"")
    fun setPolicy(policy: String){
        preferences.edit().putString(POLICY,policy).apply()
    }

    //test
    fun getUserName(): String? = preferences.getString(USERNAME,"")
    fun setUserName(username: String){
        preferences.edit().putString(USERNAME,username).apply()
    }

    //test
    fun getUserAttrs(): String? = preferences.getString(USERATTRS,"")
    fun setUserAttrs(userattrs: String){
        preferences.edit().putString(USERATTRS,userattrs).apply()
    }

}