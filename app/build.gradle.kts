import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// O número da versão fica em version.properties (o lancar-versao.bat aumenta esse número).
val versao = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val codigoVersao = versao.getProperty("versionCode").trim().toInt()

// A chave de assinatura fica em keystore.properties, que NÃO vai para o Git.
val ksProps = Properties()
val ksArquivo = rootProject.file("keystore.properties")
if (ksArquivo.exists()) ksArquivo.inputStream().use { ksProps.load(it) }

android {
    namespace = "app.locadora24h.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.locadora24h.tv"
        minSdk = 22
        targetSdk = 34
        versionCode = codigoVersao
        versionName = "1.0.$codigoVersao"
        // Endereço que o app abre. app=1 avisa o site de que ele está dentro do app.
        buildConfigField("String", "START_URL", "\"https://locadora24h.vercel.app/?modo=tv&app=1\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (ksArquivo.exists()) {
            create("release") {
                storeFile = rootProject.file(ksProps.getProperty("storeFile"))
                storePassword = ksProps.getProperty("storePassword")
                keyAlias = ksProps.getProperty("keyAlias")
                keyPassword = ksProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (ksArquivo.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Só o SDK do Android: nada de AndroidX, para o app ficar pequeno.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
