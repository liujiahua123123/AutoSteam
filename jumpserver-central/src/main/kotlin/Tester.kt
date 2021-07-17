suspend fun main(){
    val cjh = """
        23.95.213.143-pFU89Tr0n5RZ6yhpT1
    """.trimIndent().split("\n").map {
        JumpServer(it.split("-")[0],it.split("-")[1])
    }


    cjh.forEach {
        println(it.test())
    }
}


/**
23.94.182.111-7lWlp4U59zvX8IVjJ8
23.94.190.104-7IQCaymwENg432Z8k7
172.245.6.145-Bwp8E893gxJG2L4qkJ
172.245.156.111-QqA1ti25B76CH8Mkmr
107.173.24.129-kImXLusX06S62Lfh29
104.168.96.118-2VezE9N9Z9aBpUgn06
107.175.87.113-9BncENr1W7fR7tV1l5
107.172.156.106-6Qadz27rF67m5ZVHfI
104.168.46.119-UoA902hPbeJT3p6iI5
*/