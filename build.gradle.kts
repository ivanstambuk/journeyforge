plugins {
    base
    id("com.diffplug.spotless") version "6.25.0"


}

repositories {
    mavenCentral()
}


spotless {
    format("misc") {
        target("*.md", "*.json", "*.toml", "*.gitignore")
        // Enforce basic whitespace rules for all misc text files.
        replaceRegex("noTabs", "\t", "  ")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("yaml") {
        // YAML formatting (Related: Q-003 in docs/4-architecture/open-questions.md)
        target("**/*.yml", "**/*.yaml")
        prettier()
            .config(
                mapOf(
                    "tabWidth" to 2,
                    "useTabs" to false,
                    "singleQuote" to false,
                    "printWidth" to 100,
                    "proseWrap" to "preserve",
                    "trailingComma" to "none",
                    "endOfLine" to "lf"
                )
            )
            .nodeExecutable("node")
        // Enforce "no tabs anywhere" for YAML as per spec-format.md / Q-003.
        replaceRegex("noTabsYaml", "\t", "  ")
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
