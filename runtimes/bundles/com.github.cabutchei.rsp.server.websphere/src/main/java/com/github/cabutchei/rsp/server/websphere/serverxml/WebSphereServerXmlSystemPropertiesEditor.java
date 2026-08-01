package com.github.cabutchei.rsp.server.websphere.serverxml;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.cabutchei.rsp.eclipse.core.runtime.CoreException;
import com.github.cabutchei.rsp.eclipse.core.runtime.IStatus;
import com.github.cabutchei.rsp.eclipse.core.runtime.Status;

public final class WebSphereServerXmlSystemPropertiesEditor {
	private static final String[] JVM_ELEMENT_NAMES = { "jvmEntries", "JavaVirtualMachine" };
	private static final String SYSTEM_PROPERTIES_ELEMENT = "systemProperties";
	private static final String DEFAULT_INDENT_UNIT = "\t";

	private WebSphereServerXmlSystemPropertiesEditor() {
		// utility class
	}

	public static void writeSystemProperties(String serverXmlPath, Map<String, String> systemProperties) throws Exception {
		Map<String, String> desired = sanitizeSystemPropertiesPreservingOrder(systemProperties);
		Path path = Path.of(serverXmlPath);
		String xml = Files.readString(path, StandardCharsets.UTF_8);
		String updated = rewriteSystemProperties(xml, desired);
		Files.writeString(path, updated, StandardCharsets.UTF_8);
	}

	public static Map<String, String> readSystemProperties(String serverXmlPath) throws Exception {
		Path path = Path.of(serverXmlPath);
		String xml = Files.readString(path, StandardCharsets.UTF_8);
		XmlElementRange jvmElement = locateJvmElement(xml);
		Map<String, String> systemProperties = new LinkedHashMap<>();
		for (SystemPropertyElement property : parseSystemProperties(xml.substring(jvmElement.contentStart,
				jvmElement.endTagStart))) {
			if (property.name == null || property.name.trim().isEmpty()) {
				continue;
			}
			systemProperties.put(property.name, property.value == null ? "" : property.value);
		}
		return systemProperties;
	}

	private static Map<String, String> sanitizeSystemPropertiesPreservingOrder(Map<String, String> systemProperties) {
		Map<String, String> sanitized = new LinkedHashMap<>();
		if (systemProperties == null) {
			return sanitized;
		}
		for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
			String name = entry.getKey();
			if (name == null) {
				continue;
			}
			String trimmed = name.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			sanitized.put(trimmed, entry.getValue() == null ? "" : entry.getValue());
		}
		return sanitized;
	}

	private static String rewriteSystemProperties(String xml, Map<String, String> desired) throws CoreException {
		XmlElementRange jvmElement = locateJvmElement(xml);
		String newline = detectLineSeparator(xml);
		String jvmContent = xml.substring(jvmElement.contentStart, jvmElement.endTagStart);
		List<SystemPropertyElement> existingProperties = parseSystemProperties(jvmContent);
		String propertyIndent = determinePropertyIndent(xml, jvmElement, existingProperties, newline);
		Map<String, SystemPropertyElement> existingByName = new LinkedHashMap<>();
		for (SystemPropertyElement property : existingProperties) {
			if (property.name != null && !property.name.trim().isEmpty() && !existingByName.containsKey(property.name)) {
				existingByName.put(property.name, property);
			}
		}

		String replacementBlock = renderSystemPropertiesBlock(desired, existingByName, propertyIndent, newline);
		StringBuilder updated = new StringBuilder(xml.length() + Math.max(0, replacementBlock.length()));
		if (!existingProperties.isEmpty()) {
			SystemPropertyElement first = existingProperties.get(0);
			SystemPropertyElement last = existingProperties.get(existingProperties.size() - 1);
			int replaceStart = jvmElement.contentStart + lineStart(jvmContent, first.start);
			int replaceEnd = jvmElement.contentStart + extendPastTrailingNewline(jvmContent, last.end, newline);
			updated.append(xml, 0, replaceStart);
			updated.append(replacementBlock);
			updated.append(xml.substring(replaceEnd));
			return updated.toString();
		}

		if (jvmElement.selfClosing) {
			String closeTag = "</" + jvmElement.tagName + ">";
			updated.append(xml, 0, jvmElement.startTagEnd);
			updated.setLength(stripSelfClosingSlash(updated));
			updated.append(">");
			if (!replacementBlock.isEmpty()) {
				updated.append(newline).append(replacementBlock);
			}
			updated.append(detectElementIndent(xml, jvmElement.startTagStart)).append(closeTag);
			updated.append(xml.substring(jvmElement.startTagEnd + 1));
			return updated.toString();
		}

		int insertAt = lineStart(xml, jvmElement.endTagStart);
		updated.append(xml, 0, insertAt);
		updated.append(replacementBlock);
		updated.append(xml.substring(insertAt));
		return updated.toString();
	}

	private static XmlElementRange locateJvmElement(String xml) throws CoreException {
		XmlElementRange best = null;
		for (String tagName : JVM_ELEMENT_NAMES) {
			XmlElementRange candidate = locateFirstElement(xml, tagName);
			if (candidate == null) {
				continue;
			}
			if (best == null || candidate.startTagStart < best.startTagStart) {
				best = candidate;
			}
		}
		if (best != null) {
			return best;
		}
		throw new CoreException(new Status(IStatus.ERROR,
				com.github.cabutchei.rsp.server.websphere.WebSpherePluginConstants.BUNDLE_ID,
				"Unable to locate JavaVirtualMachine entry in server.xml"));
	}

	private static XmlElementRange locateFirstElement(String xml, String tagName) {
		int searchFrom = 0;
		while (searchFrom < xml.length()) {
			int start = xml.indexOf("<" + tagName, searchFrom);
			if (start == -1) {
				return null;
			}
			int nameEnd = start + tagName.length() + 1;
			if (nameEnd < xml.length() && isXmlNameChar(xml.charAt(nameEnd))) {
				searchFrom = nameEnd;
				continue;
			}
			int startTagEnd = findTagEnd(xml, start);
			boolean selfClosing = isSelfClosingTag(xml, startTagEnd);
			if (selfClosing) {
				return new XmlElementRange(tagName, start, startTagEnd, startTagEnd, startTagEnd, true);
			}
			String closeTag = "</" + tagName + ">";
			int endTagStart = xml.indexOf(closeTag, startTagEnd + 1);
			if (endTagStart == -1) {
				return null;
			}
			return new XmlElementRange(tagName, start, startTagEnd, startTagEnd + 1, endTagStart, false);
		}
		return null;
	}

	private static List<SystemPropertyElement> parseSystemProperties(String xmlFragment) {
		List<SystemPropertyElement> properties = new ArrayList<>();
		int searchFrom = 0;
		while (searchFrom < xmlFragment.length()) {
			int start = xmlFragment.indexOf("<" + SYSTEM_PROPERTIES_ELEMENT, searchFrom);
			if (start == -1) {
				return properties;
			}
			int openTagEnd = findTagEnd(xmlFragment, start);
			boolean selfClosing = isSelfClosingTag(xmlFragment, openTagEnd);
			int attrStart = start + SYSTEM_PROPERTIES_ELEMENT.length() + 1;
			int attrEnd = selfClosing ? trimTrailingSlash(xmlFragment, attrStart, openTagEnd) : openTagEnd;
			Map<String, String> attrs = parseAttributes(xmlFragment.substring(attrStart, attrEnd));
			int end = openTagEnd + 1;
			if (!selfClosing) {
				String closeTag = "</" + SYSTEM_PROPERTIES_ELEMENT + ">";
				int closeStart = xmlFragment.indexOf(closeTag, openTagEnd + 1);
				if (closeStart == -1) {
					break;
				}
				end = closeStart + closeTag.length();
			}
			properties.add(new SystemPropertyElement(start, end, attrs.get("name"), attrs.get("value"),
					attrs.get("xmi:id")));
			searchFrom = end;
		}
		return properties;
	}

	private static Map<String, String> parseAttributes(String rawAttributes) {
		Map<String, String> attrs = new HashMap<>();
		if (rawAttributes == null || rawAttributes.isEmpty()) {
			return attrs;
		}
		int index = 0;
		while (index < rawAttributes.length()) {
			while (index < rawAttributes.length() && Character.isWhitespace(rawAttributes.charAt(index))) {
				index++;
			}
			if (index >= rawAttributes.length()) {
				return attrs;
			}
			int nameStart = index;
			while (index < rawAttributes.length() && rawAttributes.charAt(index) != '='
					&& !Character.isWhitespace(rawAttributes.charAt(index))) {
				index++;
			}
			String name = rawAttributes.substring(nameStart, index);
			while (index < rawAttributes.length() && Character.isWhitespace(rawAttributes.charAt(index))) {
				index++;
			}
			if (index >= rawAttributes.length() || rawAttributes.charAt(index) != '=') {
				return attrs;
			}
			index++;
			while (index < rawAttributes.length() && Character.isWhitespace(rawAttributes.charAt(index))) {
				index++;
			}
			if (index >= rawAttributes.length()) {
				return attrs;
			}
			char quote = rawAttributes.charAt(index);
			if (quote != '"' && quote != '\'') {
				return attrs;
			}
			index++;
			int valueStart = index;
			while (index < rawAttributes.length() && rawAttributes.charAt(index) != quote) {
				index++;
			}
			if (index > rawAttributes.length()) {
				return attrs;
			}
			String value = rawAttributes.substring(valueStart, Math.min(index, rawAttributes.length()));
			attrs.put(name, unescapeXml(value));
			index++;
		}
		return attrs;
	}

	private static String renderSystemPropertiesBlock(Map<String, String> desired,
			Map<String, SystemPropertyElement> existingByName, String indent, String newline) {
		if (desired.isEmpty()) {
			return "";
		}
		StringBuilder rendered = new StringBuilder();
		for (Map.Entry<String, String> entry : desired.entrySet()) {
			String name = entry.getKey();
			if (name == null || name.trim().isEmpty()) {
				continue;
			}
			SystemPropertyElement existing = existingByName.get(name);
			String xmiId = existing != null && existing.xmiId != null && !existing.xmiId.isBlank()
					? existing.xmiId
					: generatePropertyXmiId();
			rendered.append(indent)
					.append("<").append(SYSTEM_PROPERTIES_ELEMENT)
					.append(" xmi:id=\"").append(escapeXml(xmiId)).append("\"")
					.append(" name=\"").append(escapeXml(name)).append("\"")
					.append(" value=\"").append(escapeXml(entry.getValue() == null ? "" : entry.getValue())).append("\"")
					.append(" required=\"false\"/>")
					.append(newline);
		}
		return rendered.toString();
	}

	private static String determinePropertyIndent(String xml, XmlElementRange jvmElement,
			List<SystemPropertyElement> existingProperties, String newline) {
		if (!existingProperties.isEmpty()) {
			int lineStart = lineStart(xml.substring(jvmElement.contentStart, jvmElement.endTagStart),
					existingProperties.get(0).start);
			return xml.substring(jvmElement.contentStart + lineStart,
					jvmElement.contentStart + existingProperties.get(0).start);
		}
		String jvmContent = xml.substring(jvmElement.contentStart, jvmElement.endTagStart);
		int cursor = 0;
		while (cursor < jvmContent.length()) {
			int lineEnd = jvmContent.indexOf(newline, cursor);
			if (lineEnd == -1) {
				lineEnd = jvmContent.length();
			}
			String line = jvmContent.substring(cursor, lineEnd);
			if (!line.trim().isEmpty()) {
				int marker = line.indexOf('<');
				if (marker > 0 && line.substring(0, marker).trim().isEmpty()) {
					return line.substring(0, marker);
				}
			}
			cursor = lineEnd + newline.length();
		}
		return detectElementIndent(xml, jvmElement.startTagStart) + DEFAULT_INDENT_UNIT;
	}

	private static String detectLineSeparator(String text) {
		return text.contains("\r\n") ? "\r\n" : "\n";
	}

	private static int findTagEnd(String xml, int start) {
		boolean inSingleQuote = false;
		boolean inDoubleQuote = false;
		for (int i = start; i < xml.length(); i++) {
			char current = xml.charAt(i);
			if (current == '\'' && !inDoubleQuote) {
				inSingleQuote = !inSingleQuote;
			} else if (current == '"' && !inSingleQuote) {
				inDoubleQuote = !inDoubleQuote;
			} else if (current == '>' && !inSingleQuote && !inDoubleQuote) {
				return i;
			}
		}
		return xml.length() - 1;
	}

	private static boolean isSelfClosingTag(String xml, int tagEnd) {
		for (int i = tagEnd - 1; i >= 0; i--) {
			char current = xml.charAt(i);
			if (Character.isWhitespace(current)) {
				continue;
			}
			return current == '/';
		}
		return false;
	}

	private static int trimTrailingSlash(String xml, int start, int tagEnd) {
		for (int i = tagEnd - 1; i >= start; i--) {
			char current = xml.charAt(i);
			if (Character.isWhitespace(current)) {
				continue;
			}
			return current == '/' ? i : i + 1;
		}
		return start;
	}

	private static int stripSelfClosingSlash(StringBuilder builder) {
		int length = builder.length();
		while (length > 0 && Character.isWhitespace(builder.charAt(length - 1))) {
			length--;
		}
		if (length > 0 && builder.charAt(length - 1) == '/') {
			return length - 1;
		}
		return builder.length();
	}

	private static int lineStart(String text, int index) {
		int previousNewline = text.lastIndexOf('\n', Math.max(0, index - 1));
		if (previousNewline == -1) {
			return 0;
		}
		return previousNewline + 1;
	}

	private static int extendPastTrailingNewline(String text, int index, String newline) {
		if (index + newline.length() <= text.length() && text.startsWith(newline, index)) {
			return index + newline.length();
		}
		return index;
	}

	private static String detectElementIndent(String xml, int elementStart) {
		int lineStart = lineStart(xml, elementStart);
		return xml.substring(lineStart, elementStart);
	}

	private static boolean isXmlNameChar(char c) {
		return Character.isLetterOrDigit(c) || c == ':' || c == '_' || c == '-' || c == '.';
	}

	private static String escapeXml(String value) {
		return value.replace("&", "&amp;")
				.replace("\"", "&quot;")
				.replace("<", "&lt;")
				.replace(">", "&gt;")
				.replace("'", "&apos;");
	}

	private static String unescapeXml(String value) {
		return value.replace("&quot;", "\"")
				.replace("&apos;", "'")
				.replace("&lt;", "<")
				.replace("&gt;", ">")
				.replace("&amp;", "&");
	}

	private static String generatePropertyXmiId() {
		return "CustomProperty_" + UUID.randomUUID().toString().replace("-", "");
	}

	private static final class XmlElementRange {
		private final String tagName;
		private final int startTagStart;
		private final int startTagEnd;
		private final int contentStart;
		private final int endTagStart;
		private final boolean selfClosing;

		private XmlElementRange(String tagName, int startTagStart, int startTagEnd, int contentStart, int endTagStart,
				boolean selfClosing) {
			this.tagName = tagName;
			this.startTagStart = startTagStart;
			this.startTagEnd = startTagEnd;
			this.contentStart = contentStart;
			this.endTagStart = endTagStart;
			this.selfClosing = selfClosing;
		}
	}

	private static final class SystemPropertyElement {
		private final int start;
		private final int end;
		private final String name;
		private final String value;
		private final String xmiId;

		private SystemPropertyElement(int start, int end, String name, String value, String xmiId) {
			this.start = start;
			this.end = end;
			this.name = name;
			this.value = value;
			this.xmiId = xmiId;
		}
	}
}
