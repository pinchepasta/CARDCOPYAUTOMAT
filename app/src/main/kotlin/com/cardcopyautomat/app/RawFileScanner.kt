package com.cardcopyautomat.app

import androidx.documentfile.provider.DocumentFile

/**
 * Finds Canon RAW photo files anywhere under a SAF document tree.
 *
 * Canon RAW extensions covered:
 *  - .CR3  (current mirrorless bodies, e.g. EOS R series)
 *  - .CR2  (most DSLRs, still widely used)
 *  - .CRW  (older/legacy Canon bodies)
 *
 * A typical CF/SD card layout is DCIM/100CANON/IMG_0001.CR2, so this walks
 * the whole tree rather than assuming a fixed depth.
 */
object RawFileScanner {

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "tif", "tiff",
        "cr2", "cr3", "crw", "dng", "cdng", "nef", "arw", "orf", "raf", "srw", "mlv"
    )

    fun findImageFiles(root: DocumentFile): List<DocumentFile> {
        val results = mutableListOf<DocumentFile>()
        walk(root, results)
        return results
    }

    private fun walk(dir: DocumentFile, results: MutableList<DocumentFile>) {
        val children = dir.listFiles()
        for (child in children) {
            if (child.isDirectory) {
                walk(child, results)
            } else if (child.isFile && isImageFile(child.name)) {
                results.add(child)
            }
        }
    }

    private fun isImageFile(name: String?): Boolean {
        if (name == null) return false
        val dot = name.lastIndexOf('.')
        if (dot < 0 || dot == name.length - 1) return false
        val ext = name.substring(dot + 1).lowercase()
        return ext in IMAGE_EXTENSIONS
    }
}
