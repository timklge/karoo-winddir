fun getLocalProperty(key: String, file: String = "local.properties"): String? {
    val properties = java.util.Properties()
    val localProperties = File(file)
    if (localProperties.isFile) {
        java.io.InputStreamReader(java.io.FileInputStream(localProperties), Charsets.UTF_8).use { reader ->
            properties.load(reader)
        }
    } else error("File from not found")

    return properties.getProperty(key)
}
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

val env: MutableMap<String, String> = System.getenv()
val gprUser = if(env.containsKey("GPR_USER")) env["GPR_USER"] else getLocalProperty("gpr.user")
val gprKey = if(env.containsKey("GPR_KEY")) env["GPR_KEY"] else getLocalProperty("gpr.key")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // karoo-ext from Github Packages
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = gprUser
                password = gprKey
            }
        }

        // mapbox
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
        }
    }
}

rootProject.name = "Karoo Headwind"
include("app")
