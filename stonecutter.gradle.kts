plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2-fabric" /* [SC] DO NOT EDIT */

stonecutter parameters {
    constants["fabric"] = true
    constants["client"] = true
    constants["release"] = true

    replacements {
        string(current.parsed >= "26.1") {
            replace("classTweaker v2 named", "classTweaker v2 official")
        }
    }
}

tasks.register("chiseledBuild") {
    group = "project"
    description = "Builds Null Tweaks for every configured Minecraft target."
    dependsOn(
        ":26.1-fabric:buildAndCollect",
        ":26.1.1-fabric:buildAndCollect",
        ":26.1.2-fabric:buildAndCollect",
        ":26.2-fabric:buildAndCollect",
    )
}
