package com.mineshaft.registry;

import java.util.Objects;

/**
 * Namespaced identifier (like ResourceLocation in Minecraft)
 * Format: "namespace:path" (e.g., "mineshaft:stone", "minecraft:diamond")
 * 
 * Used for mod compatibility and avoiding naming conflicts
 */
public class Identifier implements Comparable<Identifier> {
    public static final String DEFAULT_NAMESPACE = "mineshaft";
    private static final char NAMESPACE_SEPARATOR = ':';
    
    private final String namespace;
    private final String path;
    
    /**
     * Create identifier from "namespace:path" string
     */
    public Identifier(String id) {
        int separatorIndex = id.indexOf(NAMESPACE_SEPARATOR);
        
        if (separatorIndex >= 0) {
            this.namespace = id.substring(0, separatorIndex);
            this.path = id.substring(separatorIndex + 1);
        } else {
            this.namespace = DEFAULT_NAMESPACE;
            this.path = id;
        }
        
        validate();
    }
    
    /**
     * Create identifier with explicit namespace and path
     */
    public Identifier(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
        validate();
    }
    
    /**
     * Validate identifier format
     */
    private void validate() {
        if (!isValidNamespace(namespace)) {
            throw new IllegalArgumentException("Invalid namespace: " + namespace);
        }
        if (!isValidPath(path)) {
            throw new IllegalArgumentException("Invalid path: " + path);
        }
    }
    
    /**
     * Check if namespace is valid (lowercase, alphanumeric, underscore, dash)
     */
    private static boolean isValidNamespace(String namespace) {
        if (namespace == null || namespace.isEmpty()) return false;
        
        for (int i = 0; i < namespace.length(); i++) {
            char c = namespace.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-')) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Check if path is valid
     */
    private static boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) return false;
        
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '/' || c == '.')) {
                return false;
            }
        }
        return true;
    }
    
    public String getNamespace() {
        return namespace;
    }
    
    public String getPath() {
        return path;
    }
    
    @Override
    public String toString() {
        return namespace + NAMESPACE_SEPARATOR + path;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Identifier)) return false;
        Identifier other = (Identifier) obj;
        return namespace.equals(other.namespace) && path.equals(other.path);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(namespace, path);
    }
    
    @Override
    public int compareTo(Identifier other) {
        int namespaceCompare = this.namespace.compareTo(other.namespace);
        if (namespaceCompare != 0) return namespaceCompare;
        return this.path.compareTo(other.path);
    }
    
    /**
     * Helper: Create mineshaft namespace identifier
     */
    public static Identifier of(String path) {
        return new Identifier(DEFAULT_NAMESPACE, path);
    }
    
    /**
     * Helper: Create identifier with custom namespace
     */
    public static Identifier of(String namespace, String path) {
        return new Identifier(namespace, path);
    }
}