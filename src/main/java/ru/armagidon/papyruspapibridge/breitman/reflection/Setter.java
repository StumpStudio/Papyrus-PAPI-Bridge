package ru.armagidon.papyruspapibridge.breitman.reflection;

@FunctionalInterface
public interface Setter<T, V> {

  void set(T instance, V value);

}