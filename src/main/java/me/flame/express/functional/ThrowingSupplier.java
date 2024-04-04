package me.flame.express.functional;

@FunctionalInterface
public interface ThrowingSupplier<V, E extends Throwable> {
    V get() throws E, Exception;
}
