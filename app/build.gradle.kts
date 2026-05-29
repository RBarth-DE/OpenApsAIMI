
import org.gradle.kotlin.dsl.debugImplementation
import java.text.SimpleDateFormat
import java.util.Date
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

// Fixes errors in KSP task dependency
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    id("com.android.application")
    // kotlin("android")
    // kotlin("kapt")
    //alias(libs.plugins.ksp)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("android-app-dependencies")
    id("test-app-dependencies")
    id("jacoco-app-dependencies")
}

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
}

fun DependencyHandler.`kapt`(dependencyNotation: Any): Dependency? =
    add("kapt", dependencyNotation)

// -----------------------------------------------------------------------------
// Fonctions personnalisées
// -----------------------------------------------------------------------------
// ─── Git providers — configuration cache compatible ───────────────────────────
fun gitExec(vararg args: String): Provider<String> =
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true          // don't throw on non-zero exit
    }.standardOutput.asText.map { it.trim() }

val gitDescribeProvider  = gitExec("describe", "--always")
val gitRemoteProvider    = gitExec("remote", "get-url", "origin")
val gitAvailableProvider = gitExec("--version").map { it.isNotEmpty() }
val gitStatusProvider    = gitExec("status", "-s").map { output ->
    output
        .replace(Regex("""(?m)^\s*(M|A|D|\?\?)\s*.*?\.idea\/codeStyles\/.*?\s*$"""), "")
        .replace(Regex("""(?m)^\s*(\?\?)\s*.*?\s*$"""), "")
        .trim().isEmpty()
}

fun generateDate(): String {
    val stringBuilder: StringBuilder = StringBuilder()
    // showing only date prevents app to rebuild everytime
    stringBuilder.append(SimpleDateFormat("yyyy.MM.dd").format(Date()))
    return stringBuilder.toString()
}

fun isMaster(): Boolean = !Versions.appVersion.contains("-")

// ─────────────────────────────────────────────────────────────────────────────

// -----------------------------------------------------------------------------
// Configuration Android
// -----------------------------------------------------------------------------
android {
    compileSdk = 36
    // Si tu n'as pas de variable pour compileSdk, mets-le en dur, ex. 34
    // compileSdk = Versions.compileSdk

    namespace = "app.aaps"

    defaultConfig {
        minSdk    = Versions.minSdk
        targetSdk = Versions.targetSdk

        buildConfigField("String", "VERSION",      "\"$version\"")
        buildConfigField("String", "BUILDVERSION", "\"${gitDescribeProvider.getOrElse("NoGitSystemAvailable")}-${generateDate()}\"")
        buildConfigField("String", "REMOTE",       "\"${gitRemoteProvider.getOrElse("NoGitSystemAvailable")}\"")
        buildConfigField("String", "HEAD",         "\"${gitDescribeProvider.getOrElse("NoGitSystemAvailable")}\"")
        buildConfigField("String", "COMMITTED",    "\"${gitStatusProvider.getOrElse(false)}\"")

        testInstrumentationRunner = "app.aaps.runners.InjectedTestRunner"
    }

    flavorDimensions += "standard"
    productFlavors {
        create("full") {
            isDefault = true
            applicationId = "info.nightscout.androidaps"
            dimension = "standard"
            resValue("string", "app_name", "AAPS")
            versionName = Versions.appVersion
            manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_launcher_round"
        }
        create("pumpcontrol") {
            applicationId = "info.nightscout.aapspumpcontrol"
            dimension = "standard"
            resValue("string", "app_name", "Pumpcontrol")
            versionName = Versions.appVersion + "-pumpcontrol"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_pumpcontrol"
            manifestPlaceholders["appIconRound"] = "@null"
        }
        create("aapsclient") {
            applicationId = "info.nightscout.aapsclient"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient")
            versionName = Versions.appVersion + "-aapsclient"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_yellowowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_yellowowl"
        }
        create("aapsclient2") {
            applicationId = "info.nightscout.aapsclient2"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient2")
            versionName = Versions.appVersion + "-aapsclient"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_blueowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_blueowl"
        }
        create("aapsclient3") {
            applicationId = "info.nightscout.aapsclient3"
            dimension = "standard"
            resValue("string", "app_name", "AAPSClient3")
            versionName = Versions.appVersion + "-aapsclient3"
            manifestPlaceholders["appIcon"] = "@mipmap/ic_greenowl"
            manifestPlaceholders["appIconRound"] = "@mipmap/ic_greenowl"
        }
    }

    // -------------------------------------------------------------------------
    // Configuration de signature (release)
    // -------------------------------------------------------------------------
    signingConfigs {
        // On peut l'appeler "release" ou un autre nom
        create("release") {
            // Seule storeFile attend un File
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "dummy.jks")
            // Les autres sont des Strings
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "dummy"
            keyAlias = System.getenv("KEY_ALIAS") ?: "dummy"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "dummy"
        }
    }

    // -------------------------------------------------------------------------
    // Build Types
    // -------------------------------------------------------------------------
    buildTypes {
        getByName("release") {
            // Active ou non le minify
            // minifyEnabled true
            // shrinkResources true

            // Associe la config "release"
            signingConfig = signingConfigs.getByName("release")
        }
        getByName("debug") {
            // config debug
        }
    }

    useLibrary("org.apache.http.legacy")

    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }

    sourceSets {
        getByName("full") { kotlin.directories.add("src/withPumps/kotlin") }
        getByName("pumpcontrol") { kotlin.directories.add("src/withPumps/kotlin") }
        getByName("aapsclient2") { kotlin.directories.add("src/aapsclient/kotlin") }
        getByName("aapsclient3") { kotlin.directories.add("src/aapsclient/kotlin") }
    }
}

