package com.theveloper.pixelplay.data.worker

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncWorkerFilesystemScanPolicyTest {

    @Test
    fun `manual incremental refresh forces filesystem scan inside cooldown`() {
        val now = 24_000L
        val lastScan = now - 1_000L

        assertTrue(
            FilesystemScanPolicy.shouldRunFilesystemScan(
                syncMode = SyncMode.INCREMENTAL,
                forceFilesystemScan = true,
                lastFilesystemScanTimestamp = lastScan,
                now = now,
                cooldownMs = 12_000L
            )
        )
    }

    @Test
    fun `startup incremental sync obeys filesystem scan cooldown`() {
        val now = 24_000L
        val lastScan = now - 1_000L

        assertFalse(
            FilesystemScanPolicy.shouldRunFilesystemScan(
                syncMode = SyncMode.INCREMENTAL,
                forceFilesystemScan = false,
                lastFilesystemScanTimestamp = lastScan,
                now = now,
                cooldownMs = 12_000L
            )
        )
    }

    @Test
    fun `startup incremental sync runs filesystem scan after cooldown`() {
        val now = 24_000L
        val lastScan = now - 12_000L

        assertTrue(
            FilesystemScanPolicy.shouldRunFilesystemScan(
                syncMode = SyncMode.INCREMENTAL,
                forceFilesystemScan = false,
                lastFilesystemScanTimestamp = lastScan,
                now = now,
                cooldownMs = 12_000L
            )
        )
    }

    @Test
    fun `full rescan and rebuild force filesystem scan`() {
        val now = 24_000L
        val lastScan = now - 1_000L

        assertTrue(
            FilesystemScanPolicy.shouldRunFilesystemScan(
                syncMode = SyncMode.FULL,
                forceFilesystemScan = false,
                lastFilesystemScanTimestamp = lastScan,
                now = now,
                cooldownMs = 12_000L
            )
        )
        assertTrue(
            FilesystemScanPolicy.shouldRunFilesystemScan(
                syncMode = SyncMode.REBUILD,
                forceFilesystemScan = false,
                lastFilesystemScanTimestamp = lastScan,
                now = now,
                cooldownMs = 12_000L
            )
        )
    }
}
