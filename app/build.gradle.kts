import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("jacoco")
}

val localBackendBaseUrl = providers
    .gradleProperty("agroBackendBaseUrl")
    .orElse(providers.environmentVariable("AGRO_BACKEND_BASE_URL"))
    .orElse("http://10.0.2.2:8080/")
    .get()

require(localBackendBaseUrl.endsWith("/")) {
    "La URL del backend debe terminar en /."
}

val releaseBackendBaseUrl = providers
    .gradleProperty("agroReleaseBackendBaseUrl")
    .orElse(providers.environmentVariable("AGRO_RELEASE_BACKEND_BASE_URL"))
    .orElse("https://backend.invalid/")
    .get()

require(releaseBackendBaseUrl.endsWith("/")) {
    "La URL release del backend debe terminar en /."
}
require(releaseBackendBaseUrl.startsWith("https://")) {
    "La variante release exige una URL HTTPS."
}

val releaseStoreFile = providers.environmentVariable("AGRO_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("AGRO_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("AGRO_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("AGRO_RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
)
val hasAnyReleaseSigningValue = releaseSigningValues.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }

require(!hasAnyReleaseSigningValue || hasCompleteReleaseSigning) {
    "La firma release requiere AGRO_RELEASE_STORE_FILE, AGRO_RELEASE_STORE_PASSWORD, " +
        "AGRO_RELEASE_KEY_ALIAS y AGRO_RELEASE_KEY_PASSWORD."
}

android {
    namespace = "cl.ariztia.bebederos"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    defaultConfig {
        applicationId = "cl.ariztia.bebederos"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "BACKEND_BASE_URL", "\"https://backend.invalid/\"")
    }
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        if (hasCompleteReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("debug") {
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                "\"$localBackendBaseUrl\""
            )
        }
        getByName("release") {
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                "\"$releaseBackendBaseUrl\""
            )
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}
jacoco {
    toolVersion = "0.8.13"
}
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")

    implementation("androidx.navigation:navigation-compose:2.9.5")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")

    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("androidx.room:room-runtime:2.8.1")
    implementation("androidx.room:room-ktx:2.8.1")
    ksp("androidx.room:room-compiler:2.8.1")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.1")

}

val androidLogicCoverageIncludes = listOf(
    "cl/ariztia/bebederos/data/model/**",
    "cl/ariztia/bebederos/data/remote/BasicAuthInterceptor*",
    "cl/ariztia/bebederos/data/remote/BasicAuthorization*",
    "cl/ariztia/bebederos/data/remote/InMemoryCredentials*",
    "cl/ariztia/bebederos/data/remote/mapper/**",
    "cl/ariztia/bebederos/data/repository/**",
    "cl/ariztia/bebederos/viewmodel/CatalogUiState*",
    "cl/ariztia/bebederos/viewmodel/AmbientWeatherUiState*",
    "cl/ariztia/bebederos/viewmodel/LoginUiState*",
    "cl/ariztia/bebederos/viewmodel/RemoteAlertUiState*",
    "cl/ariztia/bebederos/viewmodel/RemoteFlushingUiState*",
    "cl/ariztia/bebederos/viewmodel/RemotePhotoUiState*",
    "cl/ariztia/bebederos/viewmodel/WaterTemperatureInput*",
    "cl/ariztia/bebederos/viewmodel/FlushingSubmissionGate*",
    "cl/ariztia/bebederos/viewmodel/FlushingTimeProvider*",
    "cl/ariztia/bebederos/viewmodel/TemperatureHistoryFilter*"
)

val debugLogicClassDirectories = files(
    layout.buildDirectory.dir("tmp/kotlin-classes/debug"),
    layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")
).asFileTree.matching {
    include(androidLogicCoverageIncludes)
    exclude(
        "**/BuildConfig.*",
        "**/*_Impl.*",
        "**/*JsonAdapter.*"
    )
}

val debugCoverageExecutionData = fileTree(layout.buildDirectory) {
    include(
        "jacoco/testDebugUnitTest.exec",
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
    )
}

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Genera cobertura JaCoCo de la lógica Android comprobable en JVM."

    classDirectories.setFrom(debugLogicClassDirectories)
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(debugCoverageExecutionData)

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoDebugCoverageVerification") {
    dependsOn("jacocoDebugUnitTestReport")
    group = "verification"
    description = "Exige al menos 80% de cobertura de líneas en la lógica Android seleccionada."

    classDirectories.setFrom(debugLogicClassDirectories)
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(debugCoverageExecutionData)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}
