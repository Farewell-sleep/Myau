import org.apache.commons.lang3.SystemUtils
plugins {
    idea
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}
//Constants:
val baseGroup: String by project
val mcVersion: String by project
val version: String by project
val mixinGroup = "$baseGroup.mixin"
val modid: String by project
val jarName: String by project
val transformerFile = file("src/main/resources/accesstransformer.cfg")
// Toolchains:
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}
// Minecraft configuration:
loom {
    log4jConfigs.from(file("log4j2.xml"))
    launchConfigs {
        "client" {
            // If you don't want mixins, remove these lines
            property("mixin.debug", "true")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        "client" {
            if (SystemUtils.IS_OS_MAC_OSX) {
                // This argument causes a crash on macOS
                vmArgs.remove("-XstartOnFirstThread")
            }
        }
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        // If you don't want mixins, remove this lines
        mixinConfig("mixins.$modid.json")
	    if (transformerFile.exists()) {
			println("Installing access transformer")
		    accessTransformer(transformerFile)
	    }
    }
    // If you don't want mixins, remove these lines
    mixin {
        defaultRefmapName.set("mixins.$modid.refmap.json")
    }
}
sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}
// Dependencies:
repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
    // If you don't want to log in with your real minecraft account, remove this line
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}
val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}
dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")
    // If you don't want mixins, remove these lines
    shadowImpl("org.ow2.asm:asm:9.7")
    shadowImpl("org.ow2.asm:asm-tree:9.7")
    shadowImpl("org.ow2.asm:asm-commons:9.7")
    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")
    // If you don't want to log in with your real minecraft account, remove this line
    runtimeOnly("me.djtheredstoner:DevAuth-forge-legacy:1.2.1")
}
// Tasks:
tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
}
tasks.withType(org.gradle.jvm.tasks.Jar::class) {
    archiveBaseName.set(jarName)
    manifest.attributes.run {
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        // If you don't want mixins, remove these lines
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        this["MixinConfigs"] = "mixins.$modid.json"
	    if (transformerFile.exists())
			this["FMLAT"] = "${modid}_at.cfg"
    }
}
tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcversion", mcVersion)
    inputs.property("modid", modid)
    inputs.property("basePackage", baseGroup)
    filesMatching(listOf("mcmod.info", "mixins.$modid.json","version.json")) {
        expand(inputs.properties)
    }
    rename("accesstransformer.cfg", "META-INF/${modid}_at.cfg")
}
val remapJar by tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    from(tasks.shadowJar)
    input.set(tasks.shadowJar.get().archiveFile)
}
tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}
tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    archiveClassifier.set("non-obfuscated-with-deps")
    configurations = listOf(shadowImpl)
    doLast {
        configurations.forEach {
            println("Copying dependencies into mod: ${it.files}")
        }
    }
    // If you want to include other dependencies and shadow them, you can relocate them in here
    fun relocate(name: String) = relocate(name, "$baseGroup.deps.$name")
    relocate("org.objectweb.asm", "$baseGroup.shadow.asm")
}
tasks.assemble.get().dependsOn(tasks.remapJar)

// ---- myau injection loader (C++/CMake, from InjectMyau) ----
// Builds myau_native.dll + myau_loader.exe via CMake, embedding the client jar,
// then copies the loaders into build/libs so a single `gradlew build` produces
// both the mod jar and the injector.
val buildMyauLoader by tasks.creating(Exec::class) {
    group = "build"
    description = "Build the myau injection loader (CMake) and copy it to build/libs"
    dependsOn(tasks.remapJar)
    workingDir = file("myau-natives")
    val loaderDir = projectDir.resolve("myau-natives")
    doFirst {
        val jarPath = tasks.remapJar.get().archiveFile.get().asFile.absolutePath
        val jdkHome = System.getenv("JAVA_HOME")?.takeIf { it.isNotBlank() }
            ?: "C:/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot"
        commandLine(
            "cmake", "-S", ".", "-B", "build", "-A", "x64",
            "-DJAVA_INCLUDE=$jdkHome/include",
            "-DCLIENT_JAR=$jarPath"
        )
    }
    doLast {
        val pb = ProcessBuilder("cmake", "--build", "build", "--config", "Release")
        pb.directory(loaderDir)
        pb.inheritIO()
        val buildProc = pb.start()
        if (buildProc.waitFor() != 0) {
            throw GradleException("cmake --build failed for myau loader")
        }
        val libsDir = layout.buildDirectory.dir("libs").get().asFile
        for (exe in listOf("myau_loader.exe", "myau_loader_local.exe")) {
            val src = loaderDir.resolve("build/Release/$exe")
            if (src.exists()) {
                copy {
                    from(src)
                    into(libsDir)
                }
                println("myau loader -> build/libs/$exe")
            }
        }
    }
}
tasks.assemble.get().dependsOn(buildMyauLoader)
// ---- myau simple injector GUI (C#, hand-written) ----
// Compiles the minimal MyauInjector.exe (WinForms) with the system csc and
// copies it together with myau_native.dll into build/libs. Injection uses the
// same technique as InjectMyau: LoadLibraryW via CreateRemoteThread, with the
// JVMTI agent DLL finding the running JVM itself in DllMain.
val buildMyauInjector by tasks.creating(DefaultTask::class) {
    group = "build"
    description = "Compile the simple MyauInjector GUI and copy it + the native dll to build/libs"
    dependsOn(buildMyauLoader)
    val injectorDir = projectDir.resolve("myau-injector")
    val loaderDir = projectDir.resolve("myau-natives")
    doLast {
        val libsDir = layout.buildDirectory.dir("libs").get().asFile
        val csc = file("${System.getenv("WINDIR") ?: "C:/Windows"}/Microsoft.NET/Framework64/v4.0.30319/csc.exe")
        if (!csc.exists()) {
            throw GradleException("csc.exe not found: $csc")
        }
        val src = injectorDir.resolve("SimpleMyauInjector.cs")
        val exe = libsDir.resolve("MyauInjector.exe")
        val pb = ProcessBuilder(
            csc.absolutePath, "/nologo", "/target:winexe", "/platform:x64", "/optimize",
            "/out:${exe.absolutePath}",
            "/r:System.dll", "/r:System.Drawing.dll", "/r:System.Windows.Forms.dll",
            "/r:System.Management.dll",
            src.absolutePath
        )
        pb.inheritIO()
        val proc = pb.start()
        if (proc.waitFor() != 0) {
            throw GradleException("csc failed for MyauInjector")
        }
        println("MyauInjector -> build/libs/MyauInjector.exe")
        // make sure the JVMTI agent dll sits next to the injector
        val dll = loaderDir.resolve("build/Release/myau_native.dll")
        if (dll.exists()) {
            copy {
                from(dll)
                into(libsDir)
            }
            println("myau_native.dll -> build/libs/myau_native.dll")
        }
    }
}
tasks.assemble.get().dependsOn(buildMyauInjector)
