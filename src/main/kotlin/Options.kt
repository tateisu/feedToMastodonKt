@file:Suppress("SameParameterValue")

import util.isTruth
import kotlin.reflect.KProperty

abstract class Option<T : Any?>(
    val desc: String,
    val names: List<String>,
) {
    abstract var value: T
    open val noArg: T? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return this.value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        this.value = value
    }

    abstract fun parseValue(src: String)
}

class OptionString(
    desc: String,
    names: List<String>,
    override var value: String,
) : Option<String>(desc, names) {
    override fun parseValue(src: String) {
        value = src
    }
}

class OptionStringList(
    desc: String,
    names: List<String>,
    override var value: MutableList<String> = mutableListOf(),
) : Option<MutableList<String>>(desc, names) {
    override fun parseValue(src: String) {
        // カンマ区切り、またはオプションを複数回指定する
        value.addAll(src.split(",").filter { it.isNotEmpty() })
    }
}

class OptionBoolean(
    desc: String,
    names: List<String>,
    override var value: Boolean = false,
    override val noArg: Boolean = true,
) : Option<Boolean>(desc, names) {
    override fun parseValue(src: String) {
        value = src.isTruth()
    }
}

val options = listOf(

    OptionString(
        desc = "[in]config file.",
        names = listOf("-c", "--config"),
        value = "./config.txt",
    ),

    OptionString(
        desc = "[in]api secrets file.",
        names = listOf( "--apiSecrets"),
        value = "./apiSecrets.json",
    ),

    OptionString(
        desc = "[out]image save dir for discord",
        names = listOf("--imageDir"),
        value = "",
    ),
    OptionString(
        desc = "[out]url prefix for image save dir for discord",
        names = listOf("--imageUrlPrefix"),
        value = "",
    ),
    OptionStringList(
        desc = "urls for does not check already-posted, comma separated or multi-time specified",
        names = listOf("--forcePost"),
    ),
    OptionBoolean(
        desc = "does not check already-posted for all tweet",
        names = listOf("--forcePostAll"),
        value = false,
        noArg = true,
    ),
    OptionBoolean(
        desc = "skip tweets that is too old or already processed.",
        names = listOf("--skipOld"),
        value = true,
    ),
    OptionBoolean(
        desc = "if set to false, just read tweets without forward posting.",
        names = listOf("--post"),
        value = true,
    ),
    OptionBoolean(
        desc = "post the media even if --post is false.",
        names = listOf("--postMedia"),
        value = false,
    ),
    OptionBoolean(
        desc = "more verbose information.",
        names = listOf("-v", "--verbose"),
        value = false,
        noArg = true,
    ),
    OptionBoolean(
        desc = "more verbose about content text.",
        names = listOf("--verboseContent"),
        value = false,
        noArg = true,
    ),
    OptionBoolean(
        desc = "show verbose about removing urls in tweet.",
        names = listOf("--verboseUrlRemove"),
        value = false,
        noArg = true,
    ),
)

@Suppress("SameParameterValue")
private fun findOptionString(targetName: String) =
    options.mapNotNull { it as? OptionString }
        .find { o -> o.names.any { it == targetName } }
        ?: error("findOption: missing option name $targetName")

@Suppress("SameParameterValue")
private fun findOptionStringList(targetName: String) =
    options.mapNotNull { it as? OptionStringList }
        .find { o -> o.names.any { it == targetName } }
        ?: error("findOption: missing option name $targetName")

private fun findOptionBoolean(targetName: String) =
    options.mapNotNull { it as? OptionBoolean }
        .find { o -> o.names.any { it == targetName } }
        ?: error("findOption: missing option name $targetName")

var configFileName by findOptionString("--config")
var apiSecretsFileName by findOptionString("--apiSecrets")

var imageDir by findOptionString("--imageDir")
var imageUrlPrefix by findOptionString("--imageUrlPrefix")
var forcePost by findOptionStringList("--forcePost")
var forcePostAll by findOptionBoolean("--forcePostAll")

var skipOld by findOptionBoolean("--skipOld")
var post by findOptionBoolean("--post")
var postMedia by findOptionBoolean("--postMedia")
var verboseOption by findOptionBoolean("--verbose")
var verboseContent by findOptionBoolean("--verboseContent")
var verboseUrlRemove by findOptionBoolean("--verboseUrlRemove")
fun parseOptions(args: List<String>): List<String> {
    val optionsMap = options.map { it.names.map { name -> name to it } }.flatten().toMap()
    val notOptions = ArrayList<String>()
    val end = args.size
    var i = 0
    while (i < end) {
        val arg = args[i++]
        when (val option = optionsMap[arg]) {
            null -> when {
                arg == "--" -> {
                    notOptions.addAll(args.slice(i..end))
                    break
                }

                arg.startsWith("-") ->
                    error("unknown option: $arg")

                else -> {
                    notOptions.add(arg)
                    continue
                }
            }

            else -> option.parseValue(
                when (val noArg = option.noArg) {
                    null -> args.elementAtOrNull(i++)
                        ?: error("missing option value after $arg")

                    else -> noArg.toString()
                }
            )
        }
    }
    return notOptions
}
