package com.riniso.qvoice.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CpuInfoTest {

    /** A fake /sys/devices/system/cpu: one folder per core; null = no cpufreq file (core offline). */
    private fun sysfs(vararg maxFreqKHz: String?): File {
        val root = Files.createTempDirectory("qv-cpu").toFile()
        maxFreqKHz.forEachIndexed { i, freq ->
            val core = File(root, "cpu$i").apply { mkdirs() }
            if (freq != null) File(core, "cpufreq").apply { mkdirs() }.resolve("cpuinfo_max_freq").writeText("$freq\n")
        }
        // The real folder also holds entries that aren't cores.
        File(root, "cpufreq").mkdirs()
        File(root, "cpuidle").mkdirs()
        File(root, "possible").writeText("0-${maxFreqKHz.size - 1}\n")
        return root
    }

    private fun khz(vararg groups: Pair<Int, Long>): CpuInfo =
        CpuInfo(groups.flatMap { (count, freq) -> List(count) { freq } })

    @Test
    fun readsTheGalaxyM31() {
        val root = sysfs("1742000", "1742000", "1742000", "1742000", "2314000", "2314000", "2314000", "2314000")
        try {
            val cpu = CpuInfo.read(root)
            assertEquals(8, cpu.cores)
            assertEquals(4, cpu.fastCores)
            assertEquals("8 cores: 4 × 1.74 GHz + 4 × 2.31 GHz", cpu.describe())
            // Same as before slice 3 on this phone.
            assertEquals(4, ThreadPolicy.autoThreads(8, cpu.fastCores))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun anUnreadableCoreMeansUnknownNotAGuess() {
        val root = sysfs("1742000", null, "2314000", "2314000")
        try {
            val cpu = CpuInfo.read(root)
            assertEquals(4, cpu.cores)
            assertNull(cpu.fastCores)
            assertEquals("4 cores (speeds unknown)", cpu.describe())
        } finally {
            root.deleteRecursively()
        }
        assertEquals("cores unknown", CpuInfo.read(File("/nonexistent/qvoice")).describe())
    }

    @Test
    fun fastCoresAreAllButTheSlowestCluster() {
        assertEquals(2, khz(6 to 1_800_000L, 2 to 2_300_000L).fastCores) // Snapdragon 720G, Helio G85
        assertEquals(4, khz(4 to 1_800_000L, 3 to 2_420_000L, 1 to 2_840_000L).fastCores) // prime + gold + silver
        assertNull(khz(8 to 2_000_000L).fastCores) // all alike (8 x A55)
        assertNull(CpuInfo(emptyList()).fastCores)
    }

    @Test
    fun autoThreadsNeverExceedFastCores() {
        assertEquals(2, ThreadPolicy.autoThreads(8, 2)) // 2 fast + 6 slow: not 4
        assertEquals(4, ThreadPolicy.autoThreads(8, 5)) // plenty of fast cores: the usual 4
        assertEquals(4, ThreadPolicy.autoThreads(8, null)) // unknown layout: as before
        assertEquals(2, ThreadPolicy.autoThreads(4, null))
        assertEquals(1, ThreadPolicy.autoThreads(8, 1))
        assertEquals(1, ThreadPolicy.autoThreads(2, 1))
    }
}
