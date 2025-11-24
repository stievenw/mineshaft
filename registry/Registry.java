package com.mineshaft.registry;

import java.util.*;

/**
 * Generic registry for game objects (like Minecraft's Registry)
 * Stores blocks, items, entities, etc. with namespaced identifiers
 */
public class Registry<T> implements Iterable<T> {
    private final Map<Identifier, T> entries = new LinkedHashMap<>();
    private final Map<T, Identifier> reverseMap = new IdentityHashMap<>();
    private final Identifier registryName;
    private boolean frozen = false;

    public Registry(Identifier registryName) {
        this.registryName = registryName;
    }

    /**
     * Register an entry
     */
    public T register(Identifier id, T entry) {
        if (frozen) {
            throw new IllegalStateException("Registry " + registryName + " is frozen!");
        }

        if (entries.containsKey(id)) {
            throw new IllegalStateException("Duplicate registration: " + id);
        }

        entries.put(id, entry);
        reverseMap.put(entry, id);

        System.out.println("[Registry/" + registryName.getPath() + "] Registered: " + id);

        return entry;
    }

    /**
     * Get entry by identifier
     */
    public T get(Identifier id) {
        return entries.get(id);
    }

    /**
     * Get identifier of entry
     */
    public Identifier getId(T entry) {
        return reverseMap.get(entry);
    }

    /**
     * Check if identifier is registered
     */
    public boolean containsId(Identifier id) {
        return entries.containsKey(id);
    }

    /**
     * Check if entry is registered
     */
    public boolean contains(T entry) {
        return reverseMap.containsKey(entry);
    }

    /**
     * Get all registered identifiers
     */
    public Set<Identifier> getIds() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    /**
     * Get all registered entries
     */
    public Collection<T> getEntries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /**
     * Freeze registry (no more registrations allowed)
     */
    public void freeze() {
        frozen = true;
        System.out.println("[Registry/" + registryName.getPath() + "] Frozen with " + entries.size() + " entries");
    }

    public boolean isFrozen() {
        return frozen;
    }

    public Identifier getRegistryName() {
        return registryName;
    }

    public int size() {
        return entries.size();
    }

    @Override
    public Iterator<T> iterator() {
        return entries.values().iterator();
    }

    @Override
    public String toString() {
        return "Registry[" + registryName + ", size=" + size() + "]";
    }
}