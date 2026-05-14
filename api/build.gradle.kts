dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.60-stable")
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            groupId = "github.nighter"
            artifactId = "emp-api"
            from(components["java"])

            pom {
                name.set("EMP API")
                description.set("API for EMP (Economy Multiplayer) plugin")
                url.set("https://github.com/NighterDevelopment/SmartSpawner")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("nighter")
                        name.set("Nighter")
                        email.set("notnighter@gmail.com")
                    }
                }
            }
        }
    }
}

