package com.xiaojiwei.fbng.dat

import java.io.File
import org.junit.Assert.fail

internal fun repoRoot(): File {
    val cwd = File(".").canonicalFile
    return if (cwd.name == "app") cwd.parentFile else cwd
}

internal fun parseCatalog(text: String): DatCatalog {
    return when (val parsed = FbneoDat.parse(text)) {
        is DatParse.Ok -> parsed.catalog
        is DatParse.Error -> {
            fail(parsed.message)
            error(parsed.message)
        }
    }
}
