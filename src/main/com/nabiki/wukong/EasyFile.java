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

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The class provides an easy way of managing file and directory. If it is directory,
 * it creates files and directories all as its sub-directories and files under this
 * directory. If it is a file, then it is just a file.
 */
public class EasyFile {
    private final String path;
    private final boolean isFile;
    private final Map<String, EasyFile> files = new ConcurrentHashMap<>();

    /**
     * Construct an file on the specified path.
     *
     * @param path path of the created file
     * @param isFile {@code true} to create file, {@code false} to create a directory
     * @throws IOException if file on the specified path exists but has wrong type,
     * or fail creating file or directory, or this object is not a directory
     */
    public EasyFile(String path, boolean isFile) throws IOException {
        this.path = Path.of(path).toAbsolutePath().toString();
        this.isFile = isFile;
        ensure(Path.of(this.path), this.isFile);
    }

    static void ensure(Path path, boolean isFile) throws IOException {
        Objects.requireNonNull(path, "path null");
        var file = path.toFile();
        if (file.exists()) {
            if ((file.isFile() && isFile) || (file.isDirectory() && !isFile))
                return;
            else
                throw new FileAlreadyExistsException(
                        "object exists but has wrong type");
        }
        // Not existent, or wrong type.
        if (isFile)
            Files.createFile(path);
        else
            Files.createDirectories(path);
    }

    void checkType() throws IOException {
        if (this.isFile)
            throw new IOException("this object is not directory");
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
    public EasyFile setDirectory(String key, String relPath) throws IOException {
        checkType();
        this.files.put(key, new EasyFile(
                Path.of(this.path, relPath).toAbsolutePath().toString(),
                false));
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
    public EasyFile setFile(String key, String relPath) throws IOException {
        checkType();
        this.files.put(key, new EasyFile(
                Path.of(this.path, relPath).toAbsolutePath().toString(),
                true));
        return this;
    }

    /**
     * Get object with the specified key. if the key doesn't exist, return
     * {@code null}.
     *
     * @param key key mapped to the file
     * @return file object, or {@code null} if the key doesn't exist
     */
    public EasyFile get(String key) {
        return this.files.get(key);
    }

    /**
     * Get {@link Path} of this object.
     *
     * @return {@link Path} of this object
     */
    public Path path() {
        return Path.of(this.path);
    }

    /**
     * Check if this object is a file. The method returns false if it is a directory.
     *
     * @return {@code true} if this object is a file, {@code false} otherwise
     */
    public boolean isFile() {
        return this.isFile;
    }
}
