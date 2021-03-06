package br.com.devsrsouza.kotlinbukkitapi.dsl.menu

import br.com.devsrsouza.kotlinbukkitapi.KotlinBukkitAPI
import br.com.devsrsouza.kotlinbukkitapi.dsl.scheduler.*
import br.com.devsrsouza.kotlinbukkitapi.extensions.registerEvents
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerPickupItemEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask

fun createMenu(displayname: String, lines: Int = 3, cancel: Boolean = false, block: Menu.() -> Unit) =
    Menu(displayname, lines, cancel).apply { block() }.also { MenuController.registerMenu(it) }

typealias MenuUpdatetEvent = MenuUpdate.() -> Unit
typealias MenuCloseEvent = MenuClose.() -> Unit
typealias MoveToMenuEvent = MoveToMenu.() -> Unit
typealias SlotClickEvent = SlotClick.() -> Unit
typealias SlotRenderItemEvent = SlotRenderItem.() -> Unit
typealias SlotUpdateEvent = SlotUpdate.() -> Unit
typealias MoveToSlotEvent = MoveToSlot.() -> Unit

open class Menu(var title: String, var lines: Int, var cancel: Boolean) {

    private var task: BukkitTask? = null
    var updateDelay: Long = 0
        set(value) {
            task?.cancel()
            task = null
            if (value > 0) task = task(repeatDelay = value) { update() }
        }

    val viewers = mutableMapOf<Player, Inventory>()
    val slots = mutableMapOf<Int, Slot>()
    val data = mutableMapOf<String, Any>()
    val baseSlot = Slot(this, -1)

    private var update: MenuUpdatetEvent? = null
    var close: MenuCloseEvent? = null
    var moveToMenu: MoveToMenuEvent? = null

    fun onUpdate(update: MenuUpdatetEvent) { this.update = update }
    fun onClose(close: MenuCloseEvent) { this.close = close }
    fun onMoveToMenu(moveToMenu: MoveToMenuEvent) { this.moveToMenu = moveToMenu }

    fun slot(line: Int, slot: Int, block: Slot.() -> Unit) : Slot {
        val slotExactly = ((line*9)-9)+slot
        return slot(slotExactly, block)
    }
    fun slot(slot: Int, block: Slot.() -> Unit) : Slot {
        val generetedSlot = baseSlot.clone(slot).apply(block)
        slots.put(slot, generetedSlot)
        return generetedSlot
    }

    fun update(vararg players: Player = viewers.keys.toTypedArray()) {
        for (player in players) {
            viewers.entries.forEach {
                if(it.key == player) {
                    if(update !== null) {
                        val menuUpdate = MenuUpdate(it.key, it.value.title)
                        update?.invoke(menuUpdate)

                        /*
                          todo change menu title
                          menuUpdate.title
                        */
                    }

                    for(i in 0 until (lines*9)) {
                        var slot = slots[i+1] ?: baseSlot
                        updateSlot(player, slot, it.value, i)
                    }
                }
            }
        }
    }

    fun updateSlot(slot: Slot, vararg players: Player = viewers.keys.toTypedArray()) {
        if (slot.update === null) return
        if (slot === baseSlot) {
            slots.forEach { pos, slot ->
                for (i in 0 until (lines * 9)) {
                    if (slots[i + 1] === null) {
                        players.forEach { player ->
                            viewers.entries.forEach {
                                updateSlot(player, slot, it.value, pos - 1)
                            }
                        }
                    }
                }
            }
        } else {
            slots.forEach { pos, slot ->
                if (slot === updateSlot@ slot) {
                    players.forEach { player ->
                        viewers.entries.forEach {
                            updateSlot(player, slot, it.value, pos - 1)
                        }
                    }
                }
            }
        }
    }

    private fun updateSlot(player: Player, slot: Slot, inventory: Inventory, pos: Int) {
        val slotUpdate = SlotUpdate(
            player, slot.item?.clone(),
            inventory.getItem(pos)?.takeUnless { it.type == Material.AIR })
        slot.update?.invoke(slotUpdate)
        inventory.setItem(pos, slotUpdate.showingItem)
    }

