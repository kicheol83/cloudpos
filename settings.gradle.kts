rootProject.name = "cloudpos"

include(
    "libs:tenancy",
    "libs:web",
    "services:gateway",
    "services:identity",
)

dependencyResolutionManagement {
    repositories { mavenCentral() }
}
