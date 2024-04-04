package me.flame.express.functional;

@FunctionalInterface
public interface ThrowingFunction<S, V, E extends Throwable> {
    V apply(S value) throws E;
}
