package com.asyachz.eyepayapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorite_cards")
data class FavoriteCard(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val bankName: String,
    val cardNumber: String,
    val expiryDate: String,
    val note: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface CardDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCard(card: FavoriteCard)

    @Query("SELECT * FROM favorite_cards ORDER BY createdAt DESC")
    fun getAllCards(): Flow<List<FavoriteCard>>

    @Delete
    suspend fun deleteCard(card: FavoriteCard)

    @Query("SELECT * FROM favorite_cards WHERE cardNumber = :number LIMIT 1")
    suspend fun getCardByNumber(number: String): FavoriteCard?
}