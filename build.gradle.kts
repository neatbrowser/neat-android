// AGP 9 has Kotlin built in, so there is no org.jetbrains.kotlin.android plugin. The Kotlin version used for
// :core and for the Compose compiler is set here.
plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.jvm") version "2.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
}
