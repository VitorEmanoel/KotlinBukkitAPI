package br.com.devsrsouza.kotlinbukkitapi.utils

import br.com.devsrsouza.kotlinbukkitapi.KotlinBukkitAPI
import br.com.devsrsouza.kotlinbukkitapi.dsl.scheduler.task
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask

typealias OnExipereBlock<T> = (T) -> Unit

private class ExpirationNode<E>(var element: E, val expireTime: Int) {

    var next: ExpirationNode<E>? = null
    var previous: ExpirationNode<E>? = null

    var onExpire: OnExipereBlock<E>? = null
    val startTime: Long = System.currentTimeMillis()
}

class ExpirationList<E>(private val plugin: Plugin = KotlinBukkitAPI.INSTANCE) : Iterable<E> {

    private var head: ExpirationNode<E>? = null
    private var tail: ExpirationNode<E>? = null

    private var task: BukkitTask? = null
    private var emptyCount: Byte = 0

    var size: Int = 0
        private set

    fun isEmpty(): Boolean = size == 0

    fun missingTime(element: E): Int? {
        return getNodeByElement(element)
                ?.let { it.expireTime - ((System.currentTimeMillis() - it.startTime) / 1000) }
                ?.toInt()
    }

    operator fun contains(element: E) = indexOf(element) > -1

    operator fun get(index: Int): E? {
        return getNode(index)?.element
    }

    override operator fun iterator(): Iterator<E> {
        return object : Iterator<E> {
            private val nodeIterator = nodeIterator()

            override fun hasNext() = nodeIterator.hasNext()
            override fun next() = nodeIterator.next().element
        }
    }

    fun indexOf(element: E): Int {
        if (head == null) return -1
        var node = head
        var count = 0
        do {
            if (node?.element === element) {
                return count
            }
            node = node?.next
            count++
        } while (node != null)
        return -1
    }

    fun clear() {
        head = null
        tail = null
        size = 0
    }

    fun add(element: E, expireTime: Int, onExpire: OnExipereBlock<E>? = null) {
        val newNode = ExpirationNode(element, expireTime).also { it.onExpire = onExpire }
        if (head == null) {
            head = newNode
            tail = newNode
        } else {
            tail?.next = newNode
            tail = newNode
        }
        size++
        generateTask()
    }

    fun removeAt(index: Int): E? {
        return getNode(index)?.also {
            removeNode(it)
        }?.element
    }

    fun remove(element: E) = getNodeByElement(element)?.let { true.apply { removeNode(it) } } ?: false

    private fun getNode(index: Int): ExpirationNode<E>? {
        if (index < 0 || index >= size) return null

        val mid = size / 2
        return if (index > mid)
            getFromSpecificSide(index - mid, tail) { it?.previous }
        else
            getFromSpecificSide(index, head) { it?.next }
    }

    private fun getNodeByElement(element: E): ExpirationNode<E>? {
        if (head == null) return null
        var node = head
        do {
            if (node?.element === element) {
                return node
            }
            node = node?.next
        } while (node != null)
        return null
    }

    private inline fun getFromSpecificSide(count: Int, start: ExpirationNode<E>?,
                                           next: (ExpirationNode<E>?) -> ExpirationNode<E>?): ExpirationNode<E> {
        var index = 0
        var current = start
        while (index != count) {
            current = next(current)
            index++
        }
        return current!!
    }

    private fun nodeIterator(): Iterator<ExpirationNode<E>> {
        return object : Iterator<ExpirationNode<E>> {

            private var current = head

            override fun hasNext(): Boolean {
                return current != null
            }

            override fun next(): ExpirationNode<E> {
                val aux = current
                current = current?.next
                return aux!!
            }
        }
    }

    private fun checkTime(current: Long, node: ExpirationNode<E>)
            = ((current - node.startTime) / 1000) - node.expireTime >= 0

    private fun generateTask() {
        if (task == null) {
            task = task(repeatDelay = 20, plugin = plugin) {
                if (isEmpty())
                    emptyCount++
                else {
                    emptyCount = 0
                    val current = System.currentTimeMillis()
                    for (node in nodeIterator()) {
                        if (checkTime(current, node)) {
                            if (node.onExpire != null) node.onExpire?.invoke(node.element)
                            removeNode(node)
                        }
                    }
                }
                if (emptyCount > 9) {
                    cancel()
                    task = null
                }
            }
        }
    }

    private fun removeNode(node: ExpirationNode<E>) {
        if (node === head && node === tail) {
            head = null
            tail = null
        } else if (node === head) {
            head = node.next?.apply { previous = null }
        } else if (node === tail) {
            tail = node.previous?.apply { next = null }
        } else {
            node.apply {
                previous?.next = next
                next?.previous = previous
            }
        }
        size--
    }
}

