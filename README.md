# aBuild
aBuild is a build system where the build scripts are written in Kotlin (script).

There will be plugins for aBuild which will make building certain things easier.

## Examples
C Project:
```kotlin
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
none("*.h")
}

val src = module {
    name = "src"
} sources {
    cc("*.c")
}

val bin = path("build/bin")

tasks["build"] = task default {
    action {
        val built = src.processSources()
        ld(built) > bin
    }
}
```
