package cn.xiaoshuaixia.sparseverticalexpansion.client;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Bytecode-level contract check that the optional Sodium mixins' target members exist in the exact
 * Sodium JAR we compile against. This verifies the compatibility layer against the real runtime
 * artifact without loading Sodium (which would also pull in its jarjar'd Fabric API and would need
 * an OpenGL/display context). It is the concrete form of the "bytecode verification against the
 * actual version JAR" step required by the Sodium compatibility design.
 */
public final class SodiumBytecodeContractTest {
    private static final String RSM = "net/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager";
    private static final String SECTION = "net/caffeinemc/mods/sodium/client/render/chunk/RenderSection";
    private static final String SLICE = "net/caffeinemc/mods/sodium/client/world/LevelSlice";
    private static final String CCS = "net/caffeinemc/mods/sodium/client/world/cloned/ClonedChunkSection";
    private static final String CACHE = "net/caffeinemc/mods/sodium/client/world/cloned/ClonedChunkSectionCache";
    private static final String WORLD = "net/caffeinemc/mods/sodium/client/render/SodiumWorldRenderer";

    public static void main(String[] args) throws Exception {
        String jarPath = System.getProperty("sodium.compile.jar");
        if (jarPath == null || jarPath.isBlank()) {
            throw new IllegalStateException("system property 'sodium.compile.jar' must point at the Sodium mod JAR");
        }
        Map<String, byte[]> classes = readClasses(jarPath);

        // RenderSectionManager mixin members.
        Sig rsm = signature(classes, RSM);
        rsm.requireMethod("onSectionAdded", "(III)V");
        rsm.requireMethod("isOutOfGraph", "(Lnet/minecraft/core/SectionPos;)Z");
        rsm.requireMethod("updateSectionInfo",
                "(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;"
                        + "Lnet/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo;)Z");
        rsm.requireMethod("connectNeighborNodes",
                "(Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSection;)V");
        rsm.requireMethod("markGraphDirty", "()V");
        rsm.requireMethod("scheduleRebuild", "(IIIZ)V");
        rsm.requireField("sectionByPosition", "Lit/unimi/dsi/fastutil/longs/Long2ReferenceMap;");
        rsm.requireField("regions",
                "Lnet/caffeinemc/mods/sodium/client/render/chunk/region/RenderRegionManager;");
        rsm.requireField("renderableSectionTree",
                "Lnet/caffeinemc/mods/sodium/client/render/chunk/tree/RemovableMultiForest;");
        rsm.requireField("level", "Lnet/minecraft/client/multiplayer/ClientLevel;");
        rsm.requireField("lastFrameAtTime", "J");

        // RenderSection members used by the scheduleRebuild re-arm injection.
        Sig section = signature(classes, SECTION);
        section.requireMethod("isBuilt", "()Z");
        section.requireMethod("getPendingUpdate", "()I");
        section.requireMethod("setPendingUpdate", "(IJ)V");

        // LevelSlice mixin members.
        Sig slice = signature(classes, SLICE);
        slice.requireMethod("prepare",
                "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/SectionPos;"
                        + "Lnet/caffeinemc/mods/sodium/client/world/cloned/ClonedChunkSectionCache;)"
                        + "Lnet/caffeinemc/mods/sodium/client/world/cloned/ChunkRenderContext;");

        // ClonedChunkSection light-injection mixin members.
        Sig ccs = signature(classes, CCS);
        ccs.requireMethod("copyLightArray",
                "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/LightLayer;"
                        + "Lnet/minecraft/core/SectionPos;)Lnet/minecraft/world/level/chunk/DataLayer;");

        // ClonedChunkSectionCache mixin members.
        Sig cache = signature(classes, CACHE);
        cache.requireMethod("clone",
                "(III)Lnet/caffeinemc/mods/sodium/client/world/cloned/ClonedChunkSection;");
        cache.requireField("level", "Lnet/minecraft/world/level/Level;");

        // SodiumWorldRenderer mixin members.
        Sig world = signature(classes, WORLD);
        world.requireMethod("initRenderer",
                "(Lnet/caffeinemc/mods/sodium/client/gl/device/CommandList;)V");
        world.requireField("level", "Lnet/minecraft/client/multiplayer/ClientLevel;");

        System.out.println("SodiumBytecodeContractTest OK");
    }

    private static Map<String, byte[]> readClasses(String jarPath) throws Exception {
        Map<String, byte[]> classes = new HashMap<>();
        try (JarFile jar = new JarFile(jarPath)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    classes.put(entry.getName(), in.readAllBytes());
                }
            }
        }
        return classes;
    }

    private static Sig signature(Map<String, byte[]> classes, String internalName) {
        byte[] bytes = classes.get(internalName + ".class");
        if (bytes == null) {
            throw new AssertionError("Sodium class not found in JAR: " + internalName);
        }
        Sig sig = new Sig();
        ClassReader reader = new ClassReader(bytes);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                sig.methods.add(name + descriptor);
                return null;
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                sig.fields.add(name + descriptor);
                return null;
            }
        }, 0);
        return sig;
    }

    private static final class Sig {
        private final Set<String> methods = new HashSet<>();
        private final Set<String> fields = new HashSet<>();

        void requireMethod(String name, String descriptor) {
            if (!methods.contains(name + descriptor)) {
                throw new AssertionError("Sodium method missing: " + name + descriptor);
            }
        }

        void requireField(String name, String descriptor) {
            if (!fields.contains(name + descriptor)) {
                throw new AssertionError("Sodium field missing: " + name + descriptor);
            }
        }
    }
}
