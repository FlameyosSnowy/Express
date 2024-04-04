/*
 * Copyright (c) 2021 dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.flame.express;

import lombok.Getter;
import me.flame.express.errors.AttemptFailedException;
import me.flame.express.errors.ResultException;
import me.flame.express.functional.ThrowingConsumer;
import me.flame.express.functional.ThrowingFunction;
import me.flame.express.functional.ThrowingSupplier;
import me.flame.express.sets.Blank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static me.flame.express.sets.Blank.BLANK;
import static me.flame.express.sets.Blank.voidness;
import static me.flame.express.Result.State.OK;
import static me.flame.express.Result.State.ERROR;

/**
 * {@link me.flame.express.Result} represents value or associated error that caused the absence of the expected value.
 * By definition, Result has to contain one non-null value - the value or the error.
 * If you want to use nullable value or nullable error, you have to use wrapper like {@link me.flame.express.Option} to explicitly declare it.
 *
 * @param <V> type of value
 * @param <E> type of error
 */
@SuppressWarnings({"unused", "unchecked"})
public class Result<V, E extends Exception>  {
    public enum State {
        OK,
        ERROR
    }

    @Getter
    private final State state;
    private final V value;
    private final E error;

    private Result(State state, @Nullable V value, @Nullable E error) {
        if (value != null && error != null) {
            throw new IllegalStateException("Value and error are not null - Cannot determine state of Result");
        }

        this.state = state;
        this.value = value;
        this.error = error;
    }

    public static <V, E extends Exception> @NotNull Result<V, E> ok(V value) {
        return new Result<>(OK, value, null);
    }

    public static <E extends Exception> @NotNull Result<Blank, E> ok() {
        return new Result<>(OK, BLANK, null);
    }

    public static <V, E extends Exception> @NotNull Result<V, E> error(E err) {
        return new Result<>(ERROR, null, err);
    }

    public static <V> @NotNull Result<V, ResultException> error() {
        return new Result<>(ERROR, null, new ResultException("Result error."));
    }

    public static <S, A extends Exception> @NotNull Result<S, A> when(boolean condition, @NotNull Supplier<S> value, @NotNull Supplier<A> err) {
        return condition ? ok(value.get()) : error(err.get());
    }

    public static <S, A extends Exception> @NotNull Result<S, A> when(boolean condition, S value, A err) {
        return condition ? ok(value) : error(err);
    }

    public static <E extends Exception> @NotNull Result<Void, @NotNull Exception> runThrowing(@NotNull Callable<@NotNull Exception> runnable) throws AttemptFailedException {
        return runThrowing(Exception.class, runnable);
    }

    public static <E extends Exception> @NotNull Result<Void, E> runThrowing(
            @NotNull Class<? extends E> exceptionType,
            @NotNull Callable<@NotNull E> runnable
    ) throws AttemptFailedException {
        return supplyThrowing(exceptionType, () -> {
            runnable.call();
            return voidness();
        });
    }
    public static <S> @NotNull Result<S, Exception> supplyThrowing(@NotNull ThrowingSupplier<S, @NotNull Exception> supplier) throws AttemptFailedException {
        return supplyThrowing(Exception.class, supplier);
    }

    public static <V, E extends Exception> @NotNull Result<V, E> supplyThrowing(
            @NotNull Class<? extends E> exceptionType,
            @NotNull ThrowingSupplier<V, @NotNull E> supplier
    ) throws AttemptFailedException {
        try {
            return Result.ok(supplier.get());
        } catch (Throwable throwable) {
            if (exceptionType.isAssignableFrom(throwable.getClass())) {
                //noinspection unchecked
                return Result.error((E) throwable);
            }

            throw new AttemptFailedException(throwable);
        }
    }

    public <S, R> @NotNull Result<R, E> merge(
            @NotNull Result<S, ? extends E> second,
            @NotNull BiFunction<V, S, R> mergeFunction
    ) {
        return flatMap(firstValue -> second.map(secondValue -> mergeFunction.apply(firstValue, secondValue)));
    }

    public <S> @NotNull Result<S, E> map(@NotNull Function<V, S> function) {
        return isOk() ? ok(function.apply(get())) : projectToError();
    }

    public @NotNull Result<Blank, E> mapToBlank() {
        return isOk() ? ok() : projectToError();
    }

    public <A extends Exception> @NotNull Result<V, A> mapErr(@NotNull Function<E, A> function) {
        return isOk() ? projectToValue() : error(function.apply(getError()));
    }

    public <S> @NotNull Result<S, E> flatMap(@NotNull Function<V, @NotNull Result<S, ? extends E>> function) {
        //noinspection unchecked
        return isOk() ? (Result<S, E>) function.apply(get()) : projectToError();
    }

    public <S extends Exception> @NotNull Result<V, S> flatMapErr(@NotNull Function<@NotNull E, @NotNull Result<V, S>> function) {
        return isErr() ? function.apply(getError()) : projectToValue();
    }

    public @NotNull Result<V, E> filter(@NotNull Predicate<V> predicate, @NotNull Function<V, E> errorSupplier) {
        return isOk() && !predicate.test(get()) ? error(errorSupplier.apply(get())) : this;
    }

