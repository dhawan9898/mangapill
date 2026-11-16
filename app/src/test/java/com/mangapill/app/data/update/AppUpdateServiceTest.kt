package com.mangapill.app.data.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateServiceTest {

    @Test
    fun isNewerVersionComparesSemverCorrectly() {
        assertTrue(AppUpdateService.isNewerVersion("1.0.1", "1.0.0"))
        assertTrue(AppUpdateService.isNewerVersion("1.1.0", "1.0.9"))
        assertTrue(AppUpdateService.isNewerVersion("2.0.0", "1.9.9"))
        assertTrue(AppUpdateService.isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue(AppUpdateService.isNewerVersion("v1.0.1", "v1.0.0"))

        assertFalse(AppUpdateService.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(AppUpdateService.isNewerVersion("1.0.0", "1.0.1"))
        assertFalse(AppUpdateService.isNewerVersion("0.9.9", "1.0.0"))
    }
}

