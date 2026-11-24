package com.example.desktop.data

import android.content.SharedPreferences
import java.io.File
import org.json.JSONObject

/** File-backed implementation used by MovieCatalogRepository to keep its offline snapshot. */
class FileSharedPreferences(private val file: File) : SharedPreferences {
  private val lock = Any()

  private fun readAll(): JSONObject =
    runCatching { if (file.exists()) JSONObject(file.readText()) else JSONObject() }.getOrDefault(JSONObject())

  override fun getString(key: String, defValue: String?): String? =
    synchronized(lock) { readAll().optString(key, "").takeIf { it.isNotEmpty() } ?: defValue }

  override fun edit(): SharedPreferences.Editor =
    object : SharedPreferences.Editor {
      private val pending = linkedMapOf<String, String?>()

      override fun putString(key: String, value: String?): SharedPreferences.Editor {
        pending[key] = value
        return this
      }

      override fun apply() {
        synchronized(lock) {
          val obj = readAll()
          pending.forEach { (k, v) -> if (v == null) obj.remove(k) else obj.put(k, v) }
          runCatching {
            file.parentFile?.mkdirs()
            file.writeText(obj.toString())
          }
        }
      }
    }
}