    public @NotNull Result<V, E> filterNot(@NotNull Predicate<V> predicate, @NotNull Function<V, E> errorSupplier) {
        return filter(value -> !predicate.test(value), errorSupplier);
    }

    public @NotNull Result<V, E> filter(@NotNull Function<V, @Nullable E> filtersBody) {
        return flatMap(value -> {
            E error = filtersBody.apply(value);
            return error == null ? ok(value) : error(error);
        });
    }

    /**
     *
     * @param <V>
     * @param <S> Filtered error
     * @param <E> Expected error
     */
    public static class FilteredResult<V, S extends Exception, E extends Exception> {

        private final @Nullable Result<V, E> previousError;
        private final @Nullable Result<V, S> currentResult;

        private FilteredResult(@Nullable Result<V, E> previousError, @Nullable Result<V, S> currentResult) {
            if (previousError == null && currentResult == null) {
                throw new IllegalArgumentException("Both previousError and currentResult are null");
            }
            this.previousError = previousError;
            this.currentResult = currentResult;
        }

        public @NotNull Result<V, E> mapFilterError(@NotNull Function<S, E> mapper) {
            return previousError != null
                    ? previousError
                    : Objects.requireNonNull(currentResult).mapErr(mapper);
        }

    }

    public @NotNull <ER extends Exception> FilteredResult<V, E, ER> filterWithThrowing(@NotNull ThrowingConsumer<V, @NotNull E> filtersBody) {
        return (FilteredResult<V, E, ER>) fold(value -> {
                try {
                    filtersBody.accept(value);
                    return new FilteredResult<>(null, ok(value));
                } catch (Exception e) {
                    //noinspection unchecked
                    return new FilteredResult<>(null, error((E) e));
                }
            },
            error -> new FilteredResult<>(error(error), null));
    }

    public <C> C fold(@NotNull Function<V, C> valueMerge, @NotNull Function<E, C> errorMerge) {
        return isOk() ? valueMerge.apply(get()) : errorMerge.apply(getError());
    }

    public boolean matches(Predicate<V> condition) {
        return isOk() && condition.test(value);
    }

    public <S> @NotNull Result<S, E> is(@NotNull Class<S> type, @NotNull Function<V, E> errorSupplier) {
        return this.filter(type::isInstance, errorSupplier).map(type::cast);
    }

    public Result<V, E> consume(@NotNull Consumer<V> valueConsumer, @NotNull Consumer<E> errorConsumer) {
        return this.peek(valueConsumer).onError(errorConsumer);
    }

    @SuppressWarnings("unchecked")
    public <S, ER extends Exception> @NotNull Result<S, ER> project() {
        return (Result<S, ER>) this;
    }

    @SuppressWarnings("unchecked")
    public <S extends Exception> @NotNull Result<V, S> projectToValue() {
        return (Result<V, S>) this;
    }

    @SuppressWarnings("unchecked")
    public <S> @NotNull Result<S, E> projectToError() {
        return (Result<S, E>) this;
    }

    public @NotNull Result<V, E> orElse(@NotNull Function<E, @NotNull Result<V, E>> orElse) {
        return isOk() ? this : orElse.apply(getError());
    }

    public @NotNull V orElseGet(@NotNull Function<E, V> orElse) {
        return isOk() ? get() : orElse.apply(getError());
    }

    public <S extends Exception> @NotNull V orThrow(@NotNull ThrowingFunction<S, S, S> consumer) throws S {
        if (isOk()) return get();
        throw consumer.apply((S) getError());
    }

    public @NotNull Result<V, E> peek(@NotNull Consumer<V> consumer) {
        if (isOk()) consumer.accept(get());
        return this;
    }

    public @NotNull Result<V, E> onError(@NotNull Consumer<E> consumer) {
        if (isErr()) consumer.accept(getError());
        return this;
    }

    public boolean isOk() {
        return state == OK;
    }

    public boolean isErr() {
        return state == ERROR;
    }

    public V get() {
        if (isErr()) throw new IllegalStateException("Result contains error - Cannot get the success value");
        return value;
    }

    public E getError() {
        if (isOk()) throw new IllegalStateException("Result completed successfully - Cannot get the error value");
        return error;
    }

    public Object getAny() {
        return isOk() ? value : error;
    }

    @SuppressWarnings("unchecked")
    public <AS> AS getAnyAs() {
        return (AS) getAny();
    }

    public @NotNull Option<V> toOption() {
        return Option.some(value);
    }

    public @NotNull Option<E> errorToOption() {
        return Option.some(error);
    }

    public @Nullable V orNull() {
        return value;
    }

    @Override
    public boolean equals(Object to) {
        if (this == to) {
            return true;
        }

        if (to == null || getClass() != to.getClass()) {
            return false;
        }

        Result<?, ?> other = (Result<?, ?>) to;
        return Objects.equals(value, other.value) && Objects.equals(error, other.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, error);
    }

    @Override
    public String toString() {
        return "Result{" + (isOk() ? "VALUE=" + value : "ERR=" + error) + "}";
    }

}