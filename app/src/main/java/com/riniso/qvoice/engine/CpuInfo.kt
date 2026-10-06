package com.riniso.qvoice.engine

import java.io.File
import java.util.Locale

/**
 * The processor layout: one entry per core, its top speed in kHz as the
 * kernel reports it (0 = couldn't be read).
 *
 * Phones mix fast and slow cores (big.LITTLE). ONNX Runtime splits each step
 * of a model evenly across its threads, so a thread that lands on a slow core
 * holds up the others: more threads than fast cores costs speed and battery.
 */
data class CpuInfo(val maxFreqKHz: List<Long>) {

    val cores: Int get() = maxFreqKHz.size

    /**
     * Cores faster than the slowest cluster (the "big" cores), or null when
     * every core is alike or a speed couldn't be read.
     * Galaxy M31 (Exynos 9611, 4 × 2.31 + 4 × 1.74 GHz): 4.
     * Snapdragon 720G (2 × 2.3 + 6 × 1.8 GHz): 2.
     */
    val fastCores: Int?
        get() {
            if (maxFreqKHz.isEmpty() || maxFreqKHz.any { it <= 0L }) return null
            val slowest = maxFreqKHz.min()
            return maxFreqKHz.count { it > slowest }.takeIf { it > 0 }
        }

    /** For the log: "8 cores: 4 × 1.74 GHz + 4 × 2.31 GHz". */
    fun describe(): String {
        if (maxFreqKHz.isEmpty()) return "cores unknown"
        if (maxFreqKHz.any { it <= 0L }) return "$cores cores (speeds unknown)"
        return "$cores cores: " + maxFreqKHz.groupingBy { it }.eachCount().toSortedMap().entries
            .joinToString(" + ") { (kHz, count) -> String.format(Locale.ROOT, "%d × %.2f GHz", count, kHz / 1e6) }
    }

    companion object {
        private val CPU_DIR = Regex("cpu\\d+")

        /**
         * Reads `cpuN/cpufreq/cpuinfo_max_freq` under [root]. Any value that
         * can't be read (core offline, file hidden by the vendor) becomes 0,
         * and [fastCores] then falls back to "unknown" — never a guess.
         */
        fun read(root: File = File("/sys/devices/system/cpu")): CpuInfo {
            val dirs = root.listFiles { f -> f.isDirectory && CPU_DIR.matches(f.name) }.orEmpty()
                .sortedBy { it.name.removePrefix("cpu").toInt() }
            return CpuInfo(
                dirs.map { dir ->
                    runCatching { File(dir, "cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrDefault(0L)
                },
            )
        }
    }
}
