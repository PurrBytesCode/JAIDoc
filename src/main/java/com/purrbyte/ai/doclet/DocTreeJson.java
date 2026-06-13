package com.purrbyte.ai.doclet;

import com.sun.source.doctree.*;
import com.sun.source.util.DocTrees;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import javax.lang.model.element.Element;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Serializes Javadoc comment trees ({@code com.sun.source.doctree})
 * into two representations:
 *
 * <ul>
 *   <li><b>Structured</b>: each tree node as a JSON object with its
 *       {@code kind} and own fields ({@code {@link}} references, HTML
 *       attributes, block tags, etc.).</li>
 *   <li><b>Plain text</b>: a human-readable representation (no HTML) designed for
 *       generating embeddings in ChromaDB.</li>
 * </ul>
 *
 * <p>Forward compatibility: nodes whose type does not exist in JDK 17
 * (e.g. {@code SNIPPET} from JDK 18, {@code SPEC} from JDK 20 or Markdown text
 * from JDK 23+) are serialized generically with their {@code kind} and
 * {@code toString()}, so the doclet works unchanged on JDK 18..27.
 */
final class DocTreeJson {

    private final JsonMapper mapper;

    DocTreeJson(JsonMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Complete comment of an element, or {@code null} if it has no Javadoc.
     */
    ObjectNode comment(DocTrees trees, Element e) {
        DocCommentTree dct = trees.getDocCommentTree(e);
        if (dct == null) return null;

        ObjectNode n = mapper.createObjectNode();
        String first = text(dct.getFirstSentence());
        if (!first.isEmpty()) n.put("firstSentence", first);

        String bodyText = text(dct.getFullBody());
        if (!bodyText.isEmpty()) n.put("bodyText", bodyText);
        n.set("body", nodes(dct.getFullBody()));

        List<? extends DocTree> blockTags = dct.getBlockTags();
        if (!blockTags.isEmpty()) {
            ArrayNode tags = n.putArray("blockTags");
            for (DocTree t : blockTags) tags.add(node(t));
        }
        return n;
    }

    /**
     * Complete plain text (body + block tags) for embeddings.
     */
    String fullText(DocTrees trees, Element e) {
        DocCommentTree dct = trees.getDocCommentTree(e);
        if (dct == null) return "";
        StringBuilder sb = new StringBuilder(text(dct.getFullBody()).trim());
        StringBuilder tags = new StringBuilder();
        for (DocTree t : dct.getBlockTags()) {
            String line = plainBlockTag(t).trim();
            if (!line.isEmpty()) tags.append(line).append('\n');
        }
        if (tags.length() > 0) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(tags.toString().trim());
        }
        return sb.toString().trim();
    }

    /**
     * Name → description map of the {@code @param} tags (or {@code @param <T>}).
     */
    Map<String, String> paramDescriptions(DocTrees trees, Element e, boolean typeParams) {
        Map<String, String> out = new LinkedHashMap<>();
        DocCommentTree dct = trees.getDocCommentTree(e);
        if (dct == null) return out;
        for (DocTree t : dct.getBlockTags()) {
            if (t instanceof ParamTree pt && pt.isTypeParameter() == typeParams) {
                out.put(pt.getName().getName().toString(), text(pt.getDescription()).trim());
            }
        }
        return out;
    }

    /**
     * Exception name → description map of the {@code @throws}/{@code @exception} tags.
     */
    Map<String, String> throwsDescriptions(DocTrees trees, Element e) {
        Map<String, String> out = new LinkedHashMap<>();
        DocCommentTree dct = trees.getDocCommentTree(e);
        if (dct == null) return out;
        for (DocTree t : dct.getBlockTags()) {
            if (t instanceof ThrowsTree tt) {
                out.put(tt.getExceptionName().getSignature(), text(tt.getDescription()).trim());
            }
        }
        return out;
    }

