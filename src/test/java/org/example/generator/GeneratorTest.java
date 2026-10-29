package org.example.generator;

import org.example.classes.BinaryTreeNode;
import org.example.classes.Cart;
import org.example.classes.Example;
import org.example.classes.Product;
import org.example.classes.Rectangle;
import org.example.classes.Shape;
import org.example.classes.Triangle;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorTest {
    private final Generator generator = new Generator();

    enum Color { RED, GREEN, BLUE }
    enum Empty { }

    @Test
    void generatesAllPrimitivesAndWrappers() {
        Class<?>[] primitives = {byte.class, short.class, int.class, long.class,
                float.class, double.class, boolean.class, char.class};
        Class<?>[] wrappers = {Byte.class, Short.class, Integer.class, Long.class,
                Float.class, Double.class, Boolean.class, Character.class};
        for (int i = 0; i < primitives.length; i++) {
            assertInstanceOf(wrappers[i], generator.generateValueOfType(primitives[i]));
            assertInstanceOf(wrappers[i], generator.generateValueOfType(wrappers[i]));
        }
    }

    @Test
    void generatesStringsEnumsAndVaryingValues() {
        Set<Integer> numbers = new HashSet<>();
        Set<String> strings = new HashSet<>();
        Set<Color> colors = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            numbers.add(generator.generateValueOfType(Integer.class));
            String text = generator.generateValueOfType(String.class);
            assertTrue(text.matches("[a-z]{1,10}"));
            strings.add(text);
            colors.add(generator.generateValueOfType(Color.class));
        }
        assertTrue(numbers.size() > 1);
        assertTrue(strings.size() > 1);
        assertTrue(colors.size() > 1);
    }

    @Test
    void generatesArraysWithCorrectRuntimeTypeAndElements() {
        for (Class<?> type : new Class<?>[]{int[].class, String[].class, Color[].class,
                Rectangle[].class, int[][].class}) {
            Object array = generator.generateValueOfType(type);
            assertEquals(type, array.getClass());
            assertTrue(Array.getLength(array) >= 1 && Array.getLength(array) <= 10);
            for (int i = 0; i < Array.getLength(array); i++) {
                Object element = Array.get(array, i);
                assertNotNull(element);
                if (!type.getComponentType().isPrimitive()) {
                    assertTrue(type.getComponentType().isInstance(element));
                }
            }
        }
    }

    @Test
    void generatesNonemptyRawCollections() {
        List<?> list = generator.generateValueOfType(List.class);
        assertFalse(list.isEmpty());
        list.forEach(value -> assertInstanceOf(Integer.class, value));
        Map<?, ?> map = generator.generateValueOfType(Map.class);
        assertFalse(map.isEmpty());
        map.forEach((key, value) -> {
            assertInstanceOf(Integer.class, key);
            assertInstanceOf(Integer.class, value);
        });
    }

    @Test
    void generatesTemplateClassesAndTypedCart() {
        assertNotNull(generator.generateValueOfType(Example.class));
        Product product = generator.generateValueOfType(Product.class);
        assertFalse(product.getName().isEmpty());
        assertTrue(product.getPrice() >= 0 && product.getPrice() < 1);
        assertNotNull(generator.generateValueOfType(Rectangle.class));
        assertNotNull(generator.generateValueOfType(Triangle.class));
        Cart cart = generator.generateValueOfType(Cart.class);
        assertFalse(cart.getItems().isEmpty());
        for (Product item : cart.getItems()) {
            assertNotNull(item.getName());
        }
        BinaryTreeNode node = generator.generateValueOfType(BinaryTreeNode.class);
        assertNotNull(node.getData());
        assertNull(node.getLeft());
        assertNull(node.getRight());
    }

    @Test
    void resolvesShapeImplementations() {
        Set<Class<?>> types = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            Shape shape = generator.generateValueOfType(Shape.class);
            assertTrue(shape instanceof Rectangle || shape instanceof Triangle);
            types.add(shape.getClass());
        }
        assertEquals(Set.of(Rectangle.class, Triangle.class), types);
    }

    @Generatable
    public static class Address {
        final String street;
        final int number;
        public Address(String street, int number) {
            this.street = street;
            this.number = number;
        }
    }

    @Generatable
    public static class Directory {
        final Map<String, List<Address>> entries;
        public Directory(Map<String, List<Address>> entries) { this.entries = entries; }
    }

    @Test
    void generatesNewClassesAndNestedGenericCollections() {
        Address address = generator.generateValueOfType(Address.class);
        assertFalse(address.street.isEmpty());
        Directory directory = generator.generateValueOfType(Directory.class);
        assertFalse(directory.entries.isEmpty());
        directory.entries.forEach((key, value) -> {
            assertFalse(key.isEmpty());
            assertFalse(value.isEmpty());
            value.forEach(item -> assertFalse(item.street.isEmpty()));
        });
    }

    @Generatable(implementations = Dog.class)
    public interface Animal { }
    @Generatable(implementations = Dog.class)
    public abstract static class Pet implements Animal { }
    @Generatable
    public static class Dog extends Pet {
        public Dog() { }
    }

    @Test
    void resolvesNewInterfacesAndAbstractClasses() {
        assertInstanceOf(Dog.class, generator.generateValueOfType(Animal.class));
        assertInstanceOf(Dog.class, generator.generateValueOfType(Pet.class));
    }

    public static class Unmarked { public Unmarked() { } }
    @Generatable public interface MissingImplementation { }
    @Generatable(implementations = String.class) public interface WrongImplementation { }
    @Generatable(implementations = UnmarkedChild.class) public interface UnmarkedBase { }
    public static class UnmarkedChild implements UnmarkedBase { public UnmarkedChild() { } }
    @Generatable(implementations = Pet.class) public interface AbstractImplementation { }
    @Generatable(implementations = Dog.class) public static class InvalidConcrete { }
    @Generatable public static class PrivateConstructor { private PrivateConstructor() { } }
    @Generatable public static class Rejected {
        public Rejected(int value) { throw new IllegalStateException("rejected"); }
    }
    @Generatable public static class Wildcards { public Wildcards(List<?> values) { } }
    @Generatable public static class TypeVariable<T> { public TypeVariable(T value) { } }

    @Test
    void reportsUnsupportedRequestsWithoutReturningNull() {
        for (Class<?> type : new Class<?>[]{Unmarked.class, MissingImplementation.class,
                WrongImplementation.class, UnmarkedBase.class, AbstractImplementation.class,
                InvalidConcrete.class, PrivateConstructor.class, Wildcards.class, TypeVariable.class,
                Empty.class, void.class, Void.class, Set.class}) {
            GenerationException error = assertThrows(GenerationException.class,
                    () -> generator.generateValueOfType(type), type.getName());
            assertFalse(error.getMessage().isBlank());
        }
        assertThrows(GenerationException.class, () -> generator.generateValueOfType(null));
    }

    @Test
    void preservesConstructorFailureCause() {
        GenerationException error = assertThrows(GenerationException.class,
                () -> generator.generateValueOfType(Rejected.class));
        assertTrue(error.getMessage().contains(Rejected.class.getName()));
        assertEquals(1, error.getSuppressed().length);
        assertInstanceOf(IllegalStateException.class, error.getSuppressed()[0].getCause());
    }

    @Generatable
    public static class Fallback {
        final String value;
        public Fallback(Unmarked unsupported) { value = "unused"; }
        public Fallback() { value = "fallback"; }
    }

    @Test
    void triesAnotherConstructorWhenArgumentsCannotBeGenerated() {
        assertEquals("fallback", generator.generateValueOfType(Fallback.class).value);
    }
}
