package ru.armagidon.papyruspapibridge.breitman.utility.function;

@FunctionalInterface
public interface ThrowingRunnable {

  void run() throws Throwable;

}