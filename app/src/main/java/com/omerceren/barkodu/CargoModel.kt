package com.omerceren.barkodu

import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/** Domain list: each physical entry has an independent ID, including explicitly confirmed copies. */
class CargoModel {
    companion object {
        val NAMES = arrayOf("DHL", "JET", "TEX", "ARAS")
        val PREFIXES = arrayOf(arrayOf("416", "HTS"), arrayOf("416", "H0", "HTS"), arrayOf("73"), arrayOf("72", "P0", "A0", "FL0"))
        const val UNKNOWN = 4
        private val barcodePattern=Regex("[A-Za-z0-9]{3,64}")
        fun normalize(code: String?) = code?.trim().orEmpty()
        fun valid(code: String?) = barcodePattern.matches(normalize(code))
        fun classify(code: String, selected: Int): Int =
            if(selected in PREFIXES.indices && PREFIXES[selected].any {normalize(code).startsWith(it, ignoreCase=true)}) selected else UNKNOWN
    }
    class Item(var code: String, var cargo: Int, var quantity: Int = 1) {
        companion object {private val nextId = AtomicLong()}
        var id = nextId.incrementAndGet()
            set(value) {field = value; var current=nextId.get(); while(value>current&&!nextId.compareAndSet(current,value))current=nextId.get()}
        fun copy() = Item(code, cargo, quantity).also {it.id = id}
    }
    private sealed interface Change {
        data class Added(val id: Long, val active: Int): Change
        data class Removed(val rows: List<Pair<Int, Item>>, val active: Int): Change
    }
    var selected = -1
    var active = 0
    val items = ArrayList<Item>()
    private val history = ArrayDeque<Change>()
    private fun push(change: Change) {history.addLast(change); if(history.size > 100) history.removeFirst()}
    fun add(raw: String) = addForCarrier(raw, selected)
    fun addForCarrier(raw: String, carrier: Int): Item {
        require(carrier in NAMES.indices && valid(raw))
        val next = Item(normalize(raw), carrier)
        push(Change.Added(next.id, active)); items.add(0, next); active = carrier
        return next
    }
    fun remove(item: Item) = removeMany(setOf(item.id))
    fun removeMany(ids: Set<Long>): List<Pair<Int, Item>> {
        val rows = items.mapIndexedNotNull {index, item -> if(item.id in ids) index to item.copy() else null}
        if(rows.isNotEmpty()) {push(Change.Removed(rows, active)); items.removeAll {it.id in ids}}
        return rows
    }
    fun restore(item: Item, index: Int) {if(items.none {it.id == item.id}) items.add(index.coerceIn(0, items.size), item.copy())}
    fun restore(rows: List<Pair<Int, Item>>) {rows.sortedBy {it.first}.forEach {(index, item) -> restore(item, index)}}
    fun clear() {removeMany(items.map {it.id}.toSet())}
    fun canUndo() = history.isNotEmpty()
    fun undo(): Boolean {
        val change = history.pollLast() ?: return false
        when(change) {
            is Change.Added -> {items.removeAll {it.id == change.id}; active = change.active}
            is Change.Removed -> {restore(change.rows); active = change.active}
        }
        return true
    }
    fun visible() = items.filter {it.cargo == active}
    fun count(cargo: Int) = items.count {it.cargo == cargo}
    fun text() = NAMES.indices.filter {count(it) != 0}.joinToString("\n\n") {cargo ->
        "[${NAMES[cargo]}]\n" + items.filter {it.cargo == cargo}.joinToString("\n", postfix="\n") {it.code}
    }
}
