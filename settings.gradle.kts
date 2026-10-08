rootProject.name = "FSMNK-TR"

// Sadece çalışan ve aktif eklentileri dahil et
val enabled = listOf(
    "CanliTV",
    "HDFilmCehennemi",
    "FilmMakinesi",
    "FilmModu",
    "TurkAnime"
)

File(rootDir, ".").eachDir { dir ->
    if (enabled.contains(dir.name) && File(dir, "build.gradle.kts").exists()) {
        include(dir.name)
    }
}

fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
