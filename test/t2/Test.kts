import abuild.*

val cc = customHandler { src, out ->
    exec("gcc", "-c", argRep("-I", headers.all.parents), src, "-o", out)
}

val ld = customProcessor { pool, out ->
    exec("gcc", "-Bldd", pool, "-o", out)
}

val headers = module {
    name = "headers"
} sources {
    none("src/*.h")
}

val src = module {
    name = "src"
} sources {
    cc("t2/*.c")
}

val bin = path("build/bin")

tasks["build"] = task default {
    action {
        val built = src.processSources()
        ld(built) > bin
    }
}
