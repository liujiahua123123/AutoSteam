import kotlinx.coroutines.runBlocking

class Starter {
    companion object{
        @JvmStatic
        fun main(args: Array<String>){
            runBlocking {
                main()
            }
        }
    }
}