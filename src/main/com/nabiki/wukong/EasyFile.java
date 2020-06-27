/*
 * Copyright (c) 2020 Hongbao Chen <chenhongbao@outlook.com>
 *
 * Licensed under the  GNU Affero General Public License v3.0 and you may not use
 * this file except in compliance with the  License. You may obtain a copy of the
 * License at
 *
 *                    https://www.gnu.org/licenses/agpl-3.0.txt
 *
 * Permission is hereby  granted, free of charge, to any  person obtaining a copy
 * of this software and associated  documentation files (the "Software"), to deal
 * in the Software  without restriction, including without  limitation the rights
 * to  use, copy,  modify, merge,  publish, distribute,  sublicense, and/or  sell
 * copies  of  the Software,  and  to  permit persons  to  whom  the Software  is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE  IS PROVIDED "AS  IS", WITHOUT WARRANTY  OF ANY KIND,  EXPRESS OR
 * IMPLIED,  INCLUDING BUT  NOT  LIMITED TO  THE  WARRANTIES OF  MERCHANTABILITY,
 * FITNESS FOR  A PARTICULAR PURPOSE AND  NONINFRINGEMENT. IN NO EVENT  SHALL THE
 * AUTHORS  OR COPYRIGHT  HOLDERS  BE  LIABLE FOR  ANY  CLAIM,  DAMAGES OR  OTHER
 * LIABILITY, WHETHER IN AN ACTION OF  CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE  OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.nabiki.wukong;

import com.nabiki.wukong.annotation.OutTeam;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The class provides an easy way of managing file and directory. If it is directory,
 * it creates files and directories all as its sub-directories and files under this
 * directory. If it is a file, then it is just a file.
 *
 * <p><b>Instance of the class is thread-safe.</b>
 * </p>
 */
public class EasyFile {
    private final String path;
    private final Boolean isFile;
    private final Map<String, EasyFile> files = new ConcurrentHashMap<>();

    /**
     * Construct an file on the specified path.
     *
     * @param path path of the created file
     * @param isFile {@code true} to create file, {@code false} to create a directory
     * @throws IOException if file on the specified path exists but has wrong type,
     * or fail creating file or directory, or this object is not a directory
     */
    @OutTeam
    public EasyFile(String path, boolean isFile) throws IOException {
        this.path = Path.of(path).toAbsolutePath().toString();
        this.isFile = isFile;
        ensure(Path.of(this.path), this.isFile);
    }

    /*
    The specified path may exist or not exist.
    If it does not exist, create it.
    If the file is directory and exists, read .index file in this directory and
    load information.
    */
    private void ensure(Path path, boolean isFile) throws IOException {
        Objects.requireNonNull(path, "path null");
        var file = path.toFile();
        if (file.exists()) {
            if ((file.isFile() && !isFile) || (!file.isFile() && isFile))
                throw new FileAlreadyExistsException(
                        "object exists but has wrong type");
            // Load directory.
            if ((file.isDirectory() && !isFile)) {
                Map<String, String> m;
                try {
                    m = readIndex();
                } catch (IOException e) {
                    // Fail reading index, the directory exists but hasn't been
                    // touched by this class.
                    return;
                }
                for (var entry : m.entrySet()) {
                    var p = Path.of(this.path, entry.getValue());
                    if (!p.toFile().exists())
                        throw new IOException("file missing " + p.toString());
                    this.files.put(entry.getKey(),
                            new EasyFile(p.toString(), p.toFile().isFile()));
                }
            }
        } else {
            // Not existent, or wrong type.
            if (isFile)
                Files.createFile(path);
            else
                Files.createDirectories(path);
        }
    }

    private void checkDir() throws IOException {
        if (this.isFile)
            throw new IOException("this object is not directory");
    }

