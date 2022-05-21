package com.example.securedatasharingfordtn.congestion

class EndpointInfo {
//    companion object {
//        val MsgInitInfo = 0
//        val MsgDirectory = 1
//        val MsgData = 2
//        val MsgReward = 3
//        val StatusString = arrayOf("Disconnected", "Available", "Busy")
//    }

    lateinit var name: String
    lateinit var username: String
    lateinit var userattrs: String
    lateinit var userinterests: String
//    lateinit var userinterests: String
//    var status = -1 //-1 -> no info received yet, 0 -> disconnected, 1->connected, 2-> pending
//    var infosent = false //initially, info won't be sent
}