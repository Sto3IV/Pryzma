package net.pryzma.iris.shaderpack.materialmap;

import java.util.Map;

public record TagEntry(NamespacedId id, Map<String, String> propertyPredicates) implements Entry {
}
