import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Checks that a plugin jar only links against Adventure members that exist in the Adventure
 * version the server provides (e.g. Adventure 5 on Paper 26.x).
 * <p>
 * Usage: {@code java AdventureLinkageCheck.java <plugin jar or class dir> <server adventure jar>...}
 * <p>
 * Every field, method and class reference to {@code net.kyori.adventure} that the server jars
 * provide is resolved against them, as the JVM would at runtime, and every class extending or
 * implementing a sealed Adventure type is reported. Adventure's own classes bundled in the jar
 * are skipped: Paper loads its own copies instead. Exits with status 1 if anything is broken.
 */
public class AdventureLinkageCheck {

    private static final String PREFIX = "net/kyori/";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: AdventureLinkageCheck <plugin jar|dir> <server adventure jar>...");
            System.exit(2);
        }
        final URL[] serverUrls = new URL[args.length - 1];
        for (int i = 1; i < args.length; i++) {
            serverUrls[i - 1] = new File(args[i]).toURI().toURL();
        }
        final URLClassLoader server = new URLClassLoader(serverUrls, ClassLoader.getPlatformClassLoader());

        final List<ClassRefs> plugin = new ArrayList<>();
        for (byte[] bytes : readClasses(Path.of(args[0]))) {
            plugin.add(ClassRefs.parse(bytes));
        }
        final Set<String> bundled = new HashSet<>();
        plugin.forEach(refs -> bundled.add(refs.name));

        final Set<String> problems = new TreeSet<>();
        int checked = 0;
        for (ClassRefs refs : plugin) {
            if (refs.name.startsWith(PREFIX)) {
                continue;
            }
            for (String type : refs.supertypes) {
                final Class<?> parent = load(server, type);
                if (parent != null && parent.isSealed() && !permits(parent, refs.name)) {
                    problems.add(refs.name + " extends/implements sealed " + type);
                }
            }
            for (String type : refs.classes) {
                if (type.startsWith(PREFIX + "adventure/") && !bundled.contains(type)
                        && !exists(server, type)) {
                    problems.add(refs.name + " uses missing class " + type);
                }
            }
            for (String[] member : refs.members) {
                final Class<?> owner = load(server, member[0]);
                if (owner == null) {
                    continue; // Not provided by the server: shaded in the plugin or not Adventure
                }
                checked++;
                final boolean found = member[2].startsWith("(")
                        ? hasMethod(owner, member[1], member[2]) : hasField(owner, member[1], member[2]);
                if (!found) {
                    problems.add(refs.name + " -> " + member[0] + "." + member[1] + member[2]);
                }
            }
        }

        problems.forEach(p -> System.out.println("BROKEN: " + p));
        System.out.printf("Checked %d Adventure member references, %d broken%n", checked, problems.size());
        System.exit(problems.isEmpty() ? 0 : 1);
    }

    private static List<byte[]> readClasses(Path path) throws IOException {
        final List<byte[]> classes = new ArrayList<>();
        if (Files.isDirectory(path)) {
            try (var files = Files.walk(path)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                    classes.add(Files.readAllBytes(file));
                }
            }
            return classes;
        }
        try (JarFile jar = new JarFile(path.toFile())) {
            for (JarEntry entry : jar.stream().toList()) {
                if (entry.getName().endsWith(".class") && !entry.getName().startsWith("META-INF/versions/")) {
                    try (InputStream in = jar.getInputStream(entry)) {
                        classes.add(in.readAllBytes());
                    }
                }
            }
        }
        return classes;
    }

    private static Class<?> load(ClassLoader loader, String internalName) {
        if (!internalName.startsWith(PREFIX)) {
            return null;
        }
        try {
            return Class.forName(internalName.replace('/', '.'), false, loader);
        } catch (Throwable e) {
            return null;
        }
    }

    private static boolean exists(ClassLoader loader, String internalName) {
        return loader.getResource(internalName + ".class") != null;
    }

    private static boolean permits(Class<?> sealed, String internalName) {
        for (Class<?> permitted : sealed.getPermittedSubclasses()) {
            if (permitted.getName().equals(internalName.replace('/', '.'))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMethod(Class<?> owner, String name, String descriptor) {
        final Set<Class<?>> seen = new HashSet<>();
        final List<Class<?>> queue = new ArrayList<>(List.of(owner));
        queue.add(Object.class);
        while (!queue.isEmpty()) {
            final Class<?> type = queue.remove(0);
            if (!seen.add(type)) {
                continue;
            }
            try {
                if (name.equals("<init>")) {
                    for (Constructor<?> c : type.getDeclaredConstructors()) {
                        if (descriptor.equals(descriptor(c.getParameterTypes(), void.class))) {
                            return true;
                        }
                    }
                    return false;
                }
                for (Method m : type.getDeclaredMethods()) {
                    if (m.getName().equals(name)
                            && descriptor.equals(descriptor(m.getParameterTypes(), m.getReturnType()))) {
                        return true;
                    }
                }
            } catch (Throwable e) {
                return true; // Can't introspect (missing optional dependency); don't guess
            }
            if (type.getSuperclass() != null) {
                queue.add(type.getSuperclass());
            }
            queue.addAll(List.of(type.getInterfaces()));
        }
        return false;
    }

    private static boolean hasField(Class<?> owner, String name, String descriptor) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                for (Field f : type.getDeclaredFields()) {
                    if (f.getName().equals(name) && descriptor.equals(descriptor(f.getType()))) {
                        return true;
                    }
                }
                for (Class<?> iface : type.getInterfaces()) {
                    if (hasField(iface, name, descriptor)) {
                        return true;
                    }
                }
            } catch (Throwable e) {
                return true;
            }
        }
        return false;
    }

    private static String descriptor(Class<?>[] params, Class<?> returnType) {
        final StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : params) {
            sb.append(descriptor(p));
        }
        return sb.append(')').append(descriptor(returnType)).toString();
    }

    private static String descriptor(Class<?> type) {
        if (type.isArray()) {
            return type.getName().replace('.', '/');
        }
        if (type.isPrimitive()) {
            return switch (type.getName()) {
                case "int" -> "I";
                case "long" -> "J";
                case "boolean" -> "Z";
                case "byte" -> "B";
                case "char" -> "C";
                case "short" -> "S";
                case "float" -> "F";
                case "double" -> "D";
                default -> "V";
            };
        }
        return "L" + type.getName().replace('.', '/') + ";";
    }

    /** The class, member and supertype references in one class file's constant pool. */
    private record ClassRefs(String name, List<String> supertypes, Set<String> classes, List<String[]> members) {

        static ClassRefs parse(byte[] bytes) throws IOException {
            final DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            in.readInt();
            in.readUnsignedShort();
            in.readUnsignedShort();
            final int count = in.readUnsignedShort();
            final String[] utf8 = new String[count];
            final int[] classIndex = new int[count];
            final int[][] refs = new int[count][];
            final int[] nameAndType = new int[count * 2];
            for (int i = 1; i < count; i++) {
                final int tag = in.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8[i] = in.readUTF();
                    case 7 -> classIndex[i] = in.readUnsignedShort();
                    case 8, 16, 19, 20 -> in.readUnsignedShort();
                    case 3, 4 -> in.readInt();
                    case 5, 6 -> {
                        in.readLong();
                        i++;
                    }
                    case 9, 10, 11 -> refs[i] = new int[]{in.readUnsignedShort(), in.readUnsignedShort()};
                    case 12 -> {
                        nameAndType[i * 2] = in.readUnsignedShort();
                        nameAndType[i * 2 + 1] = in.readUnsignedShort();
                    }
                    case 15 -> {
                        in.readUnsignedByte();
                        in.readUnsignedShort();
                    }
                    case 17, 18 -> in.readInt();
                    default -> throw new IOException("Unknown constant pool tag " + tag);
                }
            }
            in.readUnsignedShort();
            final String name = utf8[classIndex[in.readUnsignedShort()]];
            final List<String> supertypes = new ArrayList<>();
            final int superIndex = in.readUnsignedShort();
            if (superIndex != 0) {
                supertypes.add(utf8[classIndex[superIndex]]);
            }
            final int interfaces = in.readUnsignedShort();
            for (int i = 0; i < interfaces; i++) {
                supertypes.add(utf8[classIndex[in.readUnsignedShort()]]);
            }

            final Set<String> classes = new HashSet<>();
            final List<String[]> members = new ArrayList<>();
            for (int i = 1; i < count; i++) {
                if (classIndex[i] != 0) {
                    classes.add(utf8[classIndex[i]].replaceAll("^\\[+L|;$", ""));
                }
                if (refs[i] != null) {
                    final String owner = utf8[classIndex[refs[i][0]]];
                    final int nat = refs[i][1];
                    members.add(new String[]{owner, utf8[nameAndType[nat * 2]], utf8[nameAndType[nat * 2 + 1]]});
                }
            }
            return new ClassRefs(name, supertypes, classes, members);
        }
    }
}
