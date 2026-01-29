package com.homedashboard.app.data.local

import androidx.room.*
import com.homedashboard.app.data.model.Task
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZonedDateTime

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 ORDER BY isCompleted ASC, priority DESC, CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC, createdAt DESC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND isCompleted = 0 ORDER BY priority DESC, CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC, createdAt DESC")
    fun getIncompleteTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND isCompleted = 1 ORDER BY completedAt DESC")
    fun getCompletedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND dueDate = :date ORDER BY priority DESC, createdAt DESC")
    fun getTasksByDueDate(date: LocalDate): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE isDeleted = 0 AND dueDate <= :date AND isCompleted = 0 ORDER BY dueDate ASC, priority DESC")
    fun getOverdueTasks(date: LocalDate): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: String): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<Task>)

    @Update
    suspend fun updateTask(task: Task)

    @Query("UPDATE tasks SET isCompleted = :isCompleted, completedAt = :completedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTaskCompletion(
        id: String,
        isCompleted: Boolean,
        completedAt: ZonedDateTime?,
        updatedAt: ZonedDateTime = ZonedDateTime.now()
    )

    @Query("UPDATE tasks SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteTask(id: String, now: ZonedDateTime = ZonedDateTime.now())

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun hardDeleteTask(id: String)

    @Query("DELETE FROM tasks WHERE isDeleted = 1")
    suspend fun purgeDeletedTasks()
}
