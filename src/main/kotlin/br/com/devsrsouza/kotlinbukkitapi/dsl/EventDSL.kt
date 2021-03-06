package br.com.devsrsouza.kotlinbukkitapi.dsl.event

import br.com.devsrsouza.kotlinbukkitapi.KotlinBukkitAPI
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin

inline fun <reified T : Event> Listener.event(priority: EventPriority = EventPriority.NORMAL,
                                              ignoreCancelled: Boolean = true,
                                              plugin: Plugin = KotlinBukkitAPI.INSTANCE,
                                              crossinline block: T.() -> Unit) {
    Bukkit.getServer().pluginManager.registerEvent(
            T::class.java,
            this,
            priority,
            { _, event ->
                (event as T).block()
            },
            plugin,
            ignoreCancelled
    )
}

fun events(block: Listener.() -> Unit) = object : Listener {}.apply(block)

fun Listener.unregisterAll() {
    HandlerList.unregisterAll(this)
}

fun Listener.registerEvents(plugin: Plugin = KotlinBukkitAPI.INSTANCE)
        = plugin.server.pluginManager.registerEvents(this, plugin)