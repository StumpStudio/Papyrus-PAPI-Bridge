package ru.armagidon.papyruspapibridge.breitman.reflection;

import ru.armagidon.papyruspapibridge.breitman.utility.function.ThrowingRunnable;
import ru.armagidon.papyruspapibridge.breitman.utility.function.ThrowingSupplier;
import sun.misc.Unsafe;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.function.Supplier;

public final class Reflection {

  private static final ClassContextSecurityManager CLASS_CONTEXT_SECURITY_MANAGER = new ClassContextSecurityManager();

  public static final Unsafe UNSAFE = unchecked(Reflection::getUnsafe);
  private static final long OVERRIDE_OFFSET = unchecked(Reflection::getOverrideOffset);

  private static final Method GET_DECLARED_FIELDS_METHOD = unchecked(Reflection::getDeclaredFields0);
  private static final Method COPY_FIELD = unchecked(Reflection::getCopyField);

  private static final Method GET_DECLARED_METHODS0_METHOD = unchecked(Reflection::getDeclaredMethods0);
  private static final Method COPY_METHOD = unchecked(Reflection::getCopyMethod);

  private final static MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  private Class<?> type;
  private Object object;

  private Reflection(Class<?> type, Object object) {
    this.type = type;
    this.object = object;
  }

  public Reflection accessible() {
    if (object instanceof AccessibleObject accessible) {
      setAccessible(accessible);
    }
    return this;
  }

  public Reflection field(String name) {
    final Field field = getField(type, name);
    this.type = field.getClass();
    this.object = field;
    return this;
  }

  public Reflection field(Class<?> type) {
    final Field field = getField(this.type, type);
    this.type = field.getClass();
    this.object = field;
    return this;
  }

  public <T> T get() {
    //noinspection unchecked
    return (T) object;
  }

  public <T, V> Setter<T, V> setter() {
    return setter((Field) object);
  }

  public <T, V> Setter<T, V> setter(String name) {
    return setter(getField(type, name));
  }

  public <T, V> Setter<T, V> setter(Class<?> type) {
    return setter(getField(this.type, type));
  }

  public <T, V> Getter<T, V> getter() {
    return getter((Field) object);
  }

  public <T, V> Getter<T, V> getter(String name) {
    return getter(getField(type, name));
  }

  public <T, V> Getter<T, V> getter(Class<?> type) {
    return getter(getField(this.type, type));
  }

  public static Reflection of(Class<?> type) {
    return new Reflection(type, type);
  }

  public static Reflection of(Object object) {
    return new Reflection(object.getClass(), object);
  }

  public static Reflection of(Class<?> type, Object object) {
    return new Reflection(type, object);
  }

  public static void set(Field field, Object value) {
    set(field, null, value);
  }

  public static void set(Field field, Object instance, Object value) {
    try {
      field.set(instance, value);
    } catch (Throwable e) {
      if (_set(field, instance, value)) return;
      throw unchecked(e);
    }
  }

  private static boolean _set(Field field, Object instance, Object value) {
    if (Modifier.isFinal(field.getModifiers())) {
      try {
        final Object unsafe = getMethod("jdk.internal.misc.Unsafe", "getUnsafe").invoke(null);
        final Method putReferenceMethod = getMethod(unsafe, "putReference", Object.class, long.class, Object.class);

        putReferenceMethod.invoke(unsafe,
            instance == null ? getMethod(unsafe, "staticFieldBase", Field.class).invoke(unsafe, field) : instance,
            Modifier.isStatic(field.getModifiers()) ?
                getMethod(unsafe, "staticFieldOffset", Field.class).invoke(unsafe, field)
                :
                getMethod(unsafe, "objectFieldOffset", Field.class).invoke(unsafe, field),
            value
        );
        return true;
      } catch (Throwable e) {
        throw unchecked(e);
      }
    }
    return false;
  }

  public static <T> T get(Field field) {
    return get(field, null);
  }

  public static <T> T get(Field field, Object instance) {
    try {
      //noinspection unchecked
      return (T) setAccessible(field).get(instance);
    } catch (Throwable e) {
      throw unchecked(e);
    }
  }

  public static <T extends AccessibleObject> T setAccessible(T object) {
    try {
      object.setAccessible(true);
    } catch (Throwable e) {
      UNSAFE.putBooleanVolatile(object, OVERRIDE_OFFSET, true);
    }
    return object;
  }