    /**
     * Text of the {@code @deprecated} tag, or {@code null}.
     */
    String deprecatedDescription(DocTrees trees, Element e) {
        DocCommentTree dct = trees.getDocCommentTree(e);
        if (dct == null) return null;
        for (DocTree t : dct.getBlockTags()) {
            if (t instanceof DeprecatedTree dt) return text(dt.getBody()).trim();
        }
        return null;
    }

    /**
     * Value of the {@code @since} tag, or {@code null}.
     */
    String sinceTag(DocTrees trees, Element e) {
        DocCommentTree dct = trees.getDocCommentTree(e);
        if (dct == null) return null;
        for (DocTree t : dct.getBlockTags()) {
            if (t instanceof SinceTree st) return text(st.getBody()).trim();
        }
        return null;
    }

    // ------------------------------------------------------------------ JSON estructurado

    ArrayNode nodes(List<? extends DocTree> list) {
        ArrayNode arr = mapper.createArrayNode();
        for (DocTree t : list) arr.add(node(t));
        return arr;
    }

    ObjectNode node(DocTree t) {
        ObjectNode n = mapper.createObjectNode();
        n.put("kind", t.getKind().name());

        if (t instanceof TextTree tt) {
            n.put("text", tt.getBody());
        } else if (t instanceof LiteralTree lt) {           // {@code} y {@literal}
            n.put("body", lt.getBody().getBody());
        } else if (t instanceof LinkTree lt) {              // {@link} y {@linkplain}
            n.put("reference", lt.getReference().getSignature());
            String label = text(lt.getLabel());
            if (!label.isEmpty()) n.put("label", label);
        } else if (t instanceof EntityTree et) {
            n.put("name", et.getName().toString());
            n.put("text", decodeEntity(et.getName().toString()));
        } else if (t instanceof StartElementTree st) {
            n.put("name", st.getName().toString());
            n.put("selfClosing", st.isSelfClosing());
            if (!st.getAttributes().isEmpty()) n.set("attributes", nodes(st.getAttributes()));
        } else if (t instanceof EndElementTree et) {
            n.put("name", et.getName().toString());
        } else if (t instanceof AttributeTree at) {
            n.put("name", at.getName().toString());
            if (at.getValue() != null) n.put("value", text(at.getValue()));
        } else if (t instanceof ParamTree pt) {
            n.put("isTypeParameter", pt.isTypeParameter());
            n.put("name", pt.getName().getName().toString());
            n.put("description", text(pt.getDescription()));
            n.set("descriptionNodes", nodes(pt.getDescription()));
        } else if (t instanceof ReturnTree rt) {
            n.put("description", text(rt.getDescription()));
            n.set("descriptionNodes", nodes(rt.getDescription()));
        } else if (t instanceof ThrowsTree tt) {            // @throws y @exception
            n.put("exception", tt.getExceptionName().getSignature());
            n.put("description", text(tt.getDescription()));
        } else if (t instanceof SeeTree st) {
            n.put("reference", text(st.getReference()));
            n.set("referenceNodes", nodes(st.getReference()));
        } else if (t instanceof SinceTree st) {
            n.put("body", text(st.getBody()));
        } else if (t instanceof AuthorTree at) {
            n.put("name", text(at.getName()));
        } else if (t instanceof VersionTree vt) {
            n.put("body", text(vt.getBody()));
        } else if (t instanceof DeprecatedTree dt) {
            n.put("body", text(dt.getBody()));
        } else if (t instanceof SerialTree st) {
            n.put("description", text(st.getDescription()));
        } else if (t instanceof SerialDataTree st) {
            n.put("description", text(st.getDescription()));
        } else if (t instanceof SerialFieldTree st) {
            n.put("name", st.getName().getName().toString());
            n.put("type", st.getType().getSignature());
            n.put("description", text(st.getDescription()));
        } else if (t instanceof ValueTree vt) {
            if (vt.getReference() != null) n.put("reference", vt.getReference().getSignature());
        } else if (t instanceof InheritDocTree) {
            // sin campos extra: {"kind":"INHERIT_DOC"}
        } else if (t instanceof IndexTree it) {
            n.put("term", plainInline(it.getSearchTerm()));
            n.put("description", text(it.getDescription()));
        } else if (t instanceof SummaryTree st) {
            n.put("summary", text(st.getSummary()));
        } else if (t instanceof SystemPropertyTree st) {
            n.put("propertyName", st.getPropertyName().toString());
        } else if (t instanceof HiddenTree ht) {
            n.put("body", text(ht.getBody()));
        } else if (t instanceof ProvidesTree pt) {
            n.put("serviceType", pt.getServiceType().getSignature());
            n.put("description", text(pt.getDescription()));
        } else if (t instanceof UsesTree ut) {
            n.put("serviceType", ut.getServiceType().getSignature());
            n.put("description", text(ut.getDescription()));
        } else if (t instanceof CommentTree ct) {
            n.put("body", ct.getBody());
        } else if (t instanceof UnknownBlockTagTree ut) {
            n.put("tagName", ut.getTagName());
            n.put("content", text(ut.getContent()));
            n.set("contentNodes", nodes(ut.getContent()));
        } else if (t instanceof UnknownInlineTagTree ut) {
            n.put("tagName", ut.getTagName());
            n.put("content", text(ut.getContent()));
        } else if (t instanceof ErroneousTree et) {
            n.put("body", et.getBody());
            if (et.getDiagnostic() != null) {
                n.put("diagnostic", et.getDiagnostic().getMessage(null));
            }
        } else {
            // Nodes from JDKs after 17 (SNIPPET, SPEC, MARKDOWN/RAW_TEXT, ...):
            // generic serialization to maintain compatibility through JDK 27+.
            n.put("text", t.toString());
        }
        return n;
    }

