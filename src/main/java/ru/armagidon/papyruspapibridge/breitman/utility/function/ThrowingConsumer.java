package ru.armagidon.papyruspapibridge.breitman.utility.function;

@FunctionalInterface
public interface ThrowingConsumer<T> {

  void accept(T t) throws Throwable;

}