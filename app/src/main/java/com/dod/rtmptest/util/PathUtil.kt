package com.dod.rtmptest.util

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import java.io.File

class PathUtil {

    companion object {
        fun getRecordPath(): File {
            val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            return File(storageDir.absolutePath + "/rtmp-rtsp-stream-client-java")
        }

        fun updateGallery(context: Context, path: String){
            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("mp4"), null)
        }
    }
}