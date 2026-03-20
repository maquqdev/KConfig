package club.skidware.kconfig.bukkit

import club.skidware.kconfig.YamlConfigManager
import club.skidware.kconfig.serializer.TypeSerializer
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.util.Vector

/**
 * Provides modern [TypeSerializer] implementations for common Bukkit/Paper types.
 *
 * All serializers use the **Paper 1.21+ Component API** and **MiniMessage** format
 * for text-based fields. No deprecated Bukkit methods are used.
 *
 * Call [registerAll] during plugin initialization to register all serializers.
 *
 * **Supported types:**
 * - [Location] -- `{world, x, y, z, pitch?, yaw?}`
 * - [Vector] -- `{x, y, z}`
 * - [Color] -- hex string `"#RRGGBB"` (backward-compatible with `{red, green, blue}` maps)
 * - [ItemStack] -- structured YAML with MiniMessage displayName/lore, customModelData,
 *   enchantments, itemFlags, unbreakable, damage, leatherColor
 *
 * Example:
 * ```kotlin
 * override fun onEnable() {
 *     BukkitSerializers.registerAll(YamlConfigManager)
 *     val config = YamlConfigManager.load<MyConfig>(dataFolder.resolve("config.yml"))
 * }
 * ```
 *
 * @see YamlConfigManager.registerSerializer
 * @see TypeSerializer
 * @since 1.0
 */
object BukkitSerializers {

    /**
     * Registers all Bukkit type serializers with the given [manager].
     *
     * @param manager The [YamlConfigManager] instance to register serializers with.
     * @since 1.0
     */
    fun registerAll(manager: YamlConfigManager) {
        manager.registerSerializer(Location::class, LocationSerializer)
        manager.registerSerializer(Vector::class, VectorSerializer)
        manager.registerSerializer(Color::class, ColorSerializer)
        manager.registerSerializer(ItemStack::class, ItemStackSerializer)
    }
}

/**
 * Serializer for Bukkit [Location] objects.
 *
 * **YAML format:**
 * ```yaml
 * spawn:
 *   world: world
 *   x: 100.5
 *   y: 64.0
 *   z: -200.3
 *   pitch: 1.5
 *   yaw: 90.0
 * ```
 *
 * Pitch and yaw are omitted when they are `0`.
 *
 * @since 1.0
 */
object LocationSerializer : TypeSerializer<Location> {

    override fun serialize(value: Location): Any {
        val map = LinkedHashMap<String, Any?>()
        map["world"] = value.world?.name ?: ""
        map["x"] = value.x
        map["y"] = value.y
        map["z"] = value.z
        if (value.pitch != 0f) map["pitch"] = value.pitch.toDouble()
        if (value.yaw != 0f) map["yaw"] = value.yaw.toDouble()
        return map
    }

    override fun deserialize(raw: Any): Location {
        val map = raw as? Map<*, *> ?: throw IllegalArgumentException("Expected Map for Location")
        val worldName = map["world"]?.toString() ?: ""
        val world = if (worldName.isNotEmpty()) Bukkit.getWorld(worldName) else null
        val x = (map["x"] as? Number)?.toDouble() ?: 0.0
        val y = (map["y"] as? Number)?.toDouble() ?: 0.0
        val z = (map["z"] as? Number)?.toDouble() ?: 0.0
        val pitch = (map["pitch"] as? Number)?.toFloat() ?: 0f
        val yaw = (map["yaw"] as? Number)?.toFloat() ?: 0f
        return Location(world, x, y, z, yaw, pitch)
    }
}

/**
 * Serializer for Bukkit [Vector] objects.
 *
 * **YAML format:**
 * ```yaml
 * velocity:
 *   x: 1.0
 *   y: 0.5
 *   z: -1.0
 * ```
 *
 * @since 1.0
 */
object VectorSerializer : TypeSerializer<Vector> {

    override fun serialize(value: Vector): Any {
        val map = LinkedHashMap<String, Any>()
        map["x"] = value.x
        map["y"] = value.y
        map["z"] = value.z
        return map
    }

    override fun deserialize(raw: Any): Vector {
        val map = raw as? Map<*, *> ?: throw IllegalArgumentException("Expected Map for Vector")
        val x = (map["x"] as? Number)?.toDouble() ?: 0.0
        val y = (map["y"] as? Number)?.toDouble() ?: 0.0
        val z = (map["z"] as? Number)?.toDouble() ?: 0.0
        return Vector(x, y, z)
    }
}

