package com.omerceren.barkodu

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Uses the legacy Activity preferences file so installed users retain their records and settings. */
class AppPreferences(private val preferences: SharedPreferences) {
    fun boolean(key: String, default: Boolean) = preferences.getBoolean(key, default)
    fun integer(key: String, default: Int) = preferences.getInt(key, default)
    fun put(key: String, value: Boolean) {preferences.edit().putBoolean(key, value).apply()}
    fun put(key: String, value: Int) {preferences.edit().putInt(key, value).apply()}
    fun restore(model: CargoModel, rules: CarrierRules) {
        runCatching {
            val saved = JSONObject(preferences.getString("state", "{}").orEmpty())
            model.selected = saved.optInt("selected", -1).takeIf {it in CargoModel.NAMES.indices} ?: -1
            model.active = saved.optInt("active", model.selected.coerceAtLeast(0)).takeIf {it in CargoModel.NAMES.indices} ?: model.selected.coerceAtLeast(0)
            val rows = saved.optJSONArray("items") ?: JSONArray()
            for(i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val code = CargoModel.normalize(row.optString("code"))
                val carrier = row.optInt("cargo", 4)
                val quantity = row.optInt("quantity", 1)
                if(CargoModel.valid(code) && carrier in 0..4 && quantity > 0) repeat(quantity) {unit ->
                    model.items.add(CargoModel.Item(code, if(carrier == 4) model.selected.coerceAtLeast(0) else carrier).also {
                        if(quantity == 1 && row.optLong("id", 0) > 0) it.id = row.getLong("id")
                    })
                }
            }
        }
        runCatching {
            val data = JSONObject(preferences.getString("carrierRules", "{}").orEmpty())
            CargoModel.NAMES.indices.forEach {carrier ->
                data.optJSONArray(carrier.toString())?.let {values -> rules.setPrefixes(carrier, (0 until values.length()).map {values.getString(it)})}
            }
        }
    }
    fun save(model: CargoModel) {
        val rows = JSONArray()
        model.items.forEach {item -> rows.put(JSONObject().put("code", item.code).put("cargo", item.cargo).put("quantity", 1).put("id", item.id))}
        val data = JSONObject().put("selected", model.selected).put("active", model.active).put("items", rows)
        preferences.edit().putString("state", data.toString()).apply()
    }
    fun save(rules: CarrierRules) {
        val data = JSONObject()
        CargoModel.NAMES.indices.forEach {data.put(it.toString(), JSONArray(rules.prefixes(it)))}
        preferences.edit().putString("carrierRules", data.toString()).apply()
    }
}
