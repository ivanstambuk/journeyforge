import org.gradle.api.tasks.Exec

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

tasks.register<Exec>("lintOas") {
    group = "verification"
    description = "Lint OpenAPI specs under docs/ with Spectral (Q-005)."
    commandLine(
        "npx",
        "--yes",
        "@stoplight/spectral-cli",
        "lint",
        "--fail-severity=warn",
        "docs/3-reference/**/*.openapi.yaml"
    )
}

tasks.register<Exec>("lintArazzo") {
    group = "verification"
    description = "Lint Arazzo workflows under docs/ with Spectral (Q-005)."
    commandLine(
        "npx",
        "--yes",
        "@stoplight/spectral-cli",
        "lint",
        "-r",
        ".spectral-arazzo.yaml",
        "--fail-severity=warn",
        "docs/3-reference/examples/arazzo/*.arazzo.yaml"
    )
}

// Minimal quality gate: formatting + tests
val qualityGate = tasks.register("qualityGate") {
    group = "verification"
    description = "Runs the minimal quality automation gate"
    dependsOn("spotlessCheck", "check", "lintOas", "lintArazzo")
}

subprojects {
    repositories { mavenCentral() }
}


// Wire root check to all subprojects' check tasks
tasks.named("check").configure {
    dependsOn(subprojects.map { it.path + ":check" })
}
