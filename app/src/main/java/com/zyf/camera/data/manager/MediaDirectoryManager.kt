package com.zyf.camera.data.manager

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.zyf.camera.utils.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 媒体目录管理器
 * 统一管理照片和视频文件的保存路径和媒体扫描
 */
class MediaDirectoryManager(private val context: Context) {
    
    companion object {
        private const val CAMERA_DIRECTORY = "GCCamera"
        private const val TAG = "MediaDirectoryManager"
    }

    /**
     * 获取相机应用的DCIM目录
     */
    private fun getCameraDirectory(): File {
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val cameraDir = File(dcimDir, CAMERA_DIRECTORY)
        
        if (!cameraDir.exists()) {
            val created = cameraDir.mkdirs()
            Logger.d(TAG, "Camera directory created: $created, path: ${cameraDir.absolutePath}")
        }
        
        return cameraDir
    }

    /**
     * 创建照片文件
     */
    fun createPhotoFile(prefix: String = "IMG"): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cameraDir = getCameraDirectory()
        return File(cameraDir, "${prefix}_${timeStamp}.jpg")
    }

    /**
     * 创建视频文件
     */
    fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cameraDir = getCameraDirectory()
        return File(cameraDir, "VID_${timeStamp}.mp4")
    }

    /**
     * 创建RAW文件
     */
    fun createRawFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cameraDir = getCameraDirectory()
        return File(cameraDir, "RAW_${timeStamp}.dng")
    }

    /**
     * 直接保存照片字节数据到媒体库
     * 避免创建重复文件
     */
    fun savePhotoToMediaStore(imageBytes: ByteArray, prefix: String = "IMG"): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                savePhotoBytesToMediaStoreQ(imageBytes, prefix)
            } else {
                savePhotoBytesToMediaStoreLegacy(imageBytes, prefix)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save photo to media store: ${e.message}")
            null
        }
    }

    /**
     * 保存照片到媒体库并通知系统扫描
     * 兼容Android 10+的分区存储
     * @deprecated 使用savePhotoToMediaStore(ByteArray, String)避免重复文件
     */
    @Deprecated("Use savePhotoToMediaStore(ByteArray, String) to avoid duplicate files")
    fun savePhotoToMediaStore(file: File): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                savePhotoToMediaStoreQ(file)
            } else {
                savePhotoToMediaStoreLegacy(file)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save photo to media store: ${e.message}")
            null
        }
    }

    /**
     * 创建视频文件并直接注册到MediaStore，避免后续重复保存
     * 返回文件路径和MediaStore URI的Pair
     */
    fun createVideoFileInMediaStore(): Pair<File, Uri>? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                createVideoFileInMediaStoreQ()
            } else {
                createVideoFileInMediaStoreLegacy()
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to create video file in media store: ${e.message}")
            null
        }
    }

    /**
     * 保存视频到媒体库并通知系统扫描
     * 兼容Android 10+的分区存储
     */
    fun saveVideoToMediaStore(file: File): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveVideoToMediaStoreQ(file)
            } else {
                saveVideoToMediaStoreLegacy(file)
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save video to media store: ${e.message}")
            null
        }
    }

    /**
     * Android 10+创建视频文件并注册到MediaStore
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun createVideoFileInMediaStoreQ(): Pair<File, Uri>? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "VID_$timestamp.mp4"
        val file = File(getCameraDirectory(), fileName)
        
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$CAMERA_DIRECTORY")
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        return uri?.let { 
            Logger.d(TAG, "Video file registered in MediaStore: $uri, local path: ${file.absolutePath}")
            Pair(file, uri)
        }
    }

    /**
     * Android 9及以下创建视频文件并注册到MediaStore
     */
    private fun createVideoFileInMediaStoreLegacy(): Pair<File, Uri>? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "VID_$timestamp.mp4"
        val file = File(getCameraDirectory(), fileName)
        
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DATA, file.absolutePath)
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        return uri?.let { 
            Logger.d(TAG, "Video file registered in MediaStore: $uri, local path: ${file.absolutePath}")
            // 通知媒体扫描器
            notifyMediaScanner(file)
            Pair(file, uri)
        }
    }

    /**
     * Android 10+分区存储直接保存照片字节数据
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun savePhotoBytesToMediaStoreQ(imageBytes: ByteArray, prefix: String): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${prefix}_$timestamp.jpg"
        
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$CAMERA_DIRECTORY")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { 
            resolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(imageBytes)
            }
            Logger.d(TAG, "Photo bytes saved to MediaStore with URI: $uri")
        }
        return uri
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun savePhotoToMediaStoreQ(file: File): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$CAMERA_DIRECTORY")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { 
            resolver.openOutputStream(it)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Logger.d(TAG, "Photo saved to MediaStore with URI: $uri")
        }
        return uri
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveVideoToMediaStoreQ(file: File): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DCIM}/$CAMERA_DIRECTORY")
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { 
            resolver.openOutputStream(it)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            Logger.d(TAG, "Video saved to MediaStore with URI: $uri")
        }
        return uri
    }

    /**
     * Android 9及以下版本直接保存照片字节数据到自定义目录
     */
    private fun savePhotoBytesToMediaStoreLegacy(imageBytes: ByteArray, prefix: String): Uri? {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${prefix}_$timestamp.jpg"
        val file = File(getCameraDirectory(), fileName)
        
        try {
            file.writeBytes(imageBytes)
            
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, file.absolutePath)
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            Logger.d(TAG, "Photo bytes saved and registered in MediaStore with URI: $uri")
            
            // 通知媒体扫描器
            notifyMediaScanner(file)
            return uri
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to save photo bytes: ${e.message}")
            // 清理失败的文件
            if (file.exists()) {
                file.delete()
            }
            return null
        }
    }

    private fun savePhotoToMediaStoreLegacy(file: File): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DATA, file.absolutePath)
            put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        Logger.d(TAG, "Photo registered in MediaStore with URI: $uri")
        
        // 通知媒体扫描器
        notifyMediaScanner(file)
        return uri
    }

    private fun saveVideoToMediaStoreLegacy(file: File): Uri? {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DATA, file.absolutePath)
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        Logger.d(TAG, "Video registered in MediaStore with URI: $uri")
        
        // 通知媒体扫描器
        notifyMediaScanner(file)
        return uri
    }

    /**
     * 通知媒体扫描器扫描文件 (适用于Android 9及以下)
     */
    private fun notifyMediaScanner(file: File) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(file.absolutePath),
                    null
                ) { path, uri ->
                    Logger.d(TAG, "Media scanner callback - path: $path, uri: $uri")
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to notify media scanner: ${e.message}")
            }
        }
    }

    /**
     * 检查外部存储是否可写
     */
    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    /**
     * 获取相机目录路径（用于日志显示）
     */
    fun getCameraDirectoryPath(): String {
        return getCameraDirectory().absolutePath
    }
}