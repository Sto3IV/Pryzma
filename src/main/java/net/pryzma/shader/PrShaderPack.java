package net.pryzma.shader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * One shader pack: a folder or a zip holding a {@code shaders/} directory, possibly one level
 * down ({@code MyPack/shaders/}), as OptiFine accepts. Paths are relative to the directory that
 * contains {@code shaders/} and use forward slashes.
 */
public final class PrShaderPack implements AutoCloseable {
    private final String name;
    private final Path root;
    private final FileSystem zip;

    private PrShaderPack(String name, Path root, FileSystem zip) {
        this.name = name;
        this.root = root;
        this.zip = zip;
    }

    /** Opens a pack, or returns {@code null} when {@code file} holds no {@code shaders/} directory. */
    public static PrShaderPack open(Path file) throws IOException {
        String name = file.getFileName().toString();
        if (Files.isDirectory(file)) {
            Path root = findRoot(file);
            return root == null ? null : new PrShaderPack(name, root, null);
        }
        if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return null;
        }
        FileSystem fs = FileSystems.newFileSystem(file);
        Path root = findRoot(fs.getPath("/"));
        if (root == null) {
            fs.close();
            return null;
        }
        return new PrShaderPack(name, root, fs);
    }

    private static Path findRoot(Path dir) throws IOException {
        if (Files.isDirectory(dir.resolve("shaders"))) {
            return dir;
        }
        try (Stream<Path> children = Files.list(dir)) {
            for (Path child : (Iterable<Path>) children::iterator) {
                if (Files.isDirectory(child) && Files.isDirectory(child.resolve("shaders"))) {
                    return child;
                }
            }
        }
        return null;
    }

    public String name() {
        return name;
    }

    private Path resolve(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        return root.resolve(p);
    }

    public boolean exists(String path) {
        return Files.isRegularFile(resolve(path));
    }

    public boolean isDirectory(String path) {
        return Files.isDirectory(resolve(path));
    }

    /** The file as UTF-8 text, or {@code null} when it does not exist. */
    public String read(String path) {
        Path p = resolve(path);
        if (!Files.isRegularFile(p)) {
            return null;
        }
        try {
            String text = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (IOException e) {
            return null;
        }
    }

    public byte[] readBytes(String path) throws IOException {
        return Files.readAllBytes(resolve(path));
    }

    /** Names of the subdirectories of {@code dir}. */
    public List<String> directories(String dir) {
        List<String> out = new ArrayList<>();
        Path p = resolve(dir);
        if (!Files.isDirectory(p)) {
            return out;
        }
        try (Stream<Path> children = Files.list(p)) {
            children.filter(Files::isDirectory).forEach(c -> {
                String n = c.getFileName().toString();
                out.add(n.endsWith("/") ? n.substring(0, n.length() - 1) : n);
            });
        } catch (IOException ignored) {
            // An unreadable directory has no worlds.
        }
        return out;
    }

    @Override
    public void close() throws IOException {
        if (zip != null) {
            zip.close();
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
