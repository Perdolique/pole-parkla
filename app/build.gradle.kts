import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
}

val ppBuildChannel = providers.gradleProperty("ppBuildChannel").getOrElse("local")
require(ppBuildChannel in setOf("local", "pr", "production")) {
    "ppBuildChannel must be local, pr, or production"
}
val ppVersionName = providers.gradleProperty("ppVersionName").getOrElse("1.0.0")
val ppVersionCode = providers.gradleProperty("ppVersionCode").getOrElse("2").toInt()
require(ppVersionCode in 1..2100000000) { "ppVersionCode is outside the Android range" }
if (ppBuildChannel != "local") {
    require(providers.gradleProperty("ppVersionName").isPresent) { "CI requires ppVersionName" }
    require(providers.gradleProperty("ppVersionCode").isPresent) { "CI requires ppVersionCode" }
}

android {
    namespace = "com.perdolique.poleparkla"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.perdolique.poleparkla"
        minSdk = 26
        targetSdk = 36
        versionCode = ppVersionCode
        versionName = ppVersionName
        manifestPlaceholders["ppLauncherLabel"] = "@string/app_name"
        manifestPlaceholders["ppShortcutsResource"] = "@xml/shortcuts"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use(::load)
        }
    }

    signingConfigs {
        if (ppBuildChannel != "local") {
            create("ci") {
                fun signingValue(name: String): String =
                    providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }
                        ?: error("$ppBuildChannel requires $name")
                storeFile = file(signingValue("ANDROID_KEYSTORE_PATH"))
                require(storeFile!!.isFile) { "CI keystore file does not exist" }
                storePassword = signingValue("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = signingValue("ANDROID_KEY_ALIAS")
                keyPassword = signingValue("ANDROID_KEY_PASSWORD")
            }
        } else if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(keystoreProperties.getProperty("storeFile")))
                storePassword = requireNotNull(keystoreProperties.getProperty("storePassword"))
                keyAlias = requireNotNull(keystoreProperties.getProperty("keyAlias"))
                keyPassword = requireNotNull(keystoreProperties.getProperty("keyPassword"))
            }
        }
    }

    buildTypes {
        debug {
            if (ppBuildChannel == "pr") {
                applicationIdSuffix = ".debug"
                manifestPlaceholders["ppLauncherLabel"] = "Pole parkla! Debug"
                manifestPlaceholders["ppShortcutsResource"] = "@xml/shortcuts_debug"
                signingConfig = signingConfigs.getByName("ci")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (ppBuildChannel == "pr") {
                applicationIdSuffix = ".preview"
                manifestPlaceholders["ppLauncherLabel"] = "Pole parkla! Preview"
                manifestPlaceholders["ppShortcutsResource"] = "@xml/shortcuts_preview"
            }
            val signingName = if (ppBuildChannel == "local") "release" else "ci"
            signingConfigs.findByName(signingName)?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.exifinterface)
    implementation(libs.google.play.services.location)
    implementation(libs.google.play.review)
    implementation(libs.google.mlkit.text.recognition)
    implementation(libs.microsoft.onnxruntime.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.locationtech.proj4j)
    implementation(libs.locationtech.proj4j.epsg)
    implementation(libs.maplibre.compose)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
