package ru.armagidon.papyruspapibridge.breitman.reflection;

@FunctionalInterface
public interface Getter<T, V> {

  V get(T instance);

}