/**
 * Serializer for Bukkit [Color] objects.
 *
 * Stores colors as **hex strings** (`"#RRGGBB"`) for clean, readable YAML.
 * Also accepts the legacy `{red, green, blue}` map format for backward compatibility.
 *
 * **YAML format:**
 * ```yaml
 * armorColor: "#FF5500"
 * particleColor: "#00AAFF"
 * ```
 *
 * **Legacy format (also accepted):**
 * ```yaml
 * armorColor:
 *   red: 255
 *   green: 85
 *   blue: 0
 * ```
 *
 * @since 1.0
 */
object ColorSerializer : TypeSerializer<Color> {

    override fun serialize(value: Color): Any {
        return "#%02X%02X%02X".format(value.red, value.green, value.blue)
    }

    override fun deserialize(raw: Any): Color {
        return when (raw) {
            is String -> {
                val hex = raw.removePrefix("#")
                require(hex.length == 6) { "Invalid hex color: '$raw'. Expected format: #RRGGBB" }
                val rgb = hex.toInt(16)
                Color.fromRGB((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
            }
            is Map<*, *> -> {
                val red = (raw["red"] as? Number)?.toInt() ?: 0
                val green = (raw["green"] as? Number)?.toInt() ?: 0
                val blue = (raw["blue"] as? Number)?.toInt() ?: 0
                Color.fromRGB(red, green, blue)
            }
            else -> throw IllegalArgumentException("Expected hex string or RGB map for Color, got: ${raw::class.simpleName}")
        }
    }
}

/**
 * Modern [ItemStack] serializer using the **Paper 1.21+ Component API**.
 *
 * Produces clean, human-readable YAML with full support for:
 * - **Display name** in MiniMessage format (colors, formatting, hex colors)
 * - **Lore** as a list of MiniMessage strings (multi-line, colored)
 * - **Custom model data** for resource packs
 * - **Leather armor color** as hex string
 * - **Enchantments** as a map of `name: level`
 * - **Item flags** (HIDE_ENCHANTS, HIDE_ATTRIBUTES, etc.)
 * - **Unbreakable** flag
 * - **Damage/durability** for damageable items
 *
 * No deprecated Bukkit methods are used. All text is stored as MiniMessage strings
 * and converted to Adventure Components on deserialization.
 *
 * **YAML format:**
 * ```yaml
 * reward:
 *   type: DIAMOND_SWORD
 *   amount: 1
 *   displayName: "<red><bold>Legendary Blade</bold></red>"
 *   lore:
 *     - "<gray>Forged in the depths</gray>"
 *     - ""
 *     - "<gold>+10 Attack Damage</gold>"
 *     - "<green>Right-click to activate</green>"
 *   customModelData: 42
 *   enchantments:
 *     sharpness: 5
 *     unbreaking: 3
 *     fire_aspect: 2
 *   itemFlags:
 *     - HIDE_ENCHANTS
 *     - HIDE_ATTRIBUTES
 *   unbreakable: true
 *   damage: 0
 *
 * helmet:
 *   type: LEATHER_HELMET
 *   displayName: "<#FF5500>Orange Helmet</#FF5500>"
 *   leatherColor: "#FF5500"
 *   unbreakable: true
 * ```
 *
 * @since 1.0
 */
object ItemStackSerializer : TypeSerializer<ItemStack> {

    private val miniMessage = MiniMessage.miniMessage()

    override fun serialize(value: ItemStack): Any {
        val map = LinkedHashMap<String, Any?>()
        map["type"] = value.type.name
        if (value.amount != 1) {
            map["amount"] = value.amount
        }

        val meta = value.itemMeta ?: return map
        this.serializeTextFields(meta, map)
        this.serializeGameplayFields(meta, map)
        this.serializeVisualFields(meta, map)
        return map
    }

    override fun deserialize(raw: Any): ItemStack {
        val map = raw as? Map<*, *>
            ?: throw IllegalArgumentException("Expected Map for ItemStack, got: ${raw::class.simpleName}")

        val item = this.createBaseItem(map)
        item.editMeta { meta ->
            this.applyTextFields(meta, map)
            this.applyGameplayFields(meta, map)
            this.applyVisualFields(meta, map)
        }
        return item
    }

    private fun serializeTextFields(meta: ItemMeta, map: MutableMap<String, Any?>) {
        val displayName = meta.displayName()
        if (displayName != null) {
            map["displayName"] = this.miniMessage.serialize(displayName)
        }

        val lore = meta.lore()
        if (lore != null && lore.isNotEmpty()) {
            map["lore"] = lore.map { this.miniMessage.serialize(it) }
        }
    }

    private fun serializeGameplayFields(meta: ItemMeta, map: MutableMap<String, Any?>) {
        this.serializeEnchantments(meta, map)
        this.serializeItemFlags(meta, map)
        this.serializeUnbreakable(meta, map)
        this.serializeDamage(meta, map)
    }

    private fun serializeEnchantments(meta: ItemMeta, map: MutableMap<String, Any?>) {
        val enchants = meta.enchants
        if (enchants.isNotEmpty()) {
            map["enchantments"] = enchants.entries.associate { (enchantment, level) ->
                enchantment.key.key to level
            }
        }
    }

    private fun serializeItemFlags(meta: ItemMeta, map: MutableMap<String, Any?>) {
        val flags = meta.itemFlags
        if (flags.isNotEmpty()) {
            map["itemFlags"] = flags.map { it.name }
        }
    }

    private fun serializeUnbreakable(meta: ItemMeta, map: MutableMap<String, Any?>) {
        if (meta.isUnbreakable) {
            map["unbreakable"] = true
        }
    }

    private fun serializeDamage(meta: ItemMeta, map: MutableMap<String, Any?>) {
        if (meta is Damageable && meta.hasDamage()) {
            map["damage"] = meta.damage
        }
    }

    private fun serializeVisualFields(meta: ItemMeta, map: MutableMap<String, Any?>) {
        if (meta.hasCustomModelData()) {
            map["customModelData"] = meta.customModelData
        }

        if (meta is LeatherArmorMeta) {
            val color = meta.color
            val defaultColor = Bukkit.getItemFactory().getDefaultLeatherColor()
            if (color != defaultColor) {
                map["leatherColor"] = "#%02X%02X%02X".format(color.red, color.green, color.blue)
            }
        }
    }

    private fun createBaseItem(map: Map<*, *>): ItemStack {
        val typeName = map["type"]?.toString()
            ?: throw IllegalArgumentException("ItemStack requires 'type' field")
        val material = Material.matchMaterial(typeName)
            ?: throw IllegalArgumentException("Unknown material: '$typeName'")
        val amount = (map["amount"] as? Number)?.toInt() ?: 1
        return ItemStack(material, amount)
    }

    private fun applyTextFields(meta: ItemMeta, map: Map<*, *>) {
        val displayNameStr = map["displayName"]?.toString()
        if (displayNameStr != null) {
            meta.displayName(this.miniMessage.deserialize(displayNameStr))
        }

        val loreRaw = map["lore"]
        if (loreRaw is List<*>) {
            meta.lore(loreRaw.filterNotNull().map { this.miniMessage.deserialize(it.toString()) })
        }
    }

    private fun applyGameplayFields(meta: ItemMeta, map: Map<*, *>) {
        this.applyEnchantments(meta, map)
        this.applyItemFlags(meta, map)
        this.applyUnbreakable(meta, map)
        this.applyDamage(meta, map)
    }

    private fun applyEnchantments(meta: ItemMeta, map: Map<*, *>) {
        val enchantmentsRaw = map["enchantments"]
        if (enchantmentsRaw is Map<*, *>) {
            val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
            for ((key, level) in enchantmentsRaw) {
                val enchantment = registry.get(NamespacedKey.minecraft(key.toString().lowercase()))
                if (enchantment != null && level is Number) {
                    meta.addEnchant(enchantment, level.toInt(), true)
                }
            }
        }
    }

    private fun applyItemFlags(meta: ItemMeta, map: Map<*, *>) {
        val flagsRaw = map["itemFlags"]
        if (flagsRaw is List<*>) {
            for (flagName in flagsRaw) {
                runCatching { ItemFlag.valueOf(flagName.toString().uppercase()) }
                    .onSuccess { meta.addItemFlags(it) }
            }
        }
    }

    private fun applyUnbreakable(meta: ItemMeta, map: Map<*, *>) {
        if (map["unbreakable"] == true || map["unbreakable"]?.toString().equals("true", ignoreCase = true)) {
            meta.isUnbreakable = true
        }
    }

    private fun applyDamage(meta: ItemMeta, map: Map<*, *>) {
        val damage = (map["damage"] as? Number)?.toInt()
        if (damage != null && meta is Damageable) {
            meta.damage = damage
        }
    }

    private fun applyVisualFields(meta: ItemMeta, map: Map<*, *>) {
        val customModelData = (map["customModelData"] as? Number)?.toInt()
        if (customModelData != null) {
            meta.setCustomModelData(customModelData)
        }

        val leatherColor = map["leatherColor"]?.toString()
        if (leatherColor != null && meta is LeatherArmorMeta) {
            meta.setColor(parseHexColor(leatherColor))
        }
    }

    private fun parseHexColor(hex: String): Color {
        val clean = hex.removePrefix("#")
        require(clean.length == 6) { "Invalid hex color: '$hex'. Expected format: #RRGGBB" }
        val rgb = clean.toInt(16)
        return Color.fromRGB((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
    }
}
