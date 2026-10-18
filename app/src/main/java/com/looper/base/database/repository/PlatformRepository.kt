package com.looper.videodownloader.database.repository

import com.looper.videodownloader.database.dao.PlatformDao

class PlatformRepository(
    platformDao: PlatformDao,
) {
    val platforms = platformDao.getPlatforms()
}
