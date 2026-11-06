plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Version : la CI fournit le nom et un code croissant (numéro de build) pour que chaque nouvel APK
// s'installe par-dessus le précédent. En local : gradle.properties.
val appVersionName = providers.environmentVariable("CDR_VERSION_NAME").orNull
    ?: providers.gradleProperty("cdr.versionName").get()
val appVersionCode = providers.environmentVariable("CDR_VERSION_CODE").orNull?.toIntOrNull() ?: 1

android {
    namespace = "io.github.slaynaw.carnetduraider"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.slaynaw.carnetduraider"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Jamais de clé de publication ici : l'APK est signé avec la clé de debug (clé de test).
            // Seul le job « Publication officielle » de la CI le re-signe avec la clé définitive,
            // après validation (docs/SIGNATURE.md).
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Code de version de l'appli installée, comparé à celui des versions publiées.
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // Rapport texte (build/reports/lint-results-release.txt), affiché par la CI.
        textReport = true
        // Les mises à jour de dépendances sont gérées manuellement (catalogue de versions).
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    // Pas de bloc de métadonnées de dépendances chiffré par Google dans l'APK (inutile hors Play Store).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    // Filmer mon inventaire : aperçu de la caméra et analyse des images, sur le téléphone (rien n'est envoyé).
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
}
