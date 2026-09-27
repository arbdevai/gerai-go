package id.geraigo.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transactions")
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val price: Long,
    val quantity: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false,
    val receiptKey: String = "",
    val taxAmount: Long = 0,
    val adminAmount: Long = 0,
    val taxPercent: Double = 0.0,
    val adminPercent: Double = 0.0
) { val total: Long get() = price * quantity }

data class OrderReceipt(val key: String, val items: List<Sale>) {
    val subtotal: Long get() = items.sumOf { it.total }
    val taxAmount: Long get() = items.sumOf { it.taxAmount }
    val adminAmount: Long get() = items.sumOf { it.adminAmount }
    val taxPercent: Double get() = items.firstOrNull()?.taxPercent ?: 0.0
    val adminPercent: Double get() = items.firstOrNull()?.adminPercent ?: 0.0
    val total: Long get() = subtotal + taxAmount + adminAmount
    val createdAt: Long get() = items.minOfOrNull { it.createdAt } ?: 0
    val note: String get() = items.firstOrNull { it.note.isNotBlank() }?.note.orEmpty()
}

@Entity(tableName = "store_settings")
data class StoreSettings(
    @PrimaryKey val id: Int = 1,
    val name: String = "Gerai Go",
    val address: String = "",
    val contact: String = "",
    val header: String = "",
    val footer: String = "Terima kasih telah berbelanja",
    val logoUri: String = "",
    val extraImageUri: String = "",
    val showLogo: Boolean = true,
    val showExtraImage: Boolean = false,
    val imageWidth: Int = 160,
    val paperWidth: Int = 58,
    val taxEnabled: Boolean = false,
    val taxPercent: Double = 0.0,
    val adminFeeEnabled: Boolean = false,
    val adminFeePercent: Double = 0.0,
    val printerAddress: String = ""
)

@Dao interface SaleDao {
    @Query("SELECT * FROM transactions ORDER BY createdAt DESC") fun observeAll(): Flow<List<Sale>>
    @Query("SELECT * FROM transactions WHERE name LIKE '%' || :query || '%' OR note LIKE '%' || :query || '%' ORDER BY createdAt DESC") fun search(query: String): Flow<List<Sale>>
    @Query("SELECT name FROM transactions WHERE name LIKE '%' || :query || '%' GROUP BY name ORDER BY MAX(createdAt) DESC LIMIT 8") suspend fun suggestions(query: String): List<String>
    @Query("SELECT price FROM transactions WHERE name = :name ORDER BY createdAt DESC LIMIT 1") suspend fun latestPrice(name: String): Long?
    @Query("SELECT DISTINCT name FROM transactions WHERE favorite = 1 ORDER BY name") fun observeFavorites(): Flow<List<String>>
    @Query("SELECT * FROM transactions WHERE name = :name ORDER BY createdAt DESC LIMIT 1") suspend fun latest(name: String): Sale?
    @Insert suspend fun insert(sale: Sale): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(sales: List<Sale>)
    @Query("DELETE FROM transactions WHERE receiptKey = :receiptKey") suspend fun deleteReceipt(receiptKey: String)
    @Transaction suspend fun replaceReceipt(receiptKey: String, sales: List<Sale>) {
        deleteReceipt(receiptKey)
        insertAll(sales)
    }
    @Update suspend fun update(sale: Sale)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM transactions") suspend fun clear()
}

@Dao interface StoreSettingsDao {
    @Query("SELECT * FROM store_settings WHERE id = 1") fun observe(): Flow<StoreSettings?>
    @Query("SELECT * FROM store_settings WHERE id = 1") suspend fun get(): StoreSettings?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(settings: StoreSettings)
}

@Database(entities = [Sale::class, StoreSettings::class], version = 3, exportSchema = true)
abstract class GeraiDatabase : RoomDatabase() {
    abstract fun sales(): SaleDao
    abstract fun settings(): StoreSettingsDao
    companion object {
        @Volatile private var instance: GeraiDatabase? = null
        fun get(context: Context): GeraiDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, GeraiDatabase::class.java, "gerai-go.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS store_settings (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, address TEXT NOT NULL, contact TEXT NOT NULL, header TEXT NOT NULL, footer TEXT NOT NULL, logoUri TEXT NOT NULL, extraImageUri TEXT NOT NULL, showLogo INTEGER NOT NULL, showExtraImage INTEGER NOT NULL, imageWidth INTEGER NOT NULL, paperWidth INTEGER NOT NULL)")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN receiptKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE transactions ADD COLUMN taxAmount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN adminAmount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN taxPercent REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN adminPercent REAL NOT NULL DEFAULT 0")
                db.execSQL("UPDATE transactions SET receiptKey = CAST(id AS TEXT) WHERE receiptKey = ''")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN taxEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN taxPercent REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN adminFeeEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN adminFeePercent REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE store_settings ADD COLUMN printerAddress TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
