pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "xiaojia-app"

// 模块化：core 是基础（连接、登录、安全存储、功能接口）；每个功能一个模块。
// 以后加设备控制、R3 审批、语音对话，就新建 feature-xxx 模块并在 app 里登记。
include(":app", ":core", ":feature-today", ":feature-chat", ":feature-assistant")
