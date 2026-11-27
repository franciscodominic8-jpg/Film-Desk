package com.filmdesk.app.data

import android.content.Context
import com.filmdesk.app.model.EditParams
import org.json.JSONArray
import org.json.JSONObject

data class Recipe(val id: Long, val name: String, val params: EditParams)

/** Saved looks, stored as JSON in SharedPreferences (works on every Android version). */
class RecipeStore(context: Context) {
    private val prefs = context.getSharedPreferences("recipes", Context.MODE_PRIVATE)

    fun load(): List<Recipe> {
        val raw = prefs.getString(KEY, null) ?: return starterRecipes().also { save(it) }
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> parse(arr.optJSONObject(i)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<Recipe>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "list"
        private const val SHARE_PREFIX = "Lumora Film recipe"

        fun toJson(r: Recipe): JSONObject =
            JSONObject().put("id", r.id).put("name", r.name).put("params", r.params.toJson())

        fun parse(o: JSONObject?): Recipe? {
            if (o == null) return null
            val p = o.optJSONObject("params") ?: return null
            return Recipe(
                id = o.optLong("id", System.currentTimeMillis()),
                name = o.optString("name", "Recipe").take(60),
                params = EditParams.fromJson(p).withoutGeometry(),
            )
        }

        /** Text that can be pasted into a chat; another Film Desk user can import it. */
        fun shareText(r: Recipe): String =
            "$SHARE_PREFIX \"${r.name}\"\n" + JSONObject().put("name", r.name).put("params", r.params.toJson()).toString()

        fun fromShareText(text: String): Recipe? {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            return try {
                parse(JSONObject(text.substring(start, end + 1)))?.copy(id = System.currentTimeMillis())
            } catch (e: Exception) {
                null
            }
        }

        private fun starterRecipes(): List<Recipe> {
            val t = System.currentTimeMillis()
            return listOf(
                Recipe(t, "Street Chrome", EditParams(
                    sim = 3, grain = 1, colorChrome = 2, wb = 1, wbShiftR = 2, wbShiftB = -4,
                    dynamicRange = 2, highlight = -1f, shadow = 1f, color = 2, sharpness = -1, noiseReduction = -4)),
                Recipe(t + 1, "Golden Hour Neg", EditParams(
                    sim = 8, grain = 4, colorChrome = 1, wb = 2, wbShiftR = 3, wbShiftB = -5,
                    dynamicRange = 1, highlight = -2f, shadow = 2f)),
                Recipe(t + 2, "Teal Night", EditParams(
                    sim = 9, colorChromeBlue = 2, wb = 9, kelvin = 4200, wbShiftR = -1, wbShiftB = 3,
                    dynamicRange = 2, clarity = -2)),
                Recipe(t + 3, "Snapshot 90s", EditParams(
                    sim = 7, grain = 3, colorChrome = 2, wb = 1, wbShiftR = 1, wbShiftB = -2,
                    highlight = 1f, shadow = 2f, sharpness = 1)),
                Recipe(t + 4, "Red Sky B&W", EditParams(
                    sim = 13, grain = 2, monoWC = 2, highlight = 1f, shadow = 2f, sharpness = 2)),
                Recipe(t + 5, "Soft Portrait", EditParams(
                    sim = 2, smoothSkin = 1, wb = 1, wbShiftR = 1, wbShiftB = -1,
                    highlight = -1f, shadow = -1f, color = 1)),
            )
        }
    }
}
