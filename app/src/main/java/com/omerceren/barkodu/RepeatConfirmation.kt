package com.omerceren.barkodu

/** A consumed ID cannot act again; closing a warning leaves the next event available. */
class RepeatConfirmation {
    data class Event(val id: Long, val code: String, val carrier: Int)
    private var nextId = 0L
    private var pending: Event? = null
    fun get() = pending
    fun offer(code: String, carrier: Int): Boolean {
        if(pending != null) return false
        pending = Event(++nextId, code, carrier)
        return true
    }
    fun consume(id: Long): Event? = pending?.takeIf {it.id == id}?.also {pending = null}
    fun dismiss() {pending = null}
}
