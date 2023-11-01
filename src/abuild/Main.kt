package abuild

import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.CopyOnWriteArraySet

typealias SourceSet = Set<Pair<Regex, SourceHandler>>

class SourcesContext {
    private val sources: MutableSet<Pair<Regex, SourceHandler>> = mutableSetOf()

    internal fun build(): SourceSet {
        return sources
    }

    internal fun add(src: String, handler: SourceHandler) {
        sources += Regex(
             src.replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".")
        ) to handler
    }

    val none = object : SourceHandler {}
}

interface SourceHandler {
    operator fun invoke(vararg src: String) {
        if (sourceBuilderStack.isEmpty()) throw IllegalStateException("Can only be invoked inside a sources block")
        src.forEach {
            sourceBuilderStack.peek().add(it, this)
        }
    }

    fun processSource(ctx: ExecutingContext, file: File): File {
        return file
    }
}

class KotlinHandlerContext {
    var version: String? = null
}

fun customHandler(sourceProcessor: ExecutingContext.(src: File, out: File) -> Unit): SourceHandler =
    object : SourceHandler {
        override fun processSource(ctx: ExecutingContext, file: File): File {
            val temp = tempFile("processed_source")
            temp.createNewFile()
            sourceProcessor(ctx, file, temp)
            return temp
        }
    }

internal val sourceBuilderStack = Stack<SourcesContext>()

infix fun Module.sources(block: SourcesContext.() -> Unit): Module {
    val builder = SourcesContext()
    sourceBuilderStack.push(builder)
    block(builder)
    sourceBuilderStack.pop()
    sourceFiles = builder.build()
    return this
}

data class Dependency(
    val source: FileSource,
    val type: Type
) {
    var file: File? = null
        get() {
            if (field == null) {
                field = source.get()
            }
            return field
        }

    interface Type {
        val name: String
    }
}

fun dependencyType(name: String): Dependency.Type =
    object : Dependency.Type {
        override val name: String
            get() = name
    }

interface FileSource {
    val module: Module

    fun get(): File
}

typealias FilePool = MutableSet<File>

/**
 * Returns a reference to a file that will be inside the file pool.
 */
fun FilePool.new(): File {
    val temp = tempFile("pool")
    add(temp)
    return temp
}

typealias DirectoryPool = MutableSet<File>

/**
 * Returns all parent directories of all the files
 */
val FilePool.parents: DirectoryPool get() {
    val dirs: DirectoryPool = mutableSetOf()
    forEach { file ->
        dirs += file.parentFile
    }
    return dirs
}

fun filePool(): FilePool =
    mutableSetOf()

typealias FilePoolProcessor = (FilePool) -> File

fun path(str: String): Path {
    val p = Paths.get(str)
    p.toFile().parentFile.mkdirs()
    return p
}

operator fun File.compareTo(other: Path): Int {
    val f = other.toFile()
    f.delete()
    this.renameTo(f)
    return 0
}

fun customProcessor(block: ExecutingContext.(FilePool, out: File) -> Unit): FilePoolProcessor = {
    val temp = tempFile("processed_pool")
    val exec = ExecutingContext(
        { error -> error("Error in custom processor: $error") },
        temp
    )
    block(exec, it, temp)
    exec.end()
    temp
}

class DependencyContext(
    private val module: Module
) {

    val none = dependencyType("none")

    infix fun FileSource.type(type: Dependency.Type): Dependency {
        return Dependency(
            this,
            type
        )
    }

    val file = Unit

    data class NoneZip(
        val time: Long = System.nanoTime()
    )
    val zip = NoneZip()

    infix fun Unit.url(url: URL): FileSource =
        object : FileSource {
            override val module: Module
                get() =
                    this@DependencyContext.module

            override fun get(): File =
                download(url)
        }

    infix fun Unit.url(url: String): FileSource =
        url(URI(url).toURL())

    infix fun NoneZip.url(url: URL): FileSource =
        object : FileSource {
            override val module: Module
                get() =
                    this@DependencyContext.module

            override fun get(): File {
                val dl = download(url)
                val out = unzip(dl)
                dl.delete()
                return out
            }
        }

    infix fun NoneZip.url(url: String): FileSource =
        url(URI(url).toURL())

}

infix fun Module.dependencies(block: DependencyContext.() -> Unit): Module {
    val builder = DependencyContext(this)
    block(builder)
    return this
}

typealias DependencySet = Set<Dependency>

class ModuleContext {
    var name: String? = null
}

data class Module(
    val name: String
): JoinedModules {
    override val modules: List<Module>
        get() = listOf(this)

    internal var sourceFiles: SourceSet? = null
    internal var dependencySet: DependencySet = setOf()
}

interface JoinedModules {
    val modules: List<Module>
}

val JoinedModules.sources: FilePool get() {
    val files: MutableSet<Pair<Regex, SourceHandler>> = mutableSetOf()
    modules.forEach { mod ->
        mod.sourceFiles?.let { files.addAll(it) }
    }
    return files.toFilePool()
}

/**
 * Resolves all dependencies and returns a file pool containing all files.
 */
val JoinedModules.dependencies: FilePool get() {
        val files: MutableSet<File> = mutableSetOf()
        modules.forEach { mod ->
            mod.dependencySet.forEach { dep ->
                val df = dep.file!!
                if (df.isDirectory) {
                    df.walkTopDown().forEach { file ->
                        files += file
                    }
                } else {
                    files += df
                }
            }
        }
        return files
    }

val JoinedModules.all: FilePool get() {
        val new: FilePool = mutableSetOf()
        new += sources
        new += dependencies
        return new
    }