  public static Field getField(Object type, String name) {
    return getField(type.getClass(), name);
  }

  public static Field getField(String type, String name) {
    return getField(getType(type), name);
  }

  public static Field getField(Class<?> type, String name) {
    final Field field = _getField(type, name);
    return setAccessible(field);
  }

  private static Field _getField(Class<?> type, String name) {
    Field field;

    field = firstField(type, name);
    if (field != null) {
      return field;
    }

    try {
      field = getDeclaredField(type, name);
      return field;
    } catch (Throwable e) {
      final Class<?> superclass = type.getSuperclass();
      if (superclass != null) {
        field = _getField(superclass, name);
        if (field != null) return field;
      }
      for (Class<?> interfaze : type.getInterfaces()) {
        field = _getField(interfaze, name);
        if (field != null) return field;
      }
    }

    return null;
  }

  public static Field getField(Class<?> type, Class<?> fieldType) {
    final Field field = _getField(type, fieldType);
    return setAccessible(field);
  }

  private static Field _getField(Class<?> type, Class<?> fieldType) {
    Field field;

    try {
      field = getDeclaredField(type, fieldType);
      return field;
    } catch (Throwable e) {
      final Class<?> superclass = type.getSuperclass();
      if (superclass != null) {
        field = _getField(superclass, fieldType);
        if (field != null) return field;
      }
      for (Class<?> interfaze : type.getInterfaces()) {
        field = _getField(interfaze, fieldType);
        if (field != null) return field;
      }
    }

    return null;
  }

  public static Method getMethod(Object type, String name, Class<?>... parameters) {
    return getMethod(type.getClass(), name, parameters);
  }

  public static Method getMethod(String type, String name, Class<?>... parameters) {
    return getMethod(getType(type), name, parameters);
  }

  public static Method getMethod(Class<?> type, String name, Class<?>... parameters) {
    final Method method = _getMethod(type, name, parameters);
    return setAccessible(method);
  }

  public static Method _getMethod(Class<?> type, String name, Class<?>... parameters) {
    Method method;

    method = firstMethod(type, name, parameters);
    if (method != null) {
      return method;
    }

    try {
      method = getDeclaredMethod(type, name, parameters);
      return method;
    } catch (Throwable e) {
      final Class<?> superclass = type.getSuperclass();
      if (superclass != null) {
        method = _getMethod(superclass, name, parameters);
        if (method != null) return method;
      }
      for (Class<?> interfaze : type.getInterfaces()) {
        method = _getMethod(interfaze, name, parameters);
        if (method != null) return method;
      }
    }

    return null;
  }

  private static Field getDeclaredField(Class<?> type, String name) throws NoSuchFieldException {
    try {
      final Field[] fields = (Field[]) GET_DECLARED_FIELDS_METHOD.invoke(type, false);
      Field result = null;
      for (Field field : fields) {
        if (field.getName().equals(name)) {
          result = field;
          break;
        }
      }
      if (result == null) {
        throw new NoSuchFieldException();
      }
      return (Field) COPY_FIELD.invoke(result);
    } catch (Throwable e) {
      throw unchecked(e);
    }
  }

  private static Field getDeclaredField(Class<?> type, Class<?> fieldType) throws NoSuchFieldException {
    try {
      final Field[] fields = (Field[]) GET_DECLARED_FIELDS_METHOD.invoke(type, false);
      Field result = null;
      for (Field field : fields) {
        if (field.getType().equals(fieldType)) {
          result = field;
          break;
        }
      }
      if (result == null) {
        throw new NoSuchFieldException();
      }
      return (Field) COPY_FIELD.invoke(result);
    } catch (Throwable e) {
      throw unchecked(e);
    }
  }

  private static Method getDeclaredMethod(Class<?> type, String name, Class<?>... parameters) throws NoSuchMethodException {
    try {
      final Method[] methods = (Method[]) GET_DECLARED_METHODS0_METHOD.invoke(type, false);
      Method result = null;
      for (Method method : methods) {
        if (method.getName().equals(name)
            && sameParameters(parameters, method.getParameterTypes())
            &&
            (result == null || result.getReturnType().isAssignableFrom(method.getReturnType()))) {
          result = method;
        }
      }
      if (result == null) {
        throw new NoSuchMethodException();
      }
      return (Method) COPY_METHOD.invoke(result);
    } catch (Throwable e) {
      throw unchecked(e);
    }
  }

