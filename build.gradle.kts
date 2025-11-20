plugins {
    base
    id("com.diffplug.spotless") version "6.25.0"


}

repositories {
    mavenCentral()
}


spotless {
    format("misc") {
        target("*.md", "*.yml", "*.yaml", "*.json", "*.toml", "*.gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("**/*.gradle.kts", "**/*.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
    java {
        target("**/src/**/*.java")
        palantirJavaFormat("2.78.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Minimal quality gate: formatting + tests
val qualityGate = tasks.register("qualityGate") {
    group = "verification"
    description = "Runs the minimal quality automation gate"
    dependsOn("spotlessCheck", "check")
}

subprojects {
    repositories { mavenCentral() }
}


// Wire root check to all subprojects' check tasks
tasks.named("check").configure {
    dependsOn(subprojects.map { it.path + ":check" })
}