class SimpleJoinedModules(
    override val modules: List<Module>
): JoinedModules

infix fun JoinedModules.with(modules: JoinedModules): JoinedModules =
    SimpleJoinedModules(this.modules + modules.modules)

private fun cd(): File =
    Paths.get("").toAbsolutePath().toFile()

fun Regex.matches(f: File) =
    matches(f.relativeTo(cd()).path) || matches(f.absolutePath)

infix fun JoinedModules.eachSource(block: ExecutingContext.(File, SourceHandler) -> Unit) {
    val exec = mutableListOf<ExecutingContext>()
    modules.forEach { module ->
        val errorHandler: ErrorHandler = { error ->
            error("Error in module ${module.name}: $error")
        }
        module.sourceFiles?.forEach {
            val (regex, handler) = it
            cd().walkTopDown().forEach { file ->
                if (regex.matches(file)) {
                    val e = ExecutingContext(
                        errorHandler,
                        file
                    )
                    exec += e
                    block(e, file, handler)
                }
            }
        }
    }
    exec.forEach {
        it.end()
    }
}

fun SourceSet.toFilePool(): FilePool {
    val files = mutableSetOf<File>()
    cd().walkTopDown().forEach { file ->
        forEach { (regex, _) ->
            if (regex.matches(file)) {
                files += file
            }
        }
    }
    return files
}

/**
 * Executes the associated source handlers for each source file.
 */
fun JoinedModules.processSources(): FilePool {
    val files = mutableSetOf<File>()
    eachSource { _, handler ->
        files += handler.processSource(this, file)
    }
    return files
}

typealias ErrorHandler = (Throwable) -> Unit

data class ArgRep(
    val prefix: String,
    val files: FilePool,
)

fun argRep(prefix: String, files: FilePool): ArgRep =
    ArgRep(prefix, files)

data class ExecutingContext(
    val errorHandler: ErrorHandler,
    val file: File,
) {

    data class ErroredProc(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    )

    class Unfinished(
        internal val err: File,
        internal val out: File,
        internal val process: Process,
        internal var catcher: ((ErroredProc) -> Unit)? = null,
    )

    private val processes = CopyOnWriteArraySet<Unfinished>()
    private val tempFiles = mutableListOf<File>()

    /**
     * Every argument is converted to a string.
     * For files, the absolute path is used.
     * For file pools, all files added to the pool will be used.
     */
    fun exec(vararg cmd: Any): Unfinished {
        val err = tempFile("proc_err")
        val out = tempFile("proc_out")
        val args: MutableList<String> = mutableListOf()
        cmd.forEach {
            when (it) {
                is File -> args += it.absolutePath
                is MutableSet<*> -> args += (it as? FilePool ?: error("Invalid parameter for exec")).map { f ->
                    f.absolutePath
                }
                is ArgRep -> args += it.files.map {
                    f -> "${it.prefix}${f.absolutePath}"
                }
                else -> args += it.toString()
            }
        }
        val process = ProcessBuilder()
            .redirectError(err)
            .redirectOutput(out)
            .command(args)
            .start()
        val unfinished = Unfinished(
            err,
            out,
            process
        )
        processes += unfinished
        return unfinished
    }

    fun Unfinished.catch(block: (ErroredProc) -> Unit = {
        println(it.stderr)
    }): Unfinished {
        catcher = block
        return this
    }

    data class Finished(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
    )

    fun Unfinished.await(): Finished {
        process.waitFor()
        val err = Files.readString(err.toPath())
        print(err)
        val out = Files.readString(out.toPath())
        if (process.exitValue() != 0) {
            catcher?.invoke(
                ErroredProc(
                    process.exitValue(),
                    out,
                    err
                )
            )
        }
        processes -= this
        return Finished(
            out,
            err,
            process.exitValue()
        )
    }

    fun temp(): File {
        val temp = tempFile("req")
        tempFiles += temp
        return temp
    }

    internal fun end() {
        processes.forEach {
            it.await()
        }
        tempFiles.forEach {
            it.delete()
        }
    }

}

fun module(block: ModuleContext.() -> Unit): Module {
    val context = ModuleContext()
    block(context)
    return Module(
        context.name ?: throw IllegalStateException("Module name must be specified")
    )
}

private val properties: MutableMap<String, Any?> = mutableMapOf()

fun setProperty(name: String, value: Any?) {
    properties[name] = value
}

fun property(name: String): Any? =
    properties.getOrDefault(name, null)

data class Task(
    val actions: List<() -> Unit>,
    val dependencies: MutableSet<String> = mutableSetOf()
) {

    fun run() {
        dependencies.forEach {
            tasks[it]?.run()
                ?: throw IllegalStateException("Task $it (dependency of task) does not exist")
        }
        actions.forEach {
            it()
        }
    }

}

class TaskContext {
    val actions = mutableListOf<() -> Unit>()
    val dependencies = mutableSetOf<String>()

    fun action(block: () -> Unit) {
        actions += block
    }

    fun dependsOn(task: String) {
        dependencies += task
    }

    internal fun done(): Task {
        return Task(actions, dependencies)
    }
}

fun task(block: TaskContext.() -> Unit): Task {
    val context = TaskContext()
    block(context)
    return context.done()
}

class NoneTask

val task = NoneTask()

var defaultTask: Task? = null
    private set

infix fun NoneTask.default(block: TaskContext.() -> Unit): Task =
    task(block).also {
        defaultTask = it
    }

val tasks: MutableMap<String, Task> = mutableMapOf()