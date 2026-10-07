rootProject.name = "FSMNK-TR"

// Otomatik alt proje tanıma: build.gradle.kts içeren tüm alt klasörler projeye dahil edilir
val disabled = listOf<String>()

File(rootDir, ".").eachDir { dir ->
    if (!disabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