    open fun openToPlayer(player: Player) {
        val menuLenght = lines*9
        val inv = Bukkit.createInventory(player, menuLenght, title)

        for(i in 0 until menuLenght) {
            var slot = slots[i+1] ?: baseSlot
            if (slot.render != null) {
                val item = slot.item?.clone()
                val render = SlotRenderItem(player, item)
                slot.render?.invoke(render)
                inv.setItem(i, render.renderItem)
            } else {
                val item = slot.item?.clone()
                inv.setItem(i, item)
            }
        }

        viewers.put(player, inv)
        player.openInventory(inv)
    }
}

class Slot(private val menu: Menu, val pos: Int, var item: ItemStack? = null) {

    var click: SlotClickEvent? = null
    var render: SlotRenderItemEvent? = null
    var update: SlotUpdateEvent? = null
    var moveToSlot: MoveToSlotEvent? = null

    fun onClick(click: SlotClickEvent?) {
        this.click = click
    }

    fun onRender(render: SlotRenderItemEvent?) {
        this.render = render
    }

    fun onUpdate(update: SlotUpdateEvent) {
        this.update = update
    }

    fun onMoveToSlot(moveToSlot: MoveToSlotEvent) {
        this.moveToSlot = moveToSlot
    }

    fun clone(pos: Int) = Slot(menu, pos, item).apply {
        this@Slot.render = render
        this@Slot.click = click
        this@Slot.update = update
        this@Slot.moveToSlot = moveToSlot
    }
}

abstract class MenuInventory(protected val inventory: Inventory) {
    var Slot.showingItem
        get() = inventory.getItem(pos - 1)?.takeUnless { it.type == Material.AIR }
        set(value) = inventory.setItem(pos - 1, value)

    fun item(line: Int, slot: Int): ItemStack? {
        val slotExactly = ((line * 9) - 9) + slot
        return item(slot)
    }

    fun item(slot: Int): ItemStack? = inventory.getItem(slot - 1)

    fun line(line: Int): List<ItemStack?> = (((line * 9) - 9)+1..line * 9).map { item(it) }
}

open class MenuInteract(protected val menu: Menu, val player: Player, var cancel: Boolean,
                        inventory: Inventory) : MenuInventory(inventory) {
    fun updateToPlayer() {
        menu.update(player)
    }
}

class SlotClick(menu: Menu, player: Player, cancel: Boolean, inventory: Inventory,
                private val slot: Slot,
                val clickType: ClickType,
                val inventoryAction: InventoryAction,
                val itemClicked: ItemStack?,
                val itemCursor: ItemStack?,
                val hotbarKey: Int) : MenuInteract(menu, player, cancel, inventory) {

    fun updateSlotToPlayer() {
        menu.updateSlot(slot, player)
    }

    fun updateSlot() {
        menu.updateSlot(slot)
    }

    fun close() = player.closeInventory()
}

class MoveToSlot(val player: Player, var cancel: Boolean, val item: ItemStack?)

class SlotRenderItem(val player: Player, var renderItem: ItemStack?)
class SlotUpdate(val player: Player, val templateItem: ItemStack?, var showingItem: ItemStack?)

class MenuUpdate(val player: Player, var title: String)
class MenuClose(val player: Player, inventory: Inventory) : MenuInventory(inventory)
class MoveToMenu(inventory: Inventory, val player: Player,
                 var cancel: Boolean, targetSlot: Int, val item: ItemStack?) : MenuInventory(inventory) {

    private var getted = false

    var targetSlot: Int = targetSlot
        set(value) {
            field = value
            getted = false
        }

    var targetCurrentItem: ItemStack? = null
        set(value) {
            if(!getted) getted=true
            field = value
        }
        get() {
            if(!getted) {
                field = inventory.getItem(targetSlot - 1)?.takeUnless { it.type == Material.AIR }
                getted = true
            }
            return field
        }
}

