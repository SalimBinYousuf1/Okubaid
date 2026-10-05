package com.example.model

import org.json.JSONArray
import org.json.JSONObject

data class FileItem(
    val name: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("name", name)
        obj.put("path", absolutePath)
        obj.put("isDirectory", isDirectory)
        obj.put("sizeBytes", sizeBytes)
        obj.put("lastModifiedMs", lastModifiedMs)
        return obj
    }

    companion object {
        fun listToJson(path: String, items: List<FileItem>): String {
            val root = JSONObject()
            root.put("type", "file_list_response")
            root.put("path", path)
            val arr = JSONArray()
            items.forEach { arr.put(it.toJson()) }
            root.put("items", arr)
            return root.toString()
        }
    }
}
