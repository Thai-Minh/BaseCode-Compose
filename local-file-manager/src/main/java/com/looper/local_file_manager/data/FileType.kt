package com.looper.local_file_manager.data

private val VIDEO_MIME_TYPES = listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "ogg", "mpeg")
private val AUDIO_MIME_TYPES = listOf("mp3", "wav", "flac", "aac", "flac", "opus", "amr", "m4a")
private val IMAGE_MIME_TYPES = listOf("jpg", "jpeg", "png", "gif", "bmp", "tiff", "webp","heic", "heif")

enum class FileType(val supportTypes: List<String>) {
    Video(supportTypes = VIDEO_MIME_TYPES),
    Audio(supportTypes = AUDIO_MIME_TYPES),
    Image(supportTypes = IMAGE_MIME_TYPES),
    Other(supportTypes = emptyList()),
}