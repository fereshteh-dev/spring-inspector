package io.github.fereshtehdev.springinspector.project.maven;

import io.github.fereshtehdev.springinspector.project.maven.Pom.Coordinates;
import io.github.fereshtehdev.springinspector.project.maven.Pom.Plugin;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

final class PomReader {

    private static final String DEFAULT_PLUGIN_GROUP = "org.apache.maven.plugins";

    private PomReader() {}

    static Pom parse(String xml) {
        Element project = parseDocument(xml);
        Optional<Element> build = child(project, "build");
        List<Plugin> plugins = new ArrayList<>();
        build.ifPresent(b -> {
            plugins.addAll(plugins(child(b, "plugins")));
            plugins.addAll(plugins(child(b, "pluginManagement").flatMap(pm -> child(pm, "plugins"))));
        });
        return new Pom(
                child(project, "parent").map(PomReader::coordinates),
                properties(project),
                dependencies(child(project, "dependencyManagement").flatMap(dm -> child(dm, "dependencies"))),
                dependencies(child(project, "dependencies")),
                plugins,
                child(project, "modules").stream()
                        .flatMap(modules -> children(modules, "module").stream())
                        .map(PomReader::text)
                        .toList());
    }

    private static Element parseDocument(String xml) {
        try {
            DocumentBuilder builder = secureFactory().newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            builder.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("External entities are not allowed");
            });
            Element root = builder.parse(new InputSource(new StringReader(xml))).getDocumentElement();
            if (!root.getTagName().equals("project")) {
                throw new IllegalArgumentException("root element is <" + root.getTagName() + ">, not <project>");
            }
            return root;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    private static DocumentBuilderFactory secureFactory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setNamespaceAware(false);
        return factory;
    }

    private static Map<String, String> properties(Element project) {
        Map<String, String> properties = new LinkedHashMap<>();
        child(project, "properties").ifPresent(element -> {
            for (Element property : children(element)) {
                properties.put(property.getTagName(), text(property));
            }
        });
        return properties;
    }

    private static List<Coordinates> dependencies(Optional<Element> dependencies) {
        return dependencies.stream()
                .flatMap(element -> children(element, "dependency").stream())
                .map(PomReader::coordinates)
                .toList();
    }

    private static List<Plugin> plugins(Optional<Element> plugins) {
        return plugins.stream()
                .flatMap(element -> children(element, "plugin").stream())
                .map(PomReader::plugin)
                .toList();
    }

    private static Plugin plugin(Element plugin) {
        Coordinates coordinates = new Coordinates(
                childText(plugin, "groupId").orElse(DEFAULT_PLUGIN_GROUP),
                childText(plugin, "artifactId").orElse(""),
                childText(plugin, "version"));
        Map<String, String> configuration = new LinkedHashMap<>();
        child(plugin, "configuration").ifPresent(config -> {
            for (String key : List.of("release", "source", "target")) {
                childText(config, key).ifPresent(value -> configuration.put(key, value));
            }
            child(config, "toolchains")
                    .flatMap(toolchains -> child(toolchains, "jdk"))
                    .flatMap(jdk -> childText(jdk, "version"))
                    .ifPresent(version -> configuration.put("toolchains.jdk.version", version));
        });
        return new Plugin(coordinates, configuration);
    }

    private static Coordinates coordinates(Element element) {
        return new Coordinates(
                childText(element, "groupId").orElse(""),
                childText(element, "artifactId").orElse(""),
                childText(element, "version"));
    }

    private static Optional<Element> child(Element parent, String name) {
        return children(parent, name).stream().findFirst();
    }

    private static List<Element> children(Element parent, String name) {
        return children(parent).stream()
                .filter(element -> element.getTagName().equals(name))
                .toList();
    }

    private static List<Element> children(Element parent) {
        List<Element> elements = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static Optional<String> childText(Element parent, String name) {
        return child(parent, name).map(PomReader::text).filter(text -> !text.isEmpty());
    }

    private static String text(Element element) {
        return element.getTextContent().strip();
    }
}
