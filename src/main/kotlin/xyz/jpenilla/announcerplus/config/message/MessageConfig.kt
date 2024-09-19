/*
 * This file is part of AnnouncerPlus, licensed under the MIT License.
 *
 * Copyright (c) 2020-2024 Jason Penilla
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package xyz.jpenilla.announcerplus.config.message

import net.kyori.adventure.key.Key.key
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.Sound.sound
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.permissions.PermissionDefault
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.NodePath.path
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment
import org.spongepowered.configurate.objectmapping.meta.Setting
import org.spongepowered.configurate.serialize.SerializationException
import org.spongepowered.configurate.serialize.TypeSerializer
import org.spongepowered.configurate.transformation.ConfigurationTransformation
import org.spongepowered.configurate.transformation.TransformAction
import xyz.jpenilla.announcerplus.AnnouncerPlus
import xyz.jpenilla.announcerplus.config.ConfigManager
import xyz.jpenilla.announcerplus.config.ConfigurationUpgrader
import xyz.jpenilla.announcerplus.config.NamedConfigurationFactory
import xyz.jpenilla.announcerplus.config.SelfSavable
import xyz.jpenilla.announcerplus.config.Transformations
import xyz.jpenilla.announcerplus.config.visitor.DuplicateCommentRemovingVisitor
import xyz.jpenilla.announcerplus.util.TaskHandle
import xyz.jpenilla.announcerplus.util.addDefaultPermission
import xyz.jpenilla.announcerplus.util.asyncTimer
import xyz.jpenilla.announcerplus.util.dispatchCommandAsConsole
import xyz.jpenilla.announcerplus.util.miniMessage
import xyz.jpenilla.announcerplus.util.playSounds
import xyz.jpenilla.announcerplus.util.schedule
import xyz.jpenilla.announcerplus.util.scheduleGlobal
import java.lang.reflect.Type
import kotlin.collections.component1
import kotlin.collections.component2

@ConfigSerializable
class MessageConfig : SelfSavable<CommentedConfigurationNode>, KoinComponent {

  @Setting
  @Comment("The list of messages for a config")
  val messages = arrayListOf(
    Message {
      messages("<center><rainbow>Test AnnouncerPlus broadcast!")
    },
    Message {
      bossBar = BossBarSettings(
        25,
        "{animate:flash:YELLOW:PURPLE:40}",
        "<green>-| <white>{animate:scrolltext:Hello this is an example Boss Bar announcement:20:3}</white> |-"
      )
    },
    Message {
      messages(
        "{prefix1} 1. <gradient:blue:green:blue>Multi-line test AnnouncerPlus broadcast",
        "{prefix1} 2. <gradient:red:gold:red>Line number two of three",
        "{prefix1} 3. <bold><rainbow>this is the last line (line 3)"
      )
      toast = ToastSettings(
        Material.NETHER_STAR,
        ToastSettings.FrameType.CHALLENGE,
        "<gradient:green:blue><bold><italic>AnnouncerPlus",
        "<rainbow>This is a Toast message!"
      )
    },
    Message {
      messages("{prefix1} Test <gradient:blue:aqua>AnnouncerPlus</gradient> broadcast with sound<green>!")
      sounds(
        sound().type(key("minecraft:entity.strider.happy")).source(Sound.Source.MASTER).volume(1.0f).pitch(1.0f).seed(1234).build(),
        sound(key("minecraft:entity.villager.ambient"), Sound.Source.MASTER, 1.0f, 1.0f),
        sound(key("minecraft:block.note_block.cow_bell"), Sound.Source.MASTER, 1.0f, 1.0f)
      )
    },
    Message {
      messages("{prefix1} Use <click:run_command:/ap about><hover:show_text:'<rainbow>Click to run!'><rainbow>/ap about</rainbow></hover></click> to check the plugin version")
      actionBar = ActionBarSettings(
        true,
        15,
        "<{animate:randomcolor:pulse:25}>-| <white>{animate:scrolltext:Hello there this is some very long text being displayed in a scrolling window!! =):20:3}</white> |-"
      )
    },
    Message {
      messages("<bold><italic>Hello,</italic></bold> {nick} {prefix1} {r}!!!!!!!!!{rc}")
      title = TitleSettings(
        1,
        13,
        2,
        "<gradient:green:blue:green:{animate:scroll:0.1}>||||||||||||||||||||||||||||||||||||||||||||",
        "<{animate:pulse:red:blue:10}>{animate:type:This is a test... typing...:6}"
      )
    },
    Message {
      messages("<center><gradient:red:blue>Centered text Example")
      bossBar = BossBarSettings(
        25,
        "PINK",
        "<bold>This is an example <italic><gradient:blue:light_purple>Boss Bar"
      )
    }
  )

  @Setting("every-broadcast-commands")
  @Comment("These commands will run as console once each interval\n  Example: \"broadcast This is a test\"")
  val commands = ArrayList<String>()

  @Setting("every-broadcast-per-player-commands")
  @Comment("These commands will run as console once per player each interval\n  Example: \"minecraft:give %player_name% dirt\"")
  val perPlayerCommands = ArrayList<String>()

  @Setting("every-broadcast-as-player-commands")
  @Comment("These commands will run once per player each interval, as the player\n  Example: \"ap about\"")
  val asPlayerCommands = ArrayList<String>()

  @Setting("interval-time")
  @Comment("The amount of time used for the interval. Parsing is quite flexible for durations of minutes, hours, or seconds. '3 minutes', '10m', '30 sec', and '2hrs' are some examples of valid values.")
  var interval = SimpleDuration(3, TimeUnit.MINUTES, "3 minutes")

  @Setting("startup-delay")
  @Comment("Delay before this broadcast starts it's interval at server startup/config reload. Useful to offset configs from each other. Same format as interval-time.")
  var initialDelay = SimpleDuration.ZERO

  @Setting("random-message-order")
  @Comment("Should the messages be sent in order of the config or in random order")
  var randomOrder = false

  @Comment("Should duplicate comments be removed from this config?")
  var removeDuplicateComments = true

  @Comment("Should disabled/inactive message elements be removed from this config?")
  var removeDisabledMessageElements = false

  private fun removeDisabledMessageElements(node: CommentedConfigurationNode) {
    for ((i, message) in node.node("messages").childrenList().withIndex()) {
      if (i == 0) continue

      mapOf(
        ActionBarSettings::class to "action-bar",
        BossBarSettings::class to "boss-bar",
        TitleSettings::class to "title",
        ToastSettings::class to "toast"
      ).forEach { (type, childName) ->
        val element = message.node(childName).get(type.java) ?: return@forEach
        if (!element.isEnabled()) {
          message.removeChild(childName)
        }
      }

      listOf(
        "commands",
        "message-text",
        "per-player-commands",
        "as-player-commands"
      ).forEach { name ->
        if (message.node(name).empty()) {
          message.removeChild(name)
        }
      }

      if (message.node("sounds").empty()) {
        message.removeChild("sounds")
        message.removeChild("sounds-randomized")
      }
    }
  }

  fun populate(name: String): MessageConfig = apply { this.name = name }

  @Transient
  lateinit var name: String

  @Transient
  private var broadcastTask: TaskHandle<*>? = null

  private val announcerPlus: AnnouncerPlus by inject()
  private val configManager: ConfigManager by inject()

  @Transient
  private val broadcastQueue = ArrayDeque<Message>()

  fun broadcast(skipInitialDelay: Boolean = false) {
    stop()
    broadcastQueue.clear()
    broadcastQueue.addAll(shuffledMessages())
    if (broadcastQueue.isNotEmpty()) {
      broadcastTask = announcerPlus.asyncTimer(if (skipInitialDelay) 0L else initialDelay.ticks, interval.ticks) {
        if (broadcastQueue.isNotEmpty()) {
          broadcast(broadcastQueue.removeFirst())
        } else {
          broadcast(true)
        }
      }
    }
  }

  private fun broadcast(message: Message) {
    for (onlinePlayer in Bukkit.getOnlinePlayers().toList()) {
      if (announcerPlus.essentials != null) {
        if (announcerPlus.essentials!!.isAfk(onlinePlayer) &&
          onlinePlayer.hasPermission("${announcerPlus.name}.messages.$name.afk")
        ) {
          continue
        }
      }
      if (onlinePlayer.hasPermission("${announcerPlus.name}.messages.$name")) {
        with(message) {
          val audience = announcerPlus.audiences().player(onlinePlayer)
          if (messageText.size != 0) {
            messageText.forEach {
              audience.sendMessage(miniMessage(configManager.parse(onlinePlayer, it)))
            }
          }
          audience.playSounds(sounds, soundsRandomized)
          messageElements().forEach { it.displayIfEnabled(onlinePlayer) }
        }
        announcerPlus.scheduleGlobal {
          message.perPlayerCommands.forEach { dispatchCommandAsConsole(configManager.parse(onlinePlayer, it)) }
          perPlayerCommands.forEach { dispatchCommandAsConsole(configManager.parse(onlinePlayer, it)) }
        }
        announcerPlus.schedule(onlinePlayer) {
          message.asPlayerCommands.forEach { onlinePlayer.performCommand(configManager.parse(onlinePlayer, it)) }
          asPlayerCommands.forEach { onlinePlayer.performCommand(configManager.parse(onlinePlayer, it)) }
        }
      }
    }
    announcerPlus.scheduleGlobal {
      message.commands.forEach { dispatchCommandAsConsole(configManager.parse(null, it)) }
      commands.forEach { dispatchCommandAsConsole(configManager.parse(null, it)) }
    }
  }

  private fun shuffledMessages(): List<Message> {
    if (randomOrder) {
      return messages.shuffled()
    }
    return messages
  }

  fun stop() {
    broadcastTask?.cancel()
  }

  override fun saveTo(node: CommentedConfigurationNode) {
    node.set(this)
    node.node("version").apply {
      set(LATEST_VERSION)
      comment("The version of this configuration. For internal use only, do not modify.")
    }

    if (removeDisabledMessageElements) {
      removeDisabledMessageElements(node)
    }
    if (removeDuplicateComments) {
      node.visit(DuplicateCommentRemovingVisitor())
    }
  }

  companion object : ConfigurationUpgrader, NamedConfigurationFactory<MessageConfig, CommentedConfigurationNode> {
    const val LATEST_VERSION = 1

    override val upgrader = ConfigurationTransformation.versionedBuilder()
      .addVersion(0, initial())
      .addVersion(LATEST_VERSION, zeroToOne())
      .build()

    private fun zeroToOne(): ConfigurationTransformation = ConfigurationTransformation.chain(
      ConfigurationTransformation.builder().addAction(path("interval-time-amount"), TransformAction.rename("interval-time")).build(),
      ConfigurationTransformation.builder().addAction(
        path("interval-time"),
        TransformAction { _, value ->
          val oldUnit = value.parent()?.node("interval-time-unit")?.get<TimeUnit>() ?: TimeUnit.MINUTES
          val oldNum = value.string ?: "3"
          value.set("$oldNum ${oldUnit.name.lowercase()}")
          return@TransformAction null
        }
      ).build(),
      ConfigurationTransformation.builder().addAction(path("interval-time-unit"), TransformAction.remove()).build()
    )

    private fun initial() = ConfigurationTransformation.builder()
      .addAction(path("messages")) { path, value ->
        for (childNode in value.childrenList()) {
          val soundsNode = childNode.node("sounds")
          // the path here is wrong, but we don't use it
          Transformations.upgradeSoundsString.visitPath(path, soundsNode)
        }
        return@addAction null
      }
      .build()

    override fun loadFrom(node: CommentedConfigurationNode, configName: String?): MessageConfig {
      if (configName == null) error("Message configs require a name!")

      val config = node.get<MessageConfig>()?.populate(configName) ?: error("Failed to deserialize MessageConfig")
      addDefaultPermission("announcerplus.messages.${config.name}", PermissionDefault.FALSE)
      return config
    }
  }

  enum class TimeUnit(val ticks: Long) {
    SECONDS(20L),
    MINUTES(1200L),
    HOURS(72000L);

    fun getTicks(units: Int): Long = ticks * units
  }

  data class SimpleDuration(val value: Int, val timeUnit: TimeUnit, val input: String?) {
    companion object {
      val ZERO = SimpleDuration(0, TimeUnit.SECONDS, null)
    }

    val ticks: Long
      get() = timeUnit.getTicks(value)

    object Serializer : TypeSerializer<SimpleDuration> {
      private val regex = Regex("^([0-9]+)( |)([a-zA-Z]+)$")
      private val map = mapOf(
        setOf("s", "second", "seconds", "sec", "secs") to TimeUnit.SECONDS,
        setOf("m", "minute", "minutes", "min", "mins") to TimeUnit.MINUTES,
        setOf("h", "hour", "hours", "hr", "hrs") to TimeUnit.HOURS,
      )

      override fun deserialize(type: Type, node: ConfigurationNode): SimpleDuration {
        val s = node.string ?: "0s"
        val result = regex.matchEntire(s) ?: throw SerializationException("Invalid duration '$s', does not match pattern " + regex.pattern)
        val num = result.groupValues[1].toInt()
        val textPart = result.groupValues[3]
        map.forEach { (set, unit) ->
          if (set.any { it.equals(textPart, ignoreCase = true) }) {
            return SimpleDuration(num, unit, s)
          }
        }
        throw SerializationException("Invalid time unit '$textPart', expected one of ${map.keys}")
      }

      override fun serialize(type: Type, obj: SimpleDuration?, node: ConfigurationNode) {
        var obj1: SimpleDuration? = obj
        if (obj1 == null) {
          obj1 = ZERO
        }
        obj1.input?.let {
          node.set(it)
          return
        }
        node.set("${obj1.value} ${obj1.timeUnit.name.lowercase()}")
      }
    }
  }
}