object MenuController : Listener {

    private val menus = mutableListOf<Menu>()

    init {
        KotlinBukkitAPI.INSTANCE.registerEvents(this)
    }

    fun registerMenu(menu: Menu) = if (!menus.contains(menu)) {
        menus.add(menu)
        true
    } else false


    fun unregisterMenu(menu: Menu) {
        menus.remove(menu)
    }

    private fun getMenuFromPlayer(player: Player) : Menu? {
        for(menu in menus)
            for(viewer in menu.viewers)
                if(viewer.key.name == player.name) return menu
        return null
    }

    @EventHandler(ignoreCancelled = true)
    fun clickInventoryEvent(event: InventoryClickEvent) {
        if(event.view.type == InventoryType.CHEST) {
            val player = event.whoClicked as Player
            val menu = getMenuFromPlayer(player)
            if(menu != null) {
                if(event.rawSlot == event.slot) {
                    val clickedSlot = event.slot + 1
                    val slot = menu.slots.get(clickedSlot) ?: menu.baseSlot
                    if (slot.click != null) {
                        val slotClick = SlotClick(menu, player,
                            menu.cancel, event.inventory, slot, event.click, event.action,
                            event.currentItem, event.cursor, event.hotbarButton)
                        slot.click?.invoke(slotClick)
                        if (slotClick.cancel) event.isCancelled = true
                    } else {
                        if (menu.cancel) event.isCancelled = true
                    }
                }else{
                    val emptySlot = event.inventory.firstEmpty()
                    if(event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY && emptySlot > -1) {
                        val realEmptySlot = emptySlot + 1
                        val slot = menu.slots.get(realEmptySlot) ?: menu.baseSlot
                        val auxCurrentItem = event.currentItem

                        if(slot.moveToSlot != null) {
                            val moveToSlot = MoveToSlot(player, menu.cancel, auxCurrentItem)
                            slot.moveToSlot?.invoke(moveToSlot)

                            if(moveToSlot.cancel) event.isCancelled = true
                        } else if(menu.moveToMenu != null) {
                            val moveToMenu = MoveToMenu(event.inventory, player,
                                    menu.cancel, realEmptySlot, auxCurrentItem)
                            val auxTargetItem = moveToMenu.targetCurrentItem
                            menu.moveToMenu?.invoke(moveToMenu)

                            val moveItem = moveToMenu.item
                            val moveTargetItem = moveToMenu.targetCurrentItem
                            val exactTargetSlot = moveToMenu.targetSlot - 1

                            if(moveToMenu.cancel) {
                                event.isCancelled = true
                                if(moveItem !== auxCurrentItem) event.currentItem = moveToMenu.item
                                if(moveTargetItem !== auxTargetItem) event.inventory.setItem(exactTargetSlot, moveTargetItem)
                            } else {
                                if(exactTargetSlot != emptySlot) {
                                    //val ref = event.currentItem
                                    event.isCancelled = true
                                    event.currentItem = moveTargetItem
                                    event.inventory.setItem(exactTargetSlot, moveItem)
                                }
                            }
                        }else if (menu.cancel) event.isCancelled = true
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun clickInteractEvent(event: InventoryDragEvent) {
        if (event.view.type == InventoryType.CHEST) {
            val player = event.whoClicked as Player
            val menu = getMenuFromPlayer(player)
            if (menu != null) {
                var pass = 0
                for(i in event.inventorySlots) {
                    for(j in event.rawSlots) {
                        if(i == j) {
                            pass++
                            break
                        }
                    }
                }
                if(pass > 0) event.isCancelled = true
            }
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        if(event.view.type == InventoryType.CHEST) {
            val player = event.player as Player
            val menu = getMenuFromPlayer(player)
            if(menu != null) {
                menu.close?.invoke(MenuClose(player, event.inventory))
                menu.viewers.remove(player)
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onPickupItemEvent(event: PlayerPickupItemEvent) {
        if (getMenuFromPlayer(event.player) != null) event.isCancelled = true
    }
}