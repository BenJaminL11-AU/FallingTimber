plugins {
    java
    `maven-publish`
}

group = "io.github.benjaminl11au"
version = "1.0.0"

repositories {
    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.jar {
    archiveBaseName.set("FallingTimber")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "fallingtimber"
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            val githubRepository = System.getenv("GITHUB_REPOSITORY")
                ?: "BenJaminL11-AU/FallingTimber"
            url = uri("https://maven.pkg.github.com/$githubRepository")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