    /**
     * Renders a list of inline nodes as plain text.
     */
    String text(List<? extends DocTree> list) {
        if (list == null) return "";
        StringBuilder sb = new StringBuilder();
        for (DocTree t : list) sb.append(plainInline(t));
        return normalize(sb.toString());
    }

    private String plainInline(DocTree t) {
        if (t instanceof TextTree tt) {
            return tt.getBody();
        } else if (t instanceof LiteralTree lt) {
            return lt.getBody().getBody();
        } else if (t instanceof LinkTree lt) {
            String label = rawText(lt.getLabel());
            return label.isEmpty() ? lt.getReference().getSignature() : label;
        } else if (t instanceof EntityTree et) {
            return decodeEntity(et.getName().toString());
        } else if (t instanceof StartElementTree st) {
            String name = st.getName().toString().toLowerCase();
            return BLOCK_HTML.contains(name) ? "\n" : "";
        } else if (t instanceof EndElementTree et) {
            String name = et.getName().toString().toLowerCase();
            return ("p".equals(name) || "ul".equals(name) || "ol".equals(name)
                    || "table".equals(name) || "pre".equals(name)) ? "\n" : "";
        } else if (t instanceof ValueTree vt) {
            return vt.getReference() != null ? vt.getReference().getSignature() : "";
        } else if (t instanceof InheritDocTree) {
            return "{@inheritDoc}";
        } else if (t instanceof IndexTree it) {
            return plainInline(it.getSearchTerm());
        } else if (t instanceof SummaryTree st) {
            return rawText(st.getSummary());
        } else if (t instanceof SystemPropertyTree st) {
            return st.getPropertyName().toString();
        } else if (t instanceof UnknownInlineTagTree ut) {
            return rawText(ut.getContent());
        } else if (t instanceof CommentTree) {
            return "";
        } else if (t instanceof ErroneousTree et) {
            return et.getBody();
        } else if (t instanceof AttributeTree) {
            return "";
        } else {
            // SNIPPET, MARKDOWN and other nodes from JDK > 17
            return t.toString();
        }
    }

