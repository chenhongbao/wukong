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

package com.nabiki.wukong.cnf;

import com.nabiki.wukong.EasyFile;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class EasyFileTest {
    @Test
    public void nice() {
        try {
            final String rootPath = "C:\\Users\\chenh\\Desktop\\nice";

            var root = new EasyFile(rootPath, false);
            root.setFile("com.pwd", "pwd");
            root.setFile("com.usr", "usr");
            root.setDirectory("dir.logs", "logs");

            // Test log dir.
            var logDir = root.get("dir.logs");

            Assert.assertFalse(logDir.isFile());
            Assert.assertEquals(0, logDir.path().getFileName().toString()
                    .compareTo("logs"));

            logDir.setFile("log.warning", "warning");
            logDir.setFile("log.error", "error");

            // Test sub dir of log dir.
            logDir.setDirectory("dir.logs.info", "info");
            var infoDir = logDir.get("dir.logs.info");

            // Test file under dir.
            infoDir.setFile("log.a", "a");
            var file = infoDir.get("log.a");

            Assert.assertTrue(file.isFile());
            Assert.assertEquals(0, file.path().getFileName().toString()
                    .compareTo("a"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void bad() {
        final String rootPath = "C:\\Users\\chenh\\Desktop\\bad";
        EasyFile root = null;

        try {
            root = new EasyFile(rootPath, false);

            new EasyFile(rootPath + "\\duplicate_file", true);
            new EasyFile(rootPath + "\\duplicate_file", false);

            Assert.fail("Should throw exception.");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }

        try {
            Assert.assertNotNull("null pointer", root);
            root.setFile("solid.file", "solid");

            var file = root.get("solid.file");

            // Test creating files and directories under a file.
            file.setFile("error.file", "should_not_exist_file");
            file.setDirectory("error,dir", "should_not_exist_dir");

            Assert.fail("Should throw exception.");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    @Test
    public void recursive() {
        final String rootPath = "C:\\Users\\chenh\\Desktop\\recur_nice";

        try {
            var root = new EasyFile(rootPath, false);
            if (!root.isEmpty())
                throw new IllegalStateException("root dir is not empty");

            root.setDirectory("dir.d0", "d0");
            root.setFile("file.f0", "f0");
            root.setFile("file.f1", "f1");

            var x = root.get("file.f1");
            Assert.assertNotNull("object null", x);
            Assert.assertTrue("should be file", x.isFile());
            Assert.assertTrue("should be empty", x.isEmpty());
            Assert.assertFalse("shouldn't be empty", root.isEmpty());

            // Recursive dirs.
            var d0 = root.get("dir.d0");
            Assert.assertTrue("should be empty", d0.isEmpty());

            d0.setDirectory("dir.d1", "d1");
            d0.setDirectory("dir.d2", "d2");
            d0.setFile("file.f1", "f1");
            d0.setFile("file.f2", "f2");

            Assert.assertFalse("shouldn't be empty", d0.isEmpty());

            var d2 = d0.get("dir.d2");
            d2.setDirectory("dir.d2", "d2");
            d2.setDirectory("dir.d3", "d3");

            // Test recursive get dir from root.
            var recurD2 = root.recursiveGet("dir.d2");
            Assert.assertEquals("should have 2 directories 'd2'", 2, recurD2.size());
            for (var f : recurD2)
                Assert.assertFalse("should be directory", f.isFile());

            // Test recursive get dir from d0.
            recurD2 = d0.recursiveGet("dir.d2");
            Assert.assertEquals("should have 2 directories 'd2'", 2, recurD2.size());
            for (var f : recurD2)
                Assert.assertFalse("should be directory", f.isFile());

            var d3 = d2.get("dir.d3");
            d3.setFile("file.f0", "f0");
            d3.setFile("file.f4", "f4");

            // Test recursive get file from root.
            var recurF0 = root.recursiveGet("file.f0");
            Assert.assertEquals("should have 2 files 'f0'", 2, recurF0.size());
            for (var f : recurF0)
                Assert.assertTrue("should be file", f.isFile());

            // Test recursive get file from d2.
            var recurF4 = d2.recursiveGet("file.f4");
            Assert.assertEquals("only 1 object", 1, recurF4.size());
            for (var f : recurF4)
                Assert.assertTrue("should be file", f.isFile());

            // Test get file from root, shouldn't find the file.
            var f4 = root.get("file.f4");
            Assert.assertNull("should be null", f4);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        } catch (IllegalStateException e) {
            System.out.println(e.getMessage() + ", skip creation test");
        }

        // Test load an existing directory.
        try {
            var root = new EasyFile(rootPath, false);

            Assert.assertFalse("shouldn't be empty", rootPath.isEmpty());

            // Test recursive get from root.
            var d2 = root.recursiveGet("dir.d2");
            Assert.assertEquals("should have 2 directories 'd2'", 2, d2.size());
            for (var f : d2)
                Assert.assertFalse("should be directory", f.isFile());

            var f4 = root.recursiveGet("file.f4");
            Assert.assertEquals("only 1 object", 1, f4.size());
            for (var f : f4)
                Assert.assertTrue("should be file", f.isFile());
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }
}