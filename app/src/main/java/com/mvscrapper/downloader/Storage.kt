package com.mvscrapper.downloader

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Moves finished files into the phone's shared folders (rule R1):
 * video to Movies/MV Downloader, audio to Music/MV Downloader.
 */
object Storage {
    const val FOLDER = "MV Downloader"

    fun mimeOf(name: String, audio: Boolean): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: if (audio) "audio/*" else "video/*"
    }

    private fun relPath(audio: Boolean, sub: String) =
        (if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/" + FOLDER +
            (if (sub.isNotEmpty()) "/$sub" else "")

    private fun downloadsRel(sub: String) =
        Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER + (if (sub.isNotEmpty()) "/$sub" else "")

    /** Where files go, as shown to you: the folder chosen in Settings, or the default. */
    private fun customLabel(sub: String): String? =
        Prefs.saveFolderName.value?.takeIf { Prefs.saveFolder.value != null }?.let { it + if (sub.isNotEmpty()) "/$sub" else "" }

    /** The folder you chose in Settings, if Android still lets the app write to it. */
    private fun customTree(context: Context): DocumentFile? =
        Prefs.saveFolder.value?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }?.takeIf { it.canWrite() }

    /** Copies [file] into the chosen folder, creating [sub] folders as needed. */
    private fun saveToTree(context: Context, root: DocumentFile, file: File, sub: String, mime: String): Uri {
        var dir = root
        sub.split('/').filter { it.isNotBlank() }.forEach { name ->
            dir = dir.findFile(name)?.takeIf { it.isDirectory } ?: dir.createDirectory(name)
                ?: error("Couldn't create the folder $name in ${root.name}")
        }
        val doc = dir.createFile(mime, file.name) ?: error("Couldn't create ${file.name} in ${root.name}")
        context.contentResolver.openOutputStream(doc.uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            ?: error("Couldn't write ${file.name}")
        return doc.uri
    }

    fun folderLabel(audio: Boolean, sub: String = "") = customLabel(sub) ?: relPath(audio, sub)

    /** Turns a playlist title into a safe folder name. */
    fun folderName(title: String): String =
        title.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").trim().trim('.').take(80).ifBlank { "Playlist" }

    /** Copies [file] into MediaStore and returns its content URI. */
    fun save(context: Context, file: File, audio: Boolean, sub: String = ""): Uri {
        val resolver = context.contentResolver
        val mime = mimeOf(file.name, audio)
        val collection = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        customTree(context)?.let { return saveToTree(context, it, file, sub, mime) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath(audio, sub))
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: error("Couldn't create the file in ${relPath(audio, sub)}")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: error("Couldn't write the file")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
            return uri
        }

        // Android 8 and 9: write the file directly, then register it.
        @Suppress("DEPRECATION")
        val base = Environment.getExternalStoragePublicDirectory(
            if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES,
        )
        val dir = File(File(base, FOLDER), sub).apply { mkdirs() }
        var target = File(dir, file.name)
        var n = 1
        while (target.exists()) target = File(dir, "${file.nameWithoutExtension} ($n).${file.extension}").also { n++ }
        file.copyTo(target)
        val values = ContentValues().apply {
            @Suppress("DEPRECATION")
            put(MediaStore.MediaColumns.DATA, target.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, target.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
        }
        return resolver.insert(collection, values) ?: Uri.fromFile(target)
    }

    /** Relative path for torrent files: Download/MV Downloader/<sub>. */
    fun downloadsLabel(sub: String = "") = customLabel(sub) ?: downloadsRel(sub)

    /** Copies a torrent file into Download/MV Downloader/[sub] (rule R15), or the chosen folder. Any file type. */
    fun saveDownload(context: Context, file: File, sub: String): Uri {
        val resolver = context.contentResolver
        val ext = file.name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        customTree(context)?.let { return saveToTree(context, it, file, sub, mime) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, downloadsRel(sub))
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Couldn't create the file in ${downloadsRel(sub)}")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: error("Couldn't write the file")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (t: Throwable) {
                resolver.delete(uri, null, null)
                throw t
            }
            return uri
        }
        @Suppress("DEPRECATION")
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(File(base, FOLDER), sub).apply { mkdirs() }
        var target = File(dir, file.name)
        var n = 1
        while (target.exists()) target = File(dir, "${file.nameWithoutExtension} ($n).${file.extension}").also { n++ }
        file.copyTo(target)
        return Uri.fromFile(target)
    }

    /** Deletes a saved file. Returns false if Android refused (e.g. the app was reinstalled). */
    fun delete(context: Context, uri: Uri): Boolean = try {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } else if (uri.scheme == "file") {
            File(uri.path ?: "").delete()
        } else {
            context.contentResolver.delete(uri, null, null) > 0
        }
    } catch (_: SecurityException) {
        false
    }
}