    private Map<String, String> readIndex() throws IOException {
        var r = new HashMap<String, String>();
        var indexPath = Path.of(this.path, ".index");
        synchronized (this) {
            try (BufferedReader br = new BufferedReader(
                    new FileReader(indexPath.toFile()))) {
                String key, path;
                while ((key = readLine(br)) != null) {
                    path = br.readLine();
                    if (path == null)
                        throw new IOException("last key misses value: " + key);
                    else
                        r.put(key.trim(), path.trim());
                }
                return r;
            }
        }
    }

    private String readLine(BufferedReader reader) throws IOException {
        String line;
        do {
            line = reader.readLine();
        } while (line != null && line.length() == 0);
        return line;
    }

    private void writeIndex(String key, String relPath) throws IOException {
        var indexPath = Path.of(this.path, ".index");
        synchronized (this) {
            try (FileWriter fw
                         = new FileWriter(indexPath.toFile(), true)) {
                fw.write(key);
                fw.write(System.lineSeparator());
                fw.write(relPath);
                fw.write(System.lineSeparator());
            }
        }
    }

    /**
     * Create sub directory with the specified path. The {@code key} is a key in map
     * that associates {@code key} with object.
     *
     * <p>The object created with the {@code key} can be retrieved with
     * {@link EasyFile#get(String)}.
     * </p>
     *
     * @param key key of the object
     * @param relPath relative path of the directory to this object
     * @return new object representing the specified directory
     * @throws IOException if the specified path exists but not a directory, or
     * failed creating the directory, or this file object is not a directory
     */
    @OutTeam
    public EasyFile setDirectory(String key, String relPath) throws IOException {
        checkDir();
        this.files.put(key, new EasyFile(
                Path.of(this.path, relPath).toAbsolutePath().toString(),
                false));
        writeIndex(key, relPath);
        return this;
    }

    /**
     * Create file on the specified relative path under this object. {@code key}
     * is a key in map that associates the {@code key} with object.
     *
     * @param key key of this object
     * @param relPath relative path of the file to this file object
     * @return new file object representing the specified file
     * @throws IOException if the specified path exists but not a file, or failed
     * creating the file, or this file object is not a directory
     */
    @OutTeam
    public EasyFile setFile(String key, String relPath) throws IOException {
        checkDir();
        this.files.put(key, new EasyFile(
                Path.of(this.path, relPath).toAbsolutePath().toString(),
                true));
        writeIndex(key, relPath);
        return this;
    }

    /**
     * Get object with the specified key. if the key doesn't exist, return
     * {@code null}.
     *
     * @param key key mapped to the file
     * @return file object, or {@code null} if the key doesn't exist
     */
    @OutTeam
    public EasyFile get(String key) {
        return this.files.get(key);
    }

    /**
     * Search for object with the specified key recursively.
     *
     * @param key key of the object
     * @return {@link Collection} of {@link EasyFile} objects with the specified key
     */
    @OutTeam
    public Collection<EasyFile> recursiveGet(String key) {
        var r = new HashSet<EasyFile>();
        var f0 = this.files.get(key);
        if (f0 != null)
            r.add(f0);
        for (var v : this.files.values())
            if (!v.isFile())
                r.addAll(v.recursiveGet(key));
        return r;
    }

    /**
     * Get {@link Path} of this object.
     *
     * @return {@link Path} of this object
     */
    @OutTeam
    public Path path() {
        return Path.of(this.path);
    }

    /**
     * Get {@link File} of this object.
     *
     * @return {@link File} of this object
     */
    @OutTeam
    public File file() {
        return path().toFile();
    }

    /**
     * Check if this object is a file. The method returns false if it is a directory.
     *
     * @return {@code true} if this object is a file, {@code false} otherwise
     */
    @OutTeam
    public boolean isFile() {
        return this.isFile;
    }

    /**
     * Check whether this object represents an empty file or directory.
     *
     * @return {@code true} if the object represents an empty file or directory,
     * {@code false} otherwise
     */
    @OutTeam
    public boolean isEmpty() {
        if (this.isFile)
            return path().toFile().length() == 0;
        else
            return this.files.size() == 0;
    }
}