// -----------------------------------------------------------------------------
// allprojects / repositories
// -----------------------------------------------------------------------------
allprojects {
    repositories {
        mavenCentral()
        google()
    }
}

// -----------------------------------------------------------------------------
// Dependencies
// -----------------------------------------------------------------------------
dependencies {
    // in order to use internet"s versions you"d need to enable Jetifier again
    // https://github.com/nightscout/iconify.git
    implementation(project(":shared:impl"))
    implementation(project(":core:data"))
    implementation(project(":core:objects"))
    implementation(project(":core:graph"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":ui"))
    implementation(project(":plugins:aps"))
    implementation(project(":plugins:automation"))
    implementation(project(":plugins:calibration"))
    implementation(project(":plugins:configuration"))
    implementation(project(":plugins:constraints"))
    implementation(project(":plugins:main"))
    implementation(project(":plugins:sensitivity"))
    implementation(project(":plugins:smoothing"))
    implementation(project(":plugins:source"))
    implementation(project(":plugins:sync"))
    implementation(project(":implementation"))
    implementation(project(":database:impl"))
    implementation(project(":database:persistence"))
    implementation(project(":pump:virtual"))
    implementation(project(":workflow"))

    // Pump drivers — only for full + pumpcontrol flavors
    val pumpDependencies = listOf(
        ":pump:combov2",
        ":pump:dana",
        ":pump:danars",
        ":pump:danars-emulator",
        ":pump:danar",
        ":pump:danar-emulator",
        ":pump:diaconn",
        ":pump:eopatch",
        ":pump:medtrum",
        ":pump:equil",
        ":pump:equil-emulator",
        ":pump:insight",
        ":pump:medtronic",
        ":pump:common",
        ":pump:omnipod:common",
        ":pump:omnipod:eros",
        ":pump:omnipod:dash",
        ":pump:rileylink"
    )
    pumpDependencies.forEach {
        "fullImplementation"(project(it))
        "pumpcontrolImplementation"(project(it))
    }

    implementation(libs.androidx.lifecycle.process)

    testImplementation(project(":shared:tests"))
    androidTestImplementation(project(":shared:tests"))
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.org.skyscreamer.jsonassert)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    // Rhino is needed by the openAPS adapter test fixtures under app/src/androidTest
    // (these files reference org.mozilla.javascript.* classes directly).
    androidTestImplementation(libs.org.mozilla.rhino)

    debugImplementation(libs.com.squareup.leakcanary.android)

    /* Dagger2 - We are going to use dagger.android which includes
     * support for Activity and fragment injection so we need to include
     * the following dependencies */
    ksp(libs.com.google.dagger.android.processor)
    kspAndroidTest(libs.com.google.dagger.android.processor)
    ksp(libs.com.google.dagger.compiler)
    implementation(libs.com.google.dagger.hilt.android)
    ksp(libs.com.google.dagger.hilt.compiler)

    // MainApp
    api(libs.com.uber.rxdogtag2.rxdogtag)
    // MPAndroidChart for comparator
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    // Remote config
    api(libs.com.google.firebase.config)
    // Navigation Compose
    api(libs.androidx.compose.navigation)
}

// -----------------------------------------------------------------------------
// Dernières lignes (messages console)
// -----------------------------------------------------------------------------
println("-------------------")
println("isMaster: ${isMaster()}")   // fine — only reads Versions.appVersion, no git
println("gitAvailable: ${gitAvailableProvider.getOrElse(false)}")
println("allCommitted: ${gitStatusProvider.getOrElse(false)}")
println("-------------------")

if (!gitAvailableProvider.getOrElse(false)) {
    throw GradleException(
        "GIT system is not available. On Windows try to run Android Studio as an Administrator. " +
            "Check if GIT is installed and Studio have permissions to use it"
    )
}

/*if (isMaster() && !allCommitted()) {
    throw GradleException("There are uncommitted changes.")
}*/
