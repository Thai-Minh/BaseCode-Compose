package com.looper.local_file_manager

import androidx.activity.ComponentActivity
import com.looper.local_file_manager.data.FileItem
import kotlinx.coroutines.flow.Flow
import java.io.File

interface ILocalFileManager {

    val videoFileItem: Flow<FileItem>

    val audioFileItem: Flow<FileItem>

    val imageFileItem: Flow<FileItem>

    fun fetch()

    fun getRootFolder(): File?

    suspend fun listChildFile(parent: File): List<File>

    suspend fun getDuration(path: String): Long?

    suspend fun deleteFile(activity: ComponentActivity, path: String): Boolean

    suspend fun deleteFiles(activity: ComponentActivity, paths: List<String>): Boolean
}