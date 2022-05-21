package com.example.securedatasharingfordtn

import android.content.Context
import android.content.SharedPreferences

private const val MEMBERS = "members"
private const val REVOKED = "revoked"
private const val POLICY = "policy"
private const val MISSION = "mission"
private const val USERID = "userid"
private const val USERNAME = "username"
private const val USERATTRS = "userattrs"
private const val USERINTERESTS = "userinterests"

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

    fun getMission(): String? = preferences.getString(MISSION, "")
    fun setMission(mission: String){
        preferences.edit().putString(MISSION,mission).apply()
    }

    fun getUserId(): Int? = preferences.getInt(USERID, 0)
    fun setUserId(userid: Int){
        preferences.edit().putInt(USERID,userid).apply()
    }

    fun getUserName(): String? = preferences.getString(USERNAME,"")
    fun setUserName(username: String){
        preferences.edit().putString(USERNAME,username).apply()
    }

    //test
    fun getUserAttrs(): String? = preferences.getString(USERATTRS,"")
    fun setUserAttrs(userattrs: String){
        preferences.edit().putString(USERATTRS,userattrs).apply()
    }

    fun getUserInterest(): String? = preferences.getString(USERINTERESTS,"")
    fun setUserInterest(userinterests: String){
        preferences.edit().putString(USERINTERESTS,userinterests).apply()
    }
}