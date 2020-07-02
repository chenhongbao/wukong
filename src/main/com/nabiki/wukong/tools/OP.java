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

package com.nabiki.wukong.tools;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

public class OP {
    /**
     * Get a deep copy of the specified object. The method first serializes the
     * object to a byte array and then recover a new object from it.
     *
     * <p>The object to be copied must implement {@link Serializable}, or the method
     * fails and returns {@code null}.
     * </p>
     *
     * @param copied the specified object to be deeply copied
     * @param <T> generic type of a copied object
     * @return deep copying object
     */
    @SuppressWarnings("unchecked")
    @OutTeam
    public static <T> T deepCopy(T copied) {
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            new ObjectOutputStream(bo).writeObject(copied);
            return (T) new ObjectInputStream(
                    new ByteArrayInputStream(bo.toByteArray())).readObject();
        } catch (IOException | ClassNotFoundException ignored) {
            return null;
        }
    }

    /**
     * Clock-wise duration between the two specified local time. The {@code end} is
     * always assumed to be after the {@code start} in clock time. If the {@code end}
     * is smaller than the {@code start} numerically, the time crosses midnight.
     *
     * @param start local time start
     * @param end local time end
     * @return duration between the specified two local times
     */
    @OutTeam
    public static Duration between(LocalTime start, LocalTime end) {
        Objects.requireNonNull(start, "local time start null");
        Objects.requireNonNull(end, "local time end null");
        if (start.isBefore(end))
            return Duration.between(start, end);
        else if (start.isAfter(end))
            return Duration.between(start, LocalTime.MIDNIGHT.minusNanos(1))
                    .plus(Duration.between(LocalTime.MIDNIGHT, end)).plusNanos(1);
        else
            return Duration.ZERO;
    }

    // Instrument product ID pattern.
    private static final Pattern productPattern = Pattern.compile("[a-zA-Z]+");
    private final static Gson gson;
    static {
        gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
                .serializeNulls()
                .setPrettyPrinting()
                .create();
    }

    /**
     * Parse the specified JSON string to object of the specified {@link Class}.
     *
     * @param json JSON string
     * @param clz {@link Class} of the object
     * @param <T> generic type of the object
     * @return object parsed from the specified JSON string
     * @throws IOException fail parsing JSON string
     */
    @OutTeam
    public static <T> T fromJson(String json, Class<T> clz) throws IOException {
        try {
            return gson.fromJson(json, clz);
        } catch (com.google.gson.JsonSyntaxException e) {
            throw new IOException("parse JSON string", e);
        }
    }

    /**
     * Encode the specified object into JSON string.
     *
     * @param obj object
     * @return JSON string representing the specified object
     */
    @OutTeam
    public static String toJson(Object obj) {
        return gson.toJson(obj);
    }

    /**
     * Read from the specified file and parse and content into a string using the
     * specified charset.
     *
     * @param file file to read from
     * @param charset charset for the returned string
     * @return string parsed from the content of the specified file
     * @throws IOException fail to read the file
     */
    @OutTeam
    public static String readText(File file, Charset charset) throws IOException {
        try (InputStream is = new FileInputStream(file)) {
            return new String(is.readAllBytes(), charset);
        }
    }

    /**
     * Write the specified string to the specified file, encoded into binary data
     * with the specified charset.
     *
     * @param text the string to be written
     * @param file file to be written to
     * @param charset charset of the string to decode
     * @param append if {@code true}, the content is appended to the end of the file
     *               rather than the beginning
     * @throws IOException if operation failed or file not found
     */
    @OutTeam
    public static void writeText(String text, File file, Charset charset,
                                 boolean append) throws IOException {
        Objects.requireNonNull(text);
        try (OutputStream os = new FileOutputStream(file, append)) {
            os.write(text.getBytes(charset));
            os.flush();
        }
    }

    private static final AtomicInteger incID = new AtomicInteger(0);

    /**
     * Get an auto increment ID.
     *
     * @return integer ID
     */
    @OutTeam
    public static int getIncrementID() {
        return incID.incrementAndGet();
    }

    /**
     * Check if the specified value is valid for price.
     *
     * @param price value to be checked
     * @return {@code true} if the specified value is valid for price, {@code false}
     * otherwise
     */
    public static boolean validPrice(double price) {
        return 0.0D < price && price < Double.MAX_VALUE;
    }

    /**
     * Format log.
     *
     * @param hint description
     * @param orderRef order reference if it has
     * @param errMsg error message if it has
     * @param errCode error code if it has
     * @return log string
     */
    @OutTeam
    public static String formatLog(String hint, String orderRef, String errMsg,
                             Integer errCode) {
        return String.format("%s[%s]%s(%d)", hint, orderRef, errMsg, errCode);
    }

    /**
     * Extract product ID from the specified instrument ID. The product ID is usually
     * the first few letters before the year-month number of  an instrument ID.
     *
     * @param instrID instrument ID
     * @return product ID
     */
    @OutTeam
    public static String getProductID(String instrID) {
        var m = productPattern.matcher(instrID);
        if (m.find())
            return instrID.substring(m.start(), m.end()).toLowerCase();
        else
            return null;
    }

    /**
     * Get today's string representation of the specified pattern. The pattern
     * follows the convention of {@link DateTimeFormatter}.
     *
     * @param pattern pattern
     * @return today's string representation
     */
    @OutTeam
    public static String getToday(String pattern) {
        if (pattern == null || pattern.trim().length() == 0)
            pattern = "yyyyMMdd";
        return LocalDate.now().format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Get now' time representation of the specified pattern. The pattern
     * follows the convention of {@link DateTimeFormatter}.
     *
     * @param pattern pattern
     * @return today's string representation
     */
    @OutTeam
    public static String getTime(String pattern) {
        if (pattern == null || pattern.trim().length() == 0)
            pattern = "HH:mm:ss";
        return LocalTime.now().format(DateTimeFormatter.ofPattern(pattern));
    }
}