    /**
     * Same as {@link #text(List)} but without normalization (internal use).
     */
    private String rawText(List<? extends DocTree> list) {
        if (list == null) return "";
        StringBuilder sb = new StringBuilder();
        for (DocTree t : list) sb.append(plainInline(t));
        return sb.toString().trim();
    }

    /**
     * Renders a block tag as a "@tag ..." line.
     */
    private String plainBlockTag(DocTree t) {
        if (t instanceof ParamTree pt) {
            String name = pt.getName().getName().toString();
            if (pt.isTypeParameter()) name = "<" + name + ">";
            return "@param " + name + " " + text(pt.getDescription());
        } else if (t instanceof ReturnTree rt) {
            return "@return " + text(rt.getDescription());
        } else if (t instanceof ThrowsTree tt) {
            return "@throws " + tt.getExceptionName().getSignature()
                    + " " + text(tt.getDescription());
        } else if (t instanceof SeeTree st) {
            return "@see " + text(st.getReference());
        } else if (t instanceof SinceTree st) {
            return "@since " + text(st.getBody());
        } else if (t instanceof AuthorTree at) {
            return "@author " + text(at.getName());
        } else if (t instanceof VersionTree vt) {
            return "@version " + text(vt.getBody());
        } else if (t instanceof DeprecatedTree dt) {
            return "@deprecated " + text(dt.getBody());
        } else if (t instanceof SerialTree st) {
            return "@serial " + text(st.getDescription());
        } else if (t instanceof SerialDataTree st) {
            return "@serialData " + text(st.getDescription());
        } else if (t instanceof SerialFieldTree st) {
            return "@serialField " + st.getName().getName() + " "
                    + st.getType().getSignature() + " " + text(st.getDescription());
        } else if (t instanceof HiddenTree ht) {
            return "@hidden " + text(ht.getBody());
        } else if (t instanceof ProvidesTree pt) {
            return "@provides " + pt.getServiceType().getSignature()
                    + " " + text(pt.getDescription());
        } else if (t instanceof UsesTree ut) {
            return "@uses " + ut.getServiceType().getSignature()
                    + " " + text(ut.getDescription());
        } else if (t instanceof UnknownBlockTagTree ut) {
            return "@" + ut.getTagName() + " " + text(ut.getContent());
        } else {
            return t.toString();
        }
    }

    private static final Set<String> BLOCK_HTML = Set.of(
            "p", "br", "li", "tr", "div", "hr", "blockquote", "pre",
            "h1", "h2", "h3", "h4", "h5", "h6", "dt", "dd");

    static String decodeEntity(String name) {
        if (name.startsWith("#")) {
            try {
                int cp = name.startsWith("#x") || name.startsWith("#X")
                        ? Integer.parseInt(name.substring(2), 16)
                        : Integer.parseInt(name.substring(1));
                return new String(Character.toChars(cp));
            } catch (NumberFormatException e) {
                return "&" + name + ";";
            }
        }
        switch (name) {
            case "amp":
                return "&";
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "quot":
                return "\"";
            case "apos":
                return "'";
            case "nbsp":
                return " ";
            case "copy":
                return "\u00a9";
            case "reg":
                return "\u00ae";
            case "trade":
                return "\u2122";
            case "hellip":
                return "\u2026";
            case "mdash":
                return "\u2014";
            case "ndash":
                return "\u2013";
            default:
                return "&" + name + ";";
        }
    }

    /**
     * Collapses repeated spaces while preserving significant newlines.
     */
    static String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean lastSpace = false;
        boolean lastNewline = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                if (!lastNewline) sb.append('\n');
                lastNewline = true;
                lastSpace = true;
            } else if (Character.isWhitespace(c)) {
                if (!lastSpace) sb.append(' ');
                lastSpace = true;
                lastNewline = false;
            } else {
                sb.append(c);
                lastSpace = false;
                lastNewline = false;
            }
        }
        return sb.toString().trim();
    }
}