  private static Field firstField(Class<?> type, String name) {
    if (name.indexOf('|') >= 0) {
      for (var tokenizer = new StringTokenizer(name, "|"); tokenizer.hasMoreTokens(); ) {
        final String token = tokenizer.nextToken();
        final Field field = getField(type, token);
        if (field != null) {
          return field;
        }
      }
    }
    return null;
  }

  private static Method firstMethod(Class<?> type, String name, Class<?>[] parameters) {
    if (name.indexOf('|') >= 0) {
      for (var tokenizer = new StringTokenizer(name, "|"); tokenizer.hasMoreTokens(); ) {
        final String token = tokenizer.nextToken();
        final Method method = getMethod(type, token, parameters);
        if (method != null) {
          return method;
        }
      }
    }
    return null;
  }

  public static Class<?> getType(String fqn) {
    return getType(fqn, false);
  }

  public static Class<?> getType(String fqn, ClassLoader classLoader) {
    return getType(fqn, classLoader, false);
  }

  public static Class<?> getType(String fqn, boolean stacktrace) {
    return getType(fqn, Reflection.class.getClassLoader(), stacktrace);
  }

  public static Class<?> getType(String fqn, ClassLoader classLoader, boolean stacktrace) {
    int dims = 0, iBracket = fqn.indexOf('[');

    String componentFqn = fqn;

    if (iBracket > 0) {
      dims = (fqn.length() - iBracket) / 2;
      componentFqn = fqn.substring(0, iBracket);
    }

    Class<?> type;
    try {
      type = classForPrimitiveName(componentFqn);
      if (type == null) {
        type = Class.forName(componentFqn, false, classLoader);
      }
    } catch (ClassNotFoundException e) {
      final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
      if (classLoader != contextClassLoader && contextClassLoader != null) {
        return getType(fqn, contextClassLoader, stacktrace);
      }

      type = stacktrace ? findInStacktrace(fqn) : null;
    }

    if (type != null && dims > 0) {
      type = Array.newInstance(type, new int[dims]).getClass();
    }

    return type;
  }

