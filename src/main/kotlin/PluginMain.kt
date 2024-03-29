package github.kituin.mirai.qian


import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import net.mamoe.mirai.message.data.MusicKind
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.extension.PluginComponentStorage
import net.mamoe.mirai.console.plugin.PluginManager.INSTANCE.load
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.event.Event
import net.mamoe.mirai.event.EventChannel
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.FriendMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.utils.SimpleLogger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.*


object QianConfig : AutoSavePluginConfig("qianConfig") {
    var error: Boolean = false
    lateinit var qianList: JsonElement

}

object QianUserTemp : AutoSavePluginData("qianUserTemp") {
    var historyTemp = mutableMapOf<Long, Int>()
    var isQian = mutableMapOf<Long, Boolean>()
}

class UserTempReload() : TimerTask() {
    override fun run() {
        QianUserTemp.historyTemp = mutableMapOf<Long, Int>()
        QianUserTemp.isQian = mutableMapOf<Long, Boolean>()
    }
}

fun QianProcess(eventChannel: EventChannel<Event>) {
    eventChannel.subscribeAlways<GroupMessageEvent>
    {
        if (!QianConfig.error) {
            if (message.contentToString() == "抽签") {
                if (QianUserTemp.isQian[sender.id] == true) {
                    val chain = buildMessageChain {
                        +message.quote()
                        +PlainText("今天已经抽过啦(*´﹃｀*)")
                    }
                    group.sendMessage(chain)
                } else {
                    val randoms = (1..384).random()
                    val shi = QianConfig.qianList.jsonObject["$randoms"]?.jsonObject?.get("shi").toString()
                    val chain = buildMessageChain {
                        +message.quote()
                        +PlainText("[第 $randoms 签]\r\r")
                        +PlainText(shi)
                    }
                    QianUserTemp.historyTemp[sender.id] = randoms
                    QianUserTemp.isQian[sender.id] = true
                    group.sendMessage(chain)

                }
            } else if (message.contentToString() == "解签") {
                val randoms = QianUserTemp.historyTemp[sender.id]
                if (randoms is Int) {
                    val jie = QianConfig.qianList.jsonObject["$randoms"]?.jsonObject?.get("jie").toString()
                    val chain = buildMessageChain {
                        +message.quote()
                        +PlainText("[第 $randoms 签:解签]\r\r")
                        +PlainText(jie)
                    }
                    group.sendMessage(chain)
                } else {
                    val chain = buildMessageChain {
                        +message.quote()
                        +PlainText("请先使用抽签")
                    }
                    group.sendMessage(chain)
                }


            }
            return@subscribeAlways
        }


    }
}

object QianMain : KotlinPlugin(
    JvmPluginDescription(
        id = "github.kituin.mirai.qian",
        name = "小千抽签",
        version = "0.1.1"
    ) {
        author("kitUIN")
        info(
            """
            这是一个抽签插件, 
            为小千开发.
        """.trimIndent()
        )
        // author 和 info 可以删除.
    }
) {
    override fun PluginComponentStorage.onLoad() {

        QianUserTemp.reload()
        QianConfig.reload()
        val qian = getResource("qian.json")
        if (qian is String) {
            QianConfig.qianList = Json.parseToJsonElement(qian)
            QianConfig.error = false
        } else {
            QianConfig.error = true
            logger.error("配置文件加载失败")
        }
        val task = UserTempReload()
        val localDateTime = LocalDateTime.of(LocalDate.now(), LocalTime.MAX)
        val date = Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant())
        // 计时器，默认每日23:59:59清空抽签缓存
        Timer().schedule(task, date, 86400000)
    }

    override fun onEnable() {

        QianUserTemp.reload()
        QianConfig.reload()
        val eventChannel = GlobalEventChannel.parentScope(this)
        QianProcess(eventChannel)

    }
}
