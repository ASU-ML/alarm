package com.example.alarm.gateway.rls

object ProjectContext {
	private val holder = ThreadLocal<List<String>>()
	fun set(projects: List<String>) { holder.set(projects) }
	fun get(): List<String> = holder.get() ?: emptyList()
	fun clear() { holder.remove() }
}
