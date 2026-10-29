package org.example.generator;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class Generator {

    private final Random random = new Random(42);
    private static final int MAX_SIZE = 10;
    private static final int MAX_DEPTH = 32;

    @SuppressWarnings("unchecked")
    public <T> T generateValueOfType(Class<T> clazz) {
        if (clazz == null) {
            throw new GenerationException("Requested type must not be null");
        }
        return (T) generateImpl(clazz, new HashSet<>(), 0);
    }

    private Object generateImpl(Type type, Set<Class<?>> active, int depth) {
        if (depth > MAX_DEPTH) {
            throw new GenerationException("Maximum generation depth exceeded for " + type.getTypeName());
        }
        return switch (type) {
            case ParameterizedType parameterized -> generateParameterized(parameterized, active, depth);
            case Class<?> clazz -> generateClass(clazz, active, depth);
            default -> throw new GenerationException("Unsupported type: " + type.getTypeName());
        };
    }

    private Object generateClass(Class<?> clazz, Set<Class<?>> active, int depth) {
        return switch (clazz) {
            case Class<?> c when isPrimitiveOrWrapper(c) -> generatePrimitive(c);
            case Class<?> c when isVoid(c) -> throw new GenerationException("Cannot generate void/Void");
            case Class<?> c when isString(c) -> generateString();
            case Class<?> c when isEnum(c) -> generateEnum(c);
            case Class<?> c when isArray(c) -> generateArray(c, active, depth);
            case Class<?> c when isList(c) -> generateList(Integer.class, active, depth);
            case Class<?> c when isMap(c) -> generateMap(Integer.class, Integer.class, active, depth);
            default -> generateObject(clazz, active, depth);
        };
    }

    private boolean isPrimitiveOrWrapper(Class<?> clazz) {
        return clazz.isPrimitive() || clazz == Byte.class || clazz == Short.class
                || clazz == Integer.class || clazz == Long.class || clazz == Float.class
                || clazz == Double.class || clazz == Boolean.class || clazz == Character.class;
    }

    private boolean isVoid(Class<?> clazz) {
        return clazz == void.class || clazz == Void.class;
    }

    private boolean isString(Class<?> clazz) {
        return clazz == String.class;
    }

    private boolean isEnum(Class<?> clazz) {
        return clazz.isEnum();
    }

    private boolean isArray(Class<?> clazz) {
        return clazz.isArray();
    }

    private boolean isList(Class<?> clazz) {
        return clazz == List.class;
    }

    private boolean isMap(Class<?> clazz) {
        return clazz == Map.class;
    }

    private Object generateParameterized(ParameterizedType type, Set<Class<?>> active, int depth) {
        Type[] arguments = type.getActualTypeArguments();

        return switch (type.getRawType()) {
            case Class<?> c when isList(c) -> generateList(arguments[0], active, depth);
            case Class<?> c when isMap(c) -> generateMap(arguments[0], arguments[1], active, depth);
            default -> throw new GenerationException("Unsupported parameterized type: " + type.getTypeName());
        };
    }

    private Object generatePrimitive(Class<?> clazz) {
        return switch (clazz) {
            case Class<?> c when c == byte.class || c == Byte.class -> generateByte();
            case Class<?> c when c == short.class || c == Short.class -> generateShort();
            case Class<?> c when c == int.class || c == Integer.class -> random.nextInt();
            case Class<?> c when c == long.class || c == Long.class -> random.nextLong();
            case Class<?> c when c == double.class || c == Double.class -> random.nextDouble();
            case Class<?> c when c == float.class || c == Float.class -> random.nextFloat();
            case Class<?> c when c == boolean.class || c == Boolean.class -> random.nextBoolean();
            case Class<?> c when c == char.class || c == Character.class -> generateChar();
            default -> throw new GenerationException("Unsupported primitive type: " + clazz.getName());
        };
    }

    private byte generateByte() {
        byte[] b = new byte[1];
        random.nextBytes(b);
        return b[0];
    }

    private short generateShort() {
        return (short) (random.nextInt(Short.MAX_VALUE - Short.MIN_VALUE + 1) + Short.MIN_VALUE);
    }

    private char generateChar() {
        return (char) (random.nextInt(26) + 'a');
    }

    private String generateString() {
        StringBuilder result = new StringBuilder();
        for (int i = size(); i > 0; i--) {
            result.append((char) ('a' + random.nextInt(26)));
        }

        return result.toString();
    }

    private Object generateArray(Class<?> clazz, Set<Class<?>> active, int depth) {
        Class<?> component = clazz.getComponentType();
        Object result = Array.newInstance(component, size());
        for (int i = 0; i < Array.getLength(result); i++) {
            Array.set(result, i, generateImpl(component, active, depth + 1));
        }

        return result;
    }

    private Object generateEnum(Class<?> clazz) {
        Object[] constants = clazz.getEnumConstants();
        if (constants.length == 0) {
            throw new GenerationException("Empty enum: " + clazz.getName());
        }
        return constants[random.nextInt(constants.length)];
    }

    private List<Object> generateList(Type element, Set<Class<?>> active, int depth) {
        List<Object> result = new ArrayList<>();
        for (int i = size(); i > 0; i--) {
            result.add(generateImpl(element, active, depth + 1));
        }

        return result;
    }

    private Map<Object, Object> generateMap(Type key, Type value, Set<Class<?>> active, int depth) {
        Map<Object, Object> result = new HashMap<>();
        for (int i = size(); i > 0; i--) {
            result.put(generateImpl(key, active, depth + 1), generateImpl(value, active, depth + 1));
        }

        return result;
    }

    private int size() {
        return 1 + random.nextInt(MAX_SIZE);
    }

    private Object generateObject(Class<?> clazz, Set<Class<?>> active, int depth) {
        Generatable annotation = clazz.getDeclaredAnnotation(Generatable.class);
        if (annotation == null) {
            throw new GenerationException("Type must declare @Generatable: " + clazz.getName());
        }

        if (active.contains(clazz)) {
            return null;
        }
        active.add(clazz);
        try {
            if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
                Class<?>[] implementations = getClasses(clazz, annotation);
                return generateImpl(implementations[random.nextInt(implementations.length)], active, depth + 1);
            }
            if (annotation.implementations().length != 0) {
                throw new GenerationException("Concrete type cannot declare implementations: " + clazz.getName());
            }
            return generateFromCtor(clazz, active, depth);
        } finally {
            active.remove(clazz);
        }
    }

    private static Class<?>[] getClasses(Class<?> clazz, Generatable annotation) {
        Class<?>[] implementations = annotation.implementations();
        if (implementations.length == 0) {
            throw new GenerationException("No implementations declared for " + clazz.getName());
        }
        for (Class<?> implementation : implementations) {
            if (!clazz.isAssignableFrom(implementation) || implementation.isInterface()
                    || Modifier.isAbstract(implementation.getModifiers())
                    || implementation.getDeclaredAnnotation(Generatable.class) == null) {
                throw new GenerationException("Invalid @Generatable implementation "
                        + implementation.getName() + " for " + clazz.getName());
            }
        }
        return implementations;
    }

    private Object generateFromCtor(Class<?> clazz, Set<Class<?>> active, int depth) {
        Constructor<?>[] constructors = clazz.getConstructors();

        Arrays.sort(constructors, Comparator.<Constructor<?>>comparingInt(Constructor::getParameterCount)
                .reversed().thenComparing(Constructor::toGenericString));
        GenerationException failure = new GenerationException("No usable public constructor for " + clazz.getName());
        for (Constructor<?> constructor : constructors) {
            try {
                Type[] types = constructor.getGenericParameterTypes();
                Object[] arguments = new Object[types.length];
                for (int i = 0; i < types.length; i++) {
                    arguments[i] = generateImpl(types[i], active, depth + 1);
                }
                return constructor.newInstance(arguments);
            } catch (InvocationTargetException e) {
                failure.addSuppressed(new GenerationException("Constructor rejected generated arguments: "
                        + constructor, e.getCause()));
            } catch (ReflectiveOperationException | GenerationException | IllegalArgumentException e) {
                failure.addSuppressed(new GenerationException("Cannot invoke " + constructor, e));
            }
        }
        throw failure;
    }
}
