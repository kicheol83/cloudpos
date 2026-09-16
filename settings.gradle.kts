rootProject.name = "cloudpos"

include(
    "libs:ids",
    "libs:security",
    "libs:tenancy",
    "libs:web",
    "services:gateway",
    "services:identity",
)

dependencyResolutionManagement {
    repositories { mavenCentral() }
}
