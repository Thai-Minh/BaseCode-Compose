package com.looper.base.backup.model

enum class BackupFrequency(val intervalDays: Long) {
    DAILY(intervalDays = 1),
    WEEKLY(intervalDays = 7),
    MONTHLY(intervalDays = 30),
}

data class BackupSettings(
    val autoBackupEnabled: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.WEEKLY,
    val maxFiles: Int = 5,
) {
    fun normalized(): BackupSettings = copy(
        maxFiles = maxFiles.coerceIn(minimumValue = 1, maximumValue = 30),
    )
}
