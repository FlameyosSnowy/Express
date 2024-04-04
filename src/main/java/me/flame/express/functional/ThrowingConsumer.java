package me.flame.express.functional;

@FunctionalInterface
public interface ThrowingConsumer<T, E extends Throwable> {
    void accept(T value) throws E;
}
