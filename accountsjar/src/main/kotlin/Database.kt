import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.sql.Connection
import javax.sql.DataSource


@Serializable
enum class Profile{
    NO_PROFILE,
}

object Accounts: IntIdTable(){
    val username = varchar("username",50).index()
    val password = varchar("password",30)
    val email = varchar("email",50).index()
    val profile = enumeration("profile",Profile::class).index()
    val cnAuthed = bool("cnAuthed").index()

    val lastUpdate = date("lastUpdate")
}


class Account(id: EntityID<Int>):IntEntity(id){
    companion object: IntEntityClass<Account>(Accounts)

    var username by Accounts.username
    var password by Accounts.password
    var email    by Accounts.email
    var profile  by Accounts.profile
    var cnAuthed by Accounts.cnAuthed

    var lastUpdate by Accounts.lastUpdate
}


fun main(){
    val db = Database.connect("Jdbc: Sqlite: ${System.getProperty("user.dir")}/test.Sqlite","org.sqlite.JDBC")
    transaction(transactionIsolation = Connection.TRANSACTION_SERIALIZABLE, repetitionAttempts = 3) {
        SchemaUtils.create (Accounts)

        Account.new {
            username = "AAA"
            password = "BBBB"
            email = "CCCC"
            password = "DDDD"
            profile = Profile.NO_PROFILE
            cnAuthed = false
            lastUpdate = DateTime.now()
        }

    }

}
