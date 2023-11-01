if (defaultTask == null) {
    println("Warning: no default task defined. `build` will be used if no other task is specified.")
}

val taskName = args.getOrNull(0)
val taskX = if (taskName == null) {
    defaultTask ?: tasks.get("build")
        ?: throw IllegalStateException("No default task defined")
} else {
    tasks.get(taskName)
        ?: throw IllegalArgumentException("Unknown task: $taskName")
}

if (args.size > 1) {
    for (i in 1 ..< args.size) {
        val arg = args[i]
        val split = arg.split("=", limit = 2)
        if (split.size != 2) {
            throw IllegalArgumentException("Invalid argument: $arg")
        }
        setProperty(split[0], split[1])
    }
}

setProperty("abuild.version", "0.1.0-experimental")

taskX.run()