package com.looper.local_file_manager

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import com.looper.local_file_manager.data.FileItem
import com.looper.local_file_manager.data.FileManagerState
import com.looper.local_file_manager.data.FileType
import com.looper.local_file_manager.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File

class LocalFileManagerImpl(private val application: Application) : ILocalFileManager {

    private val mainScope = MainScope()

    private val queryVideoChannel = Channel<Unit>(Channel.CONFLATED)
    private val queryAudioChannel = Channel<Unit>(Channel.CONFLATED)
    private val queryImageChannel = Channel<Unit>(Channel.CONFLATED)

    override val videoFileItem: Flow<FileItem> = flow {
        for (channel in queryVideoChannel) {
            emit(FileItem.Video(state = FileManagerState.Loading))

            val videoFiles = queryVideoMediaStore(context = application)

            emitVideo(files = videoFiles)
        }
    }.onStart {
        application.registerObserverMediaStore(uri = VIDEO_EXTERNAL_URI, observer = videoOb)
        queryVideoChannel.trySend(Unit)
    }.onCompletion {
        application.unregisterObserverMediaStore(videoOb)
    }.flowOn(Dispatchers.IO)
        .shareIn(scope = mainScope, started = SharingStarted.WhileSubscribed(), replay = 1)

    override val audioFileItem: Flow<FileItem> = flow {
        for (channel in queryAudioChannel) {
            emit(FileItem.Audio(state = FileManagerState.Loading))

            val audioFiles = queryAudioMediaStore(context = application)

            emitAudio(files = audioFiles)
        }
    }.onStart {
        application.registerObserverMediaStore(uri = AUDIO_EXTERNAL_URI, observer = audioOb)
        queryAudioChannel.trySend(Unit)
    }.onCompletion {
        application.unregisterObserverMediaStore(audioOb)
    }.flowOn(Dispatchers.IO)
        .shareIn(scope = mainScope, started = SharingStarted.WhileSubscribed(), replay = 1)

    override val imageFileItem: Flow<FileItem> = flow {
        for (channel in queryImageChannel) {
            emit(FileItem.Image(state = FileManagerState.Loading))

            val imageFiles = queryImageMediaStore(context = application)

            emitImage(files = imageFiles)
        }
    }.onStart {
        application.registerObserverMediaStore(uri = IMAGE_EXTERNAL_URI, observer = imageOb)
        queryImageChannel.trySend(Unit)
    }.onCompletion {
        application.unregisterObserverMediaStore(imageOb)
    }.flowOn(Dispatchers.IO)
        .shareIn(scope = mainScope, started = SharingStarted.WhileSubscribed(), replay = 1)

    private val videoOb = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            queryVideoChannel.trySend(Unit)
            super.onChange(selfChange, uri)
        }
    }

    private val audioOb = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            queryAudioChannel.trySend(Unit)
            super.onChange(selfChange, uri)
        }
    }

    private val imageOb = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            queryImageChannel.trySend(Unit)
            super.onChange(selfChange, uri)
        }
    }

    override fun fetch() {
        queryVideoChannel.trySend(Unit)
        queryAudioChannel.trySend(Unit)
        queryImageChannel.trySend(Unit)
    }

    override fun getRootFolder(): File? {
        return try {
            return Environment.getExternalStorageDirectory()
        } catch (e: Exception) {
            val storagePath = getStoragePath(application) ?: return null
            return File(storagePath)
        }
    }

    override suspend fun listChildFile(parent: File): List<File> {
        return getListFile(files = parent.listFiles()?.toList(), type = FileType.Other)
            ?: emptyList()
    }

    override suspend fun getDuration(path: String): Long? = withContext(Dispatchers.IO) {
        getDuration(context = application, path = path)
    }

    override suspend fun deleteFile(activity: ComponentActivity, path: String): Boolean =
        deleteFiles(activity = activity, paths = listOf(path))

    override suspend fun deleteFiles(activity: ComponentActivity, paths: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            if (paths.isEmpty()) return@withContext false

            var result = true

            paths.forEach {
                if (!isActive)
                    return@withContext false

                result = activity.deletePath(path = it) == true
            }

            return@withContext result
        }

    private suspend fun FlowCollector<FileItem.Video>.emitVideo(files: List<MediaItem>?) {
        if (!files.isNullOrEmpty()) {
            emit(FileItem.Video(state = FileManagerState.Success, list = files))
        } else if (files == null) {
            emit(FileItem.Video(state = FileManagerState.Failed))
        } else {
            emit(FileItem.Video(state = FileManagerState.Empty))
        }
    }

    private suspend fun FlowCollector<FileItem.Audio>.emitAudio(files: List<MediaItem>?) {
        if (!files.isNullOrEmpty()) {
            emit(FileItem.Audio(state = FileManagerState.Success, list = files))
        } else if (files == null) {
            emit(FileItem.Audio(state = FileManagerState.Failed))
        } else {
            emit(FileItem.Audio(state = FileManagerState.Empty))
        }
    }

    private suspend fun FlowCollector<FileItem.Image>.emitImage(files: List<MediaItem>?) {
        if (!files.isNullOrEmpty()) {
            emit(FileItem.Image(state = FileManagerState.Success, list = files))
        } else if (files == null) {
            emit(FileItem.Image(state = FileManagerState.Failed))
        } else {
            emit(FileItem.Image(state = FileManagerState.Empty))
        }
    }

    private suspend fun getListFile(files: List<File>?, type: FileType): List<File>? = withContext(
        Dispatchers.IO
    ) {
        files?.filter {
            !it.isHidden && (it.isDirectory || it.isSupportFile(type = type))
        }
    }

    private fun Context.unregisterObserverMediaStore(contentOb: ContentObserver) {
        try {
            contentResolver.unregisterContentObserver(contentOb)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun Context.registerObserverMediaStore(
        uri: Uri,
        observer: ContentObserver,
    ) {
        contentResolver.registerContentObserver(uri, true, observer)
    }
}