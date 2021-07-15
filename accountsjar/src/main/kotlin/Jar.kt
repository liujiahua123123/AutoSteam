import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.jodatime.datetime
import org.joda.time.DateTime
import java.io.File
import java.sql.Connection

@Serializable
enum class Profile{
    NO_PROFILE,
    VECTOR_PROFILE,
}

@Serializable
data class SteamAccount(
    var id:Int = -1,
    val username: String,
    val password: String,
    val email:String,
    val profiled: Profile,
    val chinaAuthed: Boolean = false,
)

internal object DBAccounts: IntIdTable(){
    val username = varchar("username",50).index()
    val password = varchar("password",30)
    val email = varchar("email",50).index()
    val profile = enumeration("profile",Profile::class).index()
    val cnAuthed = bool("cnAuthed").index()

    val lastUpdate = datetime("lastUpdate")
}


class DBAccount(id: EntityID<Int>):IntEntity(id){
    companion object: IntEntityClass<DBAccount>(DBAccounts)

    var username by DBAccounts.username
    var password by DBAccounts.password
    var email    by DBAccounts.email
    var profile  by DBAccounts.profile
    var cnAuthed by DBAccounts.cnAuthed

    var lastUpdate by DBAccounts.lastUpdate


    internal fun toAccount():SteamAccount{
        return SteamAccount(
            if(id._value == null) {
                -1 //has not been insert yet.
            }else{
                id.value
            }
            ,username,password,email,profile,cnAuthed)
    }
}

object Jar{
    private val file = File("${System.getProperty("user.dir")}/accounts.sqlite").apply{
        createNewFile()
    }


    init {
        Database.connect("jdbc:sqlite:" + file.path, driver = "org.sqlite.JDBC")
        Class.forName("org.sqlite.JDBC")
        open {
            SchemaUtils.createMissingTablesAndColumns (DBAccounts)
        }
    }


    fun <T> open(statement: Transaction.() -> T):T{
        return transaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE, repetitionAttempts = 3,statement = statement)
    }

    fun getAccounts(op: SqlExpressionBuilder.() -> Op<Boolean>):Collection<SteamAccount>{
       return open { DBAccount.find(op).map { it.toAccount() }}
    }

    fun getAccount(op: SqlExpressionBuilder.() -> Op<Boolean>):SteamAccount?{
        return open { DBAccount.find(op).firstOrNull()?.toAccount()}
    }

    fun countAccounts(op: SqlExpressionBuilder.() -> Op<Boolean>):Long{
        return open { DBAccount.count(Op.build(op))}
    }

    fun deleteAccounts(op: SqlExpressionBuilder.() -> Op<Boolean>, limit:Int? = null){
        return open { DBAccounts.deleteWhere(limit,null,op) }
    }

    fun popAccount(op: SqlExpressionBuilder.() -> Op<Boolean>):SteamAccount?{
        return open { DBAccount.find(op).firstOrNull()?.apply {
            delete()
        }?.toAccount()}
    }


    private fun pushAccount0(account:SteamAccount){
        val dba = DBAccount.new {
            lastUpdate = DateTime.now()
            username = account.username
            password = account.password
            email = account.email
            profile = account.profiled
            cnAuthed = account.chinaAuthed
        }
        account.id = dba.id.value
    }

    fun pushAccount(account:SteamAccount){
        open {
            pushAccount0(account)
        }
    }

    fun pushAccounts(accounts: Collection<SteamAccount>){
        open {
            accounts.forEach {
                pushAccount0(it)
            }
        }
    }
}


@Serializable
data class Account(
    val username: String,
    val password: String,
    val email:String,
    val profiled: Boolean = false,
    val chinaAuthed: Boolean = false,
)

fun main(){
    //Jar.pushAccount(SteamAccount(-1,"Test","Test","Test",Profile.NO_PROFILE,false))


}


