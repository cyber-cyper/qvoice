// Sandbox-only check (not part of the app): installs the REAL catalogue
// archives with the app's ArchiveInstaller and compares the result with the
// archive contents. Run by tools/sandbox/verify.sh when QVOICE_ARCHIVES points
// at a folder of downloaded .tar.bz2 files.
import com.riniso.qvoice.catalog.CatalogJson
import com.riniso.qvoice.voices.ArchiveInstaller
import com.riniso.qvoice.voices.QVoicePaths
import com.riniso.qvoice.voices.VoiceManifest
import com.riniso.qvoice.voices.VoiceManifestJson
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val catalog = CatalogJson.parse(File(args[0]).readText())
    val archives = File(args[1])
    val work = File(args[2]).apply { deleteRecursively(); mkdirs() }
    val paths = QVoicePaths(File(work, "files"))
    val installer = ArchiveInstaller(paths, prepareSharedData = {})
    var failures = 0
    for (entry in catalog.entries) {
        val archive = File(archives, entry.id + ".tar.bz2")
        val start = System.nanoTime()
        val manifest = installer.install(entry, archive)
        val seconds = (System.nanoTime() - start) / 1e9
        val dir = paths.voiceDir(entry.id)
        // Expected: every regular file in the archive, minus shared espeak data.
        val expected = HashMap<String, Long>()
        TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered())).use { tar ->
            while (true) {
                val e = tar.nextEntry ?: break
                if (!e.isFile || e.isSymbolicLink || e.isLink) continue
                val rel = e.name.removePrefix("./").substringAfter('/')
                if (entry.manifest.espeakData.key == "shared" && rel.startsWith("espeak-ng-data/")) continue
                expected[rel] = e.size
            }
        }
        val actual = dir.walkTopDown().filter { it.isFile && it.name != VoiceManifest.FILE_NAME }
            .associate { it.relativeTo(dir).invariantSeparatorsPath to it.length() }
        val installedBytes = actual.values.sum()
        val ok = expected == actual && installedBytes == entry.installedSize &&
            VoiceManifestJson.parse(File(dir, VoiceManifest.FILE_NAME).readText()) == manifest
        if (!ok) failures++
        println("%-48s %s %6.1f s  %4d files  %,d bytes%s".format(entry.id, if (ok) "OK  " else "FAIL", seconds, actual.size, installedBytes,
            if (ok) "" else "  expected ${expected.size} files / ${entry.installedSize} bytes; diff ${(expected.keys - actual.keys).take(3)} ${(actual.keys - expected.keys).take(3)}"))
        dir.deleteRecursively()
    }
    work.deleteRecursively()
    if (failures > 0) exitProcess(1)
}