  public static <T> Class<T> define(ClassLoader classLoader, String fqn, byte[] bytes) {
    try {
      final Method method = setAccessible(getClassLoaderDefineMethod());
      //noinspection unchecked
      return (Class<T>) method.invoke(classLoader, fqn, bytes, 0, bytes.length);
    } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
      throw unchecked(e);
    }
  }

  public static Class<?> findLoaded(ClassLoader classLoader, String type) {
    try {
      final Method method = setAccessible(getFindLoadedClassMethod());
      return (Class<?>) method.invoke(classLoader, type);
    } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
      throw unchecked(e);
    }
  }

  public static boolean defined(String type) {
    return defined(type, false, Reflection.class.getClassLoader());
  }

  public static boolean defined(String type, boolean initialize) {
    return defined(type, initialize, Reflection.class.getClassLoader());
  }

  public static boolean defined(String type, boolean initialize, ClassLoader classLoader) {
    try {
      Class.forName(type, initialize, classLoader);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private static Class<?> findInStacktrace(String fqn) {
    final Class<?>[] stackTraceClasses = CLASS_CONTEXT_SECURITY_MANAGER.getClassContext();
    if (stackTraceClasses == null) {
      return null;
    }

    final HashSet<ClassLoader> attempted = new HashSet<>();
    attempted.add(Reflection.class.getClassLoader());
    attempted.add(Thread.currentThread().getContextClassLoader());

    for (Class<?> type : stackTraceClasses) {
      final ClassLoader classLoader = type.getClassLoader();
      if (attempted.contains(classLoader)) {
        continue;
      }
      attempted.add(classLoader);
      return getType(fqn, classLoader, false);
    }
    return null;
  }

  private static Class<?> classForPrimitiveName(String type) {
    return switch (type) {
      case "void" -> void.class;
      case "boolean" -> boolean.class;
      case "char" -> char.class;
      case "byte" -> byte.class;
      case "short" -> short.class;
      case "int" -> int.class;
      case "long" -> long.class;
      case "float" -> float.class;
      case "double" -> double.class;
      default -> null;
    };
  }

  private static boolean sameParameters(Class<?>[] params1, Class<?>[] params2) {
    if (params1 == null) {
      return params2 == null || params2.length == 0;
    }
    if (params2 == null) {
      return params1.length == 0;
    }
    if (params1.length != params2.length) {
      return false;
    }
    for (int i = 0; i < params1.length; i++) {
      if (params1[i] != params2[i]) {
        return false;
      }
    }
    return true;
  }

  public static <T, V> Setter<T, V> setter(Field field) {
    try {
      final MethodHandle handle = LOOKUP.unreflectSetter(field);
      final MethodType type = handle.type();
      //noinspection unchecked
      return (Setter<T, V>) LambdaMetafactory.metafactory(
          LOOKUP,
          "set",
          MethodType.methodType(Setter.class, MethodHandle.class),
          type.generic().changeReturnType(void.class),
          MethodHandles.exactInvoker(type),
          type
      ).getTarget().invokeExact(handle);
    } catch (Throwable e) {
      throw unchecked(e);
    }
  }

  public static <T, V> Getter<T, V> getter(Field field) {
    try {
      final MethodHandle handle = LOOKUP.unreflectGetter(field);
      final MethodType type = handle.type();
      //noinspection unchecked
      return (Getter<T, V>) LambdaMetafactory.metafactory(
          LOOKUP,
          "get",
          MethodType.methodType(Getter.class, MethodHandle.class),
          type.generic(),
          MethodHandles.exactInvoker(type),
          type
      ).getTarget().invokeExact(handle);
    } catch (Throwable e) {
      throw unchecked(e);
    }
  }

  public static <T> Supplier<T> constructor(Class<?> type) {
    try {
      final MethodHandle handle = LOOKUP.findConstructor(type, MethodType.methodType(void.class));
      //noinspection unchecked
      return (Supplier<T>) LambdaMetafactory.metafactory(
          LOOKUP,
          "get",
          MethodType.methodType(Supplier.class),
          handle.type().generic(),
          handle,
          handle.type()
      ).getTarget().invokeExact();
    } catch (Throwable e) {
      throw unchecked(e);
    }
  }

  @SuppressWarnings("removal")
  private static final class ClassContextSecurityManager extends SecurityManager {
    /**
     * Expose getClassContext() to enable finding classloaders in the stack trace
     */
    @Override
    protected Class<?>[] getClassContext() {
      return super.getClassContext();
    }
  }

  static void unchecked(ThrowingRunnable runnable) {
    try {
      runnable.run();
    } catch (Throwable e) {
      throw unchecked(e);
    }
  }

  static <T> T unchecked(ThrowingSupplier<T> supplier) {
    try {
      return supplier.get();
    } catch (Throwable e) {
      throw unchecked(e);
    }
  }

  private static RuntimeException unchecked(Throwable t) {
    _unchecked(t);
    throw new IllegalStateException();
  }

  private static <T extends Throwable> void _unchecked(Throwable t) throws T {
    //noinspection unchecked
    throw (T) t;
  }

  private static Unsafe getUnsafe() throws NoSuchFieldException, IllegalAccessException {
    final Field field = Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return (Unsafe) field.get(null);
  }

  private static long getOverrideOffset() throws NoSuchFieldException {
    //noinspection deprecation
    return UNSAFE.objectFieldOffset(Shadow.class.getDeclaredField("override"));
  }

  private static Method getDeclaredFields0() throws NoSuchMethodException {
    final Method method = Class.class.getDeclaredMethod("getDeclaredFields0", boolean.class);
    return setAccessible(method);
  }

  private static Method getCopyField() throws NoSuchMethodException {
    final Method method = Field.class.getDeclaredMethod("copy");
    return setAccessible(method);
  }

  private static Method getDeclaredMethods0() throws NoSuchMethodException {
    final Method method = Class.class.getDeclaredMethod("getDeclaredMethods0", boolean.class);
    return setAccessible(method);
  }

  private static Method getCopyMethod() throws NoSuchMethodException {
    final Method method = Method.class.getDeclaredMethod("copy");
    return setAccessible(method);
  }

  private static Method getClassLoaderDefineMethod() throws NoSuchMethodException {
    return ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
  }

  private static Method getFindLoadedClassMethod() throws NoSuchMethodException {
    return ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
  }

  private static final class Shadow {
    private static final String ACCESS_PERMISSION = "";
    private boolean override;
  }

}