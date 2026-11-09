pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "quarkforge-parent"

include(
    "quarkforge-bom",
    "quarkforge-core",
    "quarkforge-web",
    "quarkforge-validation",
    "quarkforge-data",
    "quarkforge-audit",
    "quarkforge-crypto",
    "quarkforge-cache",
    "quarkforge-dict",
    "quarkforge-excel",
    "quarkforge-migration",
    "quarkforge-security",
    "quarkforge-redis",
    "quarkforge-ratelimit",
    "quarkforge-degrade",
    "quarkforge-idempotency",
    "quarkforge-messaging",
    "quarkforge-messaging-rabbitmq",
    "quarkforge-outbox",
    "quarkforge-rpc",
    "quarkforge-scheduler",
    "quarkforge-notification",
    "quarkforge-observability",
    "quarkforge-test",
    // "examples:mall-service",
)
