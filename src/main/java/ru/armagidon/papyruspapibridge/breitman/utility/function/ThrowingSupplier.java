package ru.armagidon.papyruspapibridge.breitman.utility.function;

@FunctionalInterface
public interface ThrowingSupplier<T> {

  T get() throws Throwable;

}