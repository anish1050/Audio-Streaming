package com.anish1050.hires.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

enum class DownloadState { QUEUED, DOWNLOADING, COMPLETED, FAILED }

/** Metadata for the Downloads screen and offline Library. The audio bytes live in Media3's encrypted cache. */
@Entity(tableName = "downloaded_tracks")
data class DownloadedTrack(
    @PrimaryKey @ColumnInfo(name = "track_id") val trackId: String,
    /** The full server Track JSON, so offline screens and queues need no network. */
    @ColumnInfo(name = "track_json") val trackJson: String,
    val quality: String,
    @ColumnInfo(name = "bit_depth") val bitDepth: Int,
    val bytes: Long,
    val state: DownloadState,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/** Listening events waiting to be uploaded; deleted once the server accepts them. */
@Entity(tableName = "pending_plays")
data class PendingPlay(
    @PrimaryKey @ColumnInfo(name = "client_event_id") val clientEventId: String,
    @ColumnInfo(name = "track_id") val trackId: String,
    @ColumnInfo(name = "ms_played") val msPlayed: Long,
    @ColumnInfo(name = "started_at") val startedAt: String,
    val quality: String,
    @ColumnInfo(name = "is_offline") val isOffline: Boolean,
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloaded_tracks ORDER BY created_at DESC")
    fun observeAll(): Flow<List<DownloadedTrack>>

    @Query("SELECT * FROM downloaded_tracks WHERE track_id = :trackId")
    suspend fun find(trackId: String): DownloadedTrack?

    @Query("SELECT track_id FROM downloaded_tracks WHERE state = 'COMPLETED'")
    suspend fun completedIds(): List<String>

    @Upsert
    suspend fun upsert(track: DownloadedTrack)

    @Query("UPDATE downloaded_tracks SET state = :state, bytes = :bytes WHERE track_id = :trackId")
    suspend fun updateState(trackId: String, state: DownloadState, bytes: Long)

    @Query("DELETE FROM downloaded_tracks WHERE track_id = :trackId")
    suspend fun delete(trackId: String)

    @Query("DELETE FROM downloaded_tracks")
    suspend fun deleteAll()
}

@Dao
interface PendingPlayDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(play: PendingPlay)

    @Query("SELECT * FROM pending_plays ORDER BY started_at LIMIT :limit")
    suspend fun oldest(limit: Int): List<PendingPlay>

    @Query("SELECT COUNT(*) FROM pending_plays")
    suspend fun count(): Int

    @Query("DELETE FROM pending_plays WHERE client_event_id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("DELETE FROM pending_plays")
    suspend fun deleteAll()
}

@Database(entities = [DownloadedTrack::class, PendingPlay::class], version = 1, exportSchema = true)
abstract class HiResDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun pendingPlayDao(): PendingPlayDao

    companion object {
        fun build(context: Context): HiResDatabase = Room.databaseBuilder(context, HiResDatabase::class.java, "hires.db").build()
    }
}
