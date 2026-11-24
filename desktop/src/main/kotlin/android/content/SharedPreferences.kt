package android.content

/**
 * Minimal desktop stand-in for android.content.SharedPreferences.
 *
 * The shared MovieCatalogRepository (compiled from the Android module) only needs
 * getString / edit().putString().apply() to persist its offline home-feed snapshot, so that is
 * all this declares. The real implementation is com.example.desktop.data.FileSharedPreferences.
 */
interface SharedPreferences {
  fun getString(key: String, defValue: String?): String?
  fun edit(): Editor

  interface Editor {
    fun putString(key: String, value: String?): Editor
    fun apply()
  }
}
