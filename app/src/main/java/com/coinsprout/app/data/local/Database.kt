package com.coinsprout.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The M-Pesa receipt code is the natural key: it makes SMS re-delivery and re-sync idempotent. */
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val mpesaCode: String,
    val merchantName: String,
    val kind: String,
    val amountCents: Long,
    val roundUpCents: Long,
    val status: String,
    val occurredAtMillis: Long,
    val synced: Boolean
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val emoji: String,
    val targetCents: Long,
    val savedCents: Long,
    val deadline: String?
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val createdAtMillis: Long,
    val isRead: Boolean
)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY occurredAtMillis DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT COALESCE(SUM(roundUpCents), 0) FROM transactions WHERE status = 'PENDING_ROUNDUP'")
    fun observePendingCents(): Flow<Long>

    /** Returns -1 when the receipt code already exists (duplicate SMS). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(tx: TransactionEntity): Long

    @Upsert
    suspend fun upsert(tx: TransactionEntity)

    @Upsert
    suspend fun upsertAll(list: List<TransactionEntity>)

    @Query("SELECT * FROM transactions WHERE synced = 0 ORDER BY occurredAtMillis")
    suspend fun unsynced(): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE mpesaCode = :code")
    suspend fun delete(code: String)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}

@Dao
abstract class GoalDao {
    @Query("SELECT * FROM goals ORDER BY title")
    abstract fun observeAll(): Flow<List<GoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(list: List<GoalEntity>)

    @Query("DELETE FROM goals")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(list: List<GoalEntity>) {
        clear()
        insertAll(list)
    }
}

@Dao
abstract class NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAtMillis DESC")
    abstract fun observeAll(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(list: List<NotificationEntity>)

    @Query("DELETE FROM notifications")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(list: List<NotificationEntity>) {
        clear()
        insertAll(list)
    }
}

@Database(
    entities = [TransactionEntity::class, GoalEntity::class, NotificationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CoinSproutDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun goalDao(): GoalDao
    abstract fun notificationDao(): NotificationDao
}
