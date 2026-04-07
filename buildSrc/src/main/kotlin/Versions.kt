import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

@Suppress("ConstPropertyName")
object Versions {

    const val appVersion = "4.0.0.0-dev-RB"
    const val versionCode = 1500

    const val compileSdk = 36
    const val minSdk = 31
    const val targetSdk = 34 // Bumped to 34 for Health Connect compatibility on Android 14
    const val wearMinSdk = 30
    const val wearTargetSdk = 30

    val javaVersion = JavaVersion.VERSION_21
    val jvmTarget = JvmTarget.JVM_21
    const val jacoco = "0.8.11"
}
