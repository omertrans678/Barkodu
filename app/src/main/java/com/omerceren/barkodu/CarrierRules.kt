package com.omerceren.barkodu

/** Prefix matching follows the existing app rule. Unknown length sets impose no invented business rule. */
class CarrierRules {
    private val prefixes = CargoModel.PREFIXES.map {it.toList()}.toMutableList()
    private val lengths = MutableList(CargoModel.NAMES.size) {emptySet<Int>()}
    fun prefixes(carrier: Int): List<String> = prefixes[carrier].toList()
    fun setPrefixes(carrier: Int, values: List<String>) {
        require(carrier in CargoModel.NAMES.indices)
        val clean = values.map {it.trim().uppercase(java.util.Locale.ROOT)}.filter {it.isNotEmpty()}.distinct()
        require(clean.all {it.matches(Regex("[A-Z0-9]{1,64}"))})
        prefixes[carrier] = clean
    }
    fun setLengths(carrier: Int, values: Set<Int>) {require(values.all {it in 3..64}); lengths[carrier] = values.toSet()}
    fun lengths(carrier: Int) = lengths[carrier].toSet()
    fun matches(code: String, carrier: Int) = carrier in prefixes.indices && prefixes[carrier].any {code.startsWith(it, true)}
    fun validLength(code: String, carrier: Int): Boolean {
        if(carrier !in lengths.indices) return false
        // Confirmed business rule: only DHL/JET 416/417 codes have an exact length of ten.
        if(carrier in 0..1 && (code.startsWith("416") || code.startsWith("417"))) return code.length == 10
        return lengths[carrier].isEmpty() || code.length in lengths[carrier]
    }
    fun classify(code: String, carrier: Int) = if(matches(code, carrier)) carrier else CargoModel.UNKNOWN
}
