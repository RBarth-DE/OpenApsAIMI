plugins {
    id("kmp-test-defaults")
    kotlin("multiplatform")
    // NOT com.android.library. AGP 9 refuses that plugin together with the multiplatform plugin.
    // Same reason as the :core modules, :pump:virtual and the other converted plugins.
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    // Metro, so this module can wire its own Android entry points. The rewrite of this build file
    // dropped the Hilt/KSP processors but did not add this one, and without it the module's
    // `@ContributesTo` containers are never registered and nothing here can be injected.
    alias(libs.plugins.metro)
}

// Same generator as the other converted plugins, pointed at this module's strings. The strings do
// not move, and AAPT keeps resolving them on Android exactly as before - this only adds a TextRef
// named view of them that common code can reach.
val generateMainStrings = tasks.register<GenerateKeyStringsTask>("generateMainStrings") {
    resDir.set(layout.projectDirectory.dir("src/androidMain/res"))
    packageName.set("app.aaps.plugins.main")
    owner.set("main")
    objectName.set("MainStrings")
    idsObjectName.set("MainStringIds")
    reportFile.set(layout.buildDirectory.file("reports/mainStrings/translations.txt"))
    // Set explicitly: addGeneratedSourceDirectory derives its convention from the task name, so both
    // properties would land on one directory and the second file written would delete the first.
    commonOutputDir.set(layout.buildDirectory.dir("generated/mainStrings/common"))
    androidOutputDir.set(layout.buildDirectory.dir("generated/mainStrings/android"))
}

kotlin {
    android {
        namespace = "app.aaps.plugins.main"
        compileSdk = Versions.compileSdk
        minSdk = Versions.minSdk
        androidResources { enable = true }
        // isIncludeAndroidResources is what makes Robolectric work - see :core:ui for the detail.
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
        compilerOptions { jvmTarget.set(Versions.jvmTarget) }

        lint {
            checkReleaseBuilds = false
            disable += "MissingTranslation"
            disable += "ExtraTranslation"
        }
    }

    iosArm64()
    iosSimulatorArm64()

    // Desktop (Windows/macOS/Linux). Compose Multiplatform resolves its `desktop` variant from a
    // plain jvm() target, so no special target name is needed.
    jvm()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateMainStrings.flatMap { it.commonOutputDir })
            dependencies {
                implementation(project(":core:data"))
                implementation(project(":core:interfaces"))
                implementation(project(":core:keys"))
                implementation(project(":core:objects"))
                implementation(project(":core:ui"))
                implementation(project(":core:utils"))

                implementation(libs.androidx.collection)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.jetbrains.compose.runtime)
            }
        }

        androidMain {
            // Android only: the string name to R.string id map.
            kotlin.srcDir(generateMainStrings.flatMap { it.androidOutputDir })
            dependencies {
                // Fork AIMI Auditor UI components
                implementation(project(":plugins:aps"))
                // The compose overview lives in :ui (GraphViewModel, BgGraphCompose and the glass
                // detail screens use it). Present in the pre-KMP build file, dropped by the rewrite.
                implementation(project(":ui"))
                // The graph series types of the dashboard and the overview, and the vendored
                // GraphView widget (com.jjoe64.graphview) they draw on. :core:graph keeps the
                // widget to itself, so it is named here too.
                implementation(project(":core:graph"))
                implementation(project(":core:graphview"))
                // RxBusImpl, used by OverviewPlugin.
                implementation(project(":shared:impl"))
                // AndroidX Fragment: the overview and dashboard fragments and their view models.
                implementation(libs.androidx.fragment)
                // GridLayout, used by the overview chart menu.
                implementation(libs.androidx.gridlayout)
                // observeAsState, for the live data the dashboard screens read.
                implementation(libs.androidx.compose.runtime.livedata)
                // Fork legacy overview layout uses FlexboxLayout
                implementation(libs.com.google.android.flexbox)
            }
        }

        // Hand written rather than taken from test-module-dependencies, which applies
        // com.android.library and so cannot be used here. Same approach as :plugins:sensitivity.
        getByName("androidHostTest") {
            dependencies {
                implementation(project(":shared:tests"))
                implementation(project(":implementation"))
                implementation(libs.org.junit.jupiter)
                implementation(libs.org.junit.jupiter.api)
                implementation(libs.org.mockito.junit.jupiter)
                implementation(libs.org.mockito.kotlin)
                implementation(libs.com.google.truth)
                implementation(libs.kotlinx.coroutines.test)
                // The real org.json: isReturnDefaultValues makes the platform stub answer null rather
                // than throwing, which NPEs the shared profile fixtures. Same reason as :pump:virtual.
                implementation(libs.org.json.android)
                runtimeOnly(libs.org.junit.vintage.engine)
                runtimeOnly(libs.org.junit.platform.launcher)
            }
        }
    }
}

