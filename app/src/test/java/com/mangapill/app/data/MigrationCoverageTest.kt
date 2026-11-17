package com.mangapill.app.data

import com.mangapill.app.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Every database version exported to app/schemas must be reachable from version 1 through
 * [AppDatabase.MIGRATIONS]. A table change without its migration would stop the app from opening
 * its database after the update, for everyone who has it installed.
 */
class MigrationCoverageTest {
    @Test
    fun everySchemaVersionHasAMigration() {
        val dir = File("schemas/com.mangapill.app.data.local.AppDatabase")
        val versions = dir.listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.sorted()
        assertTrue("No exported schemas found in ${dir.absolutePath}", versions.isNotEmpty())
        assertEquals("Schema files must be numbered 1..N without gaps", (1..versions.last()).toList(), versions)
        val steps = AppDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.sortedBy { it.first }
        assertEquals("Missing or extra migrations", (1 until versions.last()).map { it to it + 1 }, steps)
    }
}
