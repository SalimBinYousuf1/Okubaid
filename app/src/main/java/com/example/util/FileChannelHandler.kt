package com.example.util

import android.os.Environment
import android.util.Base64
import android.util.Log
import com.example.model.FileItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileChannelHandler {
    private const val TAG = "FileChannelHandler"
    private const val CHUNK_SIZE = 32 * 1024 // 32 KB chunk size for WebRTC DataChannel

    fun handleFileCommand(message: String, sendResponse: (String) -> Unit) {
        CrashProtector.safeRun(TAG, Unit) {
            val json = JSONObject(message)
            when (json.optString("action")) {
                "list" -> {
                    try {
                        val requestedPath = json.optString("path", "")
                        val targetDir = if (requestedPath.isBlank() || requestedPath == "/") {
                            Environment.getExternalStorageDirectory()
                        } else {
                            File(requestedPath)
                        }

                        val items = mutableListOf<FileItem>()
                        if (targetDir.exists() && targetDir.isDirectory) {
                            targetDir.listFiles()?.forEach { file ->
                                items.add(
                                    FileItem(
                                        name = file.name,
                                        absolutePath = file.absolutePath,
                                        isDirectory = file.isDirectory,
                                        sizeBytes = if (file.isDirectory) 0L else file.length(),
                                        lastModifiedMs = file.lastModified()
                                    )
                                )
                            }
                        }
                        sendResponse(FileItem.listToJson(targetDir.absolutePath, items))
                    } catch (e: Exception) {
                        Log.e(TAG, "List files error: ${e.message}", e)
                        val err = JSONObject().apply {
                            put("type", "file_error")
                            put("action", "list")
                            put("message", e.message ?: "Failed to list directory")
                        }
                        sendResponse(err.toString())
                    }
                }

                "read" -> {
                    val filePath = json.optString("path")
                    try {
                        val file = File(filePath)
                        if (!file.exists() || file.isDirectory) {
                            val err = JSONObject().apply {
                                put("type", "file_error")
                                put("path", filePath)
                                put("message", "File does not exist or is a directory")
                            }
                            sendResponse(err.toString())
                            return@safeRun
                        }

                        val totalBytes = file.length()
                        val totalChunks = ((totalBytes + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt().coerceAtLeast(1)
                        val buffer = ByteArray(CHUNK_SIZE)

                        FileInputStream(file).use { fis ->
                            var chunkIndex = 0
                            var bytesRead: Int
                            while (fis.read(buffer).also { bytesRead = it } != -1) {
                                val chunkData = if (bytesRead == CHUNK_SIZE) buffer else buffer.copyOf(bytesRead)
                                val base64Data = Base64.encodeToString(chunkData, Base64.NO_WRAP)
                                val chunkJson = JSONObject().apply {
                                    put("type", "file_read_chunk")
                                    put("path", filePath)
                                    put("chunkIndex", chunkIndex)
                                    put("totalChunks", totalChunks)
                                    put("totalBytes", totalBytes)
                                    put("data", base64Data)
                                }
                                sendResponse(chunkJson.toString())
                                chunkIndex++
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Read file error: ${e.message}", e)
                        val err = JSONObject().apply {
                            put("type", "file_error")
                            put("path", filePath)
                            put("message", e.message ?: "Failed to read file")
                        }
                        sendResponse(err.toString())
                    }
                }

                "write" -> {
                    val filePath = json.optString("path")
                    try {
                        val base64Data = json.optString("data")
                        val chunkIndex = json.optInt("chunkIndex", 0)
                        val totalChunks = json.optInt("totalChunks", 1)

                        val file = File(filePath)
                        file.parentFile?.mkdirs()

                        val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                        FileOutputStream(file, chunkIndex > 0).use { fos ->
                            fos.write(bytes)
                        }

                        if (chunkIndex == totalChunks - 1) {
                            val ack = JSONObject().apply {
                                put("type", "file_write_complete")
                                put("path", filePath)
                                put("sizeBytes", file.length())
                            }
                            sendResponse(ack.toString())
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Write file error: ${e.message}", e)
                        val err = JSONObject().apply {
                            put("type", "file_error")
                            put("path", filePath)
                            put("message", e.message ?: "Failed to write file")
                        }
                        sendResponse(err.toString())
                    }
                }
            }
        }
    }
}
