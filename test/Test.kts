import abuild.*

val staticLib = dependencyType("staticLib")
val header = dependencyType("headers")

val ccomp = property("cc") ?: "gcc"
val linker = property("ld") ?: "lld"

val headers = module {
    name = "headers"
} sources {
    none("src/headers/*.h")
} dependencies {
    zip url "http://idk.com/thing_headers.zip" type header
}

val cc = customHandler { src, out ->
    exec(ccomp, argRep("-I", headers.all), "-c", src, "-o", out)
}

val asm = customHandler { src, out ->
    exec("nasm", "-f", "elf64", src, "-o", out)
}

val ld = customProcessor { pool, out ->
    exec(linker, pool, "-o", out)
}

val common = module {
    name = "common"
} sources {
    cc("src/common/*.c")
} dependencies {
    file url "http://idk.com/thing_code.a" type staticLib
}

val x86 = module {
    name = "x86"
} sources {
    cc("src/x86/*.c")
    asm("src/x86/*.asm")
}

val out = path("build/bin")

tasks["buildX86"] = task {
    action {
        val built = (common with x86).processSources()
        ld(built) > out
    }
}

val target = property("target")

tasks["build"] = task {
    when (target) {
        "x86" -> {
            dependsOn("buildX86")
        }
        else -> {
            error("Unknown target: $target")
        }
    }
    action {
        println("done!")
    }
}