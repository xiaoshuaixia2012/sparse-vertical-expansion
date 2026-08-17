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
 * Bytecode-level contract check that the optional Embeddium mixins' target members exist in the
 * exact Embeddium JAR we compile against. Embeddium's internals differ from Sodium (package layout,
 * {@code WorldSlice}, {@code ChunkUpdateType} enum, {@code world} field name), so this contract is
 * kept separate from {@link SodiumBytecodeContractTest}.
 */
public final class EmbeddiumBytecodeContractTest {
    private static final String RSM = "org/embeddedt/embeddium/impl/render/chunk/RenderSectionManager";
    private static final String SECTION = "org/embeddedt/embeddium/impl/render/chunk/RenderSection";
    private static final String UPDATE_TYPE = "org/embeddedt/embeddium/impl/render/chunk/ChunkUpdateType";
    private static final String SLICE = "org/embeddedt/embeddium/impl/world/WorldSlice";
    private static final String CACHE = "org/embeddedt/embeddium/impl/world/cloned/ClonedChunkSectionCache";
    private static final String CTX = "org/embeddedt/embeddium/impl/world/cloned/ChunkRenderContext";
    private static final String CULLER = "org/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller";
    private static final String WORLD = "org/embeddedt/embeddium/impl/render/EmbeddiumWorldRenderer";

    public static void main(String[] args) throws Exception {
        String jarPath = System.getProperty("embeddium.compile.jar");
        if (jarPath == null || jarPath.isBlank()) {
            throw new IllegalStateException("system property 'embeddium.compile.jar' must point at the Embeddium mod JAR");
        }
        Map<String, byte[]> classes = readClasses(jarPath);

        Sig rsm = signature(classes, RSM);
        rsm.requireMethod("onSectionAdded", "(III)V");
        rsm.requireMethod("updateSectionInfo",
                "(Lorg/embeddedt/embeddium/impl/render/chunk/RenderSection;"
                        + "Lorg/embeddedt/embeddium/impl/render/chunk/data/BuiltSectionInfo;)V");
        rsm.requireMethod("connectNeighborNodes",
                "(Lorg/embeddedt/embeddium/impl/render/chunk/RenderSection;)V");
        rsm.requireField("sectionByPosition", "Lit/unimi/dsi/fastutil/longs/Long2ReferenceMap;");
        rsm.requireField("regions", "Lorg/embeddedt/embeddium/impl/render/chunk/region/RenderRegionManager;");
        rsm.requireField("world", "Lnet/minecraft/client/multiplayer/ClientLevel;");
        rsm.requireField("needsUpdate", "Z");

        Sig section = signature(classes, SECTION);
        section.requireMethod("isBuilt", "()Z");
        section.requireMethod("getPendingUpdate", "()Lorg/embeddedt/embeddium/impl/render/chunk/ChunkUpdateType;");
        section.requireMethod("setPendingUpdate", "(Lorg/embeddedt/embeddium/impl/render/chunk/ChunkUpdateType;)V");
        section.requireMethod("getChunkX", "()I");
        section.requireMethod("getChunkY", "()I");
        section.requireMethod("getChunkZ", "()I");

        Sig updateType = signature(classes, UPDATE_TYPE);
        updateType.requireField("INITIAL_BUILD", "Lorg/embeddedt/embeddium/impl/render/chunk/ChunkUpdateType;");

        Sig slice = signature(classes, SLICE);
        slice.requireMethod("prepare",
                "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/SectionPos;"
                        + "Lorg/embeddedt/embeddium/impl/world/cloned/ClonedChunkSectionCache;)"
                        + "Lorg/embeddedt/embeddium/impl/world/cloned/ChunkRenderContext;");

        Sig cache = signature(classes, CACHE);
        cache.requireMethod("clone", "(III)Lorg/embeddedt/embeddium/impl/world/cloned/ClonedChunkSection;");
        cache.requireField("world", "Lnet/minecraft/world/level/Level;");

        Sig ctx = signature(classes, CTX);
        ctx.requireMethod("<init>",
                "(Lnet/minecraft/core/SectionPos;[Lorg/embeddedt/embeddium/impl/world/cloned/ClonedChunkSection;"
                        + "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)V");
        ctx.requireMethod("withMeshAppenders", "(Ljava/util/List;)Lorg/embeddedt/embeddium/impl/world/cloned/ChunkRenderContext;");

        Sig culler = signature(classes, CULLER);
        culler.requireMethod("init",
                "(Lorg/embeddedt/embeddium/impl/render/chunk/occlusion/OcclusionCuller$Visitor;"
                        + "Lorg/embeddedt/embeddium/impl/util/collections/WriteQueue;"
                        + "Lorg/embeddedt/embeddium/impl/render/viewport/Viewport;FZI)V");
        culler.requireMethod("isWithinFrustum",
                "(Lorg/embeddedt/embeddium/impl/render/viewport/Viewport;"
                        + "Lorg/embeddedt/embeddium/impl/render/chunk/RenderSection;)Z");
        culler.requireField("sections", "Lit/unimi/dsi/fastutil/longs/Long2ReferenceMap;");
        culler.requireField("world", "Lnet/minecraft/world/level/Level;");

        Sig world = signature(classes, WORLD);
        world.requireMethod("initRenderer", "(Lorg/embeddedt/embeddium/impl/gl/device/CommandList;)V");
        world.requireField("renderSectionManager", "Lorg/embeddedt/embeddium/impl/render/chunk/RenderSectionManager;");
        world.requireField("world", "Lnet/minecraft/client/multiplayer/ClientLevel;");

        System.out.println("EmbeddiumBytecodeContractTest OK");
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
            throw new AssertionError("Embeddium class not found in JAR: " + internalName);
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
                throw new AssertionError("Embeddium method missing: " + name + descriptor);
            }
        }

        void requireField(String name, String descriptor) {
            if (!fields.contains(name + descriptor)) {
                throw new AssertionError("Embeddium field missing: " + name + descriptor);
            }
        }
    }
}
