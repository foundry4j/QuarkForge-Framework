// QuarkForge BOM —— 只用于依赖版本对齐
// 业务方项目只需：
//   implementation(platform("io.github.foundry4j:quarkforge-bom:1.0.0"))
//   implementation(platform("io.quarkus.platform:quarkus-bom:3.40.1.1"))
plugins {
    `java-platform`
}

val quarkusBom: String by rootProject.extra
val mapstructVer: String by rootProject.extra
val poiVer: String by rootProject.extra

javaPlatform { allowDependencies() }

dependencies {
    // 导入 Quarkus BOM
    api(platform(quarkusBom))

    // 本框架自己的模块（版本固定为当前 project.version）
    api(project(":quarkforge-core"))
    api(project(":quarkforge-web"))
    api(project(":quarkforge-validation"))
    api(project(":quarkforge-data"))
    api(project(":quarkforge-audit"))
    api(project(":quarkforge-crypto"))
    api(project(":quarkforge-cache"))
    api(project(":quarkforge-dict"))
    api(project(":quarkforge-excel"))
    api(project(":quarkforge-migration"))
    api(project(":quarkforge-security"))
    api(project(":quarkforge-redis"))
    api(project(":quarkforge-ratelimit"))
    api(project(":quarkforge-degrade"))
    api(project(":quarkforge-idempotency"))
    api(project(":quarkforge-messaging"))
    api(project(":quarkforge-messaging-rabbitmq"))
    api(project(":quarkforge-outbox"))
    api(project(":quarkforge-rpc"))
    api(project(":quarkforge-scheduler"))
    api(project(":quarkforge-notification"))
    api(project(":quarkforge-observability"))
    api(project(":quarkforge-test"))

    // 独立三方依赖的版本约束
    constraints {
        api("org.mapstruct:mapstruct:$mapstructVer")
        api("org.mapstruct:mapstruct-processor:$mapstructVer")
        api("org.apache.poi:poi-ooxml:$poiVer")
    }
}
