package id.geraigo.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transactions")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Long,
    val quantity: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) { val total: Long get() = price * quantity }

@Dao interface SaleDao {
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC") fun observeAll(): Flow<List<Sale>>
    @Query("SELECT * FROM transactions WHERE name LIKE '%' || :query || '%' ORDER BY createdAt DESC") fun search(query: String): Flow<List<Sale>>
    @Query("SELECT DISTINCT name FROM transactions WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT 6") suspend fun suggestions(query: String): List<String>
    @Insert suspend fun insert(sale: Sale): Long
    @Update suspend fun update(sale: Sale)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM transactions WHERE createdAt >= :start ORDER BY createdAt DESC") suspend fun since(start: Long): List<Sale>
}

@Database(entities = [Sale::class], version = 1, exportSchema = true)
abstract class GeraiDatabase : RoomDatabase() {
    abstract fun sales(): SaleDao
    companion object {
        @Volatile private var instance: GeraiDatabase? = null
        fun get(context: Context): GeraiDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GeraiDatabase::class.java, "gerai-go.db").build().also { instance = it }
        }
    }
}
