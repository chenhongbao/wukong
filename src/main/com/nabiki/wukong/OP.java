package com.nabiki.wukong;

import java.io.*;

public class OP {
    /**
     * Get a deep copy of the specified object. The method does the deep copy
     * via serialize the object to a byte array that then deserializes from.
     *
     * @param copied the specified object to be deeply copied
     * @param <T> generic type of a copied object
     * @return deep copying object
     */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T copied) {
        try (ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            new ObjectOutputStream(bo).writeObject(copied);
            return (T) new ObjectInputStream(
                    new ByteArrayInputStream(bo.toByteArray())).readObject();
        } catch (IOException | ClassNotFoundException ignored) {
            return null;
        }
    }
}
