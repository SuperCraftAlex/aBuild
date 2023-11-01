package abuild

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

fun unzip(file: File): File {
    val temp = tempDir()
    unzip(file.absolutePath, temp.absolutePath)
    return temp
}

fun unzip(zipFilePath: String, destDir: String) {
    val dir = File(destDir)
    // creating an output directory if it doesn't exist already
    if (!dir.exists()) dir.mkdirs()
    // buffer to read and write data in the file
    val buffer = ByteArray(1024)
    val fis = FileInputStream(zipFilePath)
    val zis = ZipInputStream(fis)
    var ze: ZipEntry? = zis.getNextEntry()
    while (ze != null) {
        val fileName = ze.name
        val newFile = File(destDir + File.separator + fileName)
        // create directories for sub directories in zip
        newFile.parentFile?.mkdirs()
        if (ze.isDirectory) {
            newFile.mkdir()
            ze = zis.getNextEntry()
            continue
        }
        val fos = FileOutputStream(newFile)
        var len: Int
        while (zis.read(buffer).also { len = it } > 0) {
            fos.write(buffer, 0, len)
        }
        fos.close()
        // close this ZipEntry
        zis.closeEntry()
        ze = zis.getNextEntry()
    }
    // close last ZipEntry
    zis.closeEntry()
    zis.close()
    fis.close()
}