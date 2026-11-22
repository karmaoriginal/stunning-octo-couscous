package app.kromiv.core.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperDao {
    @Query("SELECT * FROM wallpapers ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<WallpaperEntity>>

    @Query("SELECT * FROM wallpapers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WallpaperEntity?

    @Query("SELECT * FROM wallpapers WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<WallpaperEntity?>

    @Query("SELECT * FROM wallpapers WHERE sha256 = :sha256 LIMIT 1")
    suspend fun getBySha256(sha256: String): WallpaperEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WallpaperEntity)

    @Query("UPDATE wallpapers SET title = :newTitle WHERE id = :id")
    suspend fun updateTitle(id: String, newTitle: String)

    @Query("UPDATE wallpapers SET enhanceStatus = :status, enhanceProgress = :progress WHERE id = :id")
    suspend fun updateEnhanceProgress(id: String, status: String, progress: Float)

    @Query(
        """
        UPDATE wallpapers SET 
            enhancedPath = :enhancedPath,
            enhancedSizeBytes = :enhancedSizeBytes,
            width = :newWidth,
            height = :newHeight,
            codec = :newCodec,
            enhanceStatus = :status,
            enhanceProgress = 1.0
        WHERE id = :id
        """
    )
    suspend fun markEnhanced(
        id: String,
        enhancedPath: String,
        enhancedSizeBytes: Long,
        newWidth: Int,
        newHeight: Int,
        newCodec: String,
        status: String
    )

    @Query("SELECT COUNT(*) FROM wallpapers")
    suspend fun count(): Int

    @Query("DELETE FROM wallpapers WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT wallpaperId FROM favorites")
    fun observeFavoriteIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE wallpaperId = :id)")
    suspend fun isFavorite(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE wallpaperId = :id")
    suspend fun remove(id: String)
}

@Database(
    entities = [WallpaperEntity::class, FavoriteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KromivDatabase : RoomDatabase() {
    abstract fun wallpaperDao(): WallpaperDao
    abstract fun favoriteDao(): FavoriteDao
}
