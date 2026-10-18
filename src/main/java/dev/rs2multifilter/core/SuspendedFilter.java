package dev.rs2multifilter.core;

/** Optional runtime capability of our filter; no change to RS/Predicate or card serialization. */
public interface SuspendedFilter {
    boolean suspended();
}
