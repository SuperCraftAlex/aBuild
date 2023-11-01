import abuild.*

val cc = customHandler { src, out ->
    exec("gcc", src, "-o", out)
}

val src = module {
    name = "src"
} sources {
    cc("t1/*.c")
}

val bin = path("build/bin")

tasks["build"] = task {
    action {
        val built = src.processSources()
        built.first() > bin
        println("done!")
    }
}
