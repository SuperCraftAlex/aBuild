package abuild

import java.io.File
import java.net.URL
import java.nio.file.Files

internal fun tempFile(clas: String = "dep"): File =
    File.createTempFile("abuild_${clas}_", ".tmp")

internal fun tempDir(clas: String = "dep"): File {
    val temp = tempFile(clas)
    temp.delete()
    temp.mkdir()
    return temp
}

internal fun download(url: URL): File {
    val temp = tempFile()
    url.openStream().use { Files.copy(it, temp.toPath()) }
    return temp
}