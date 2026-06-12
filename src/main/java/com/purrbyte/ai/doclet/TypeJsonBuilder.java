package com.purrbyte.ai.doclet;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;
import jdk.javadoc.doclet.DocletEnvironment;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Converts language model elements ({@code javax.lang.model}) and their
 * Javadoc comments into JSON nodes, and emits the corresponding chunks.
 */
final class TypeJsonBuilder {

    private final JsonMapper mapper;
    private final DocTrees trees;
    private final Elements elements;
    private final DocTreeJson docs;
    private final ChunkWriter chunks; // puede ser null (--no-chunks)

    TypeJsonBuilder(DocletEnvironment env, JsonMapper mapper, ChunkWriter chunks) {
        this.mapper = mapper;
        this.trees = env.getDocTrees();
        this.elements = env.getElementUtils();
        this.docs = new DocTreeJson(mapper);
        this.chunks = chunks;
    }

    ObjectNode buildModule(ModuleElement mod) {
        ObjectNode n = mapper.createObjectNode();
        n.put("kind", "MODULE");
        n.put("name", mod.getQualifiedName().toString());
        n.put("open", mod.isOpen());
        addModifiersAnnotationsDoc(n, mod);
        emitChunk(mod, "module " + mod.getQualifiedName(), n);
        return n;
    }

    ObjectNode buildPackage(PackageElement pkg) {
        ObjectNode n = mapper.createObjectNode();
        n.put("kind", "PACKAGE");
        n.put("name", pkg.getQualifiedName().toString());
        ModuleElement mod = elements.getModuleOf(pkg);
        if (mod != null && !mod.isUnnamed()) {
            n.put("module", mod.getQualifiedName().toString());
        }
        addModifiersAnnotationsDoc(n, pkg);
        emitChunk(pkg, "package " + pkg.getQualifiedName(), n);
        return n;
    }

    /**
     * Serializes a type (class, interface, enum, record or annotation) and its members.
     */
    ObjectNode buildType(TypeElement t) {
        ObjectNode n = mapper.createObjectNode();
        n.put("kind", t.getKind().name()); // CLASS | INTERFACE | ENUM | RECORD | ANNOTATION_TYPE
        n.put("name", t.getSimpleName().toString());
        n.put("qualifiedName", t.getQualifiedName().toString());

        PackageElement pkg = elements.getPackageOf(t);
        n.put("package", pkg.getQualifiedName().toString());
        ModuleElement mod = elements.getModuleOf(t);
        if (mod != null && !mod.isUnnamed()) {
            n.put("module", mod.getQualifiedName().toString());
        }

        putModifiers(n, t);
        putAnnotations(n, t);
        putTypeParameters(n, t.getTypeParameters());

        TypeMirror sup = t.getSuperclass();
        if (sup != null && sup.getKind() != TypeKind.NONE) {
            n.put("superclass", sup.toString());
        }
        if (!t.getInterfaces().isEmpty()) {
            ArrayNode arr = n.putArray("interfaces");
            for (TypeMirror i : t.getInterfaces()) arr.add(i.toString());
        }
        // Sealed classes (JDK 17+)
        List<? extends TypeMirror> permits = t.getPermittedSubclasses();
        if (!permits.isEmpty()) {
            ArrayNode arr = n.putArray("permittedSubclasses");
            for (TypeMirror p : permits) arr.add(p.toString());
        }

        putDeprecation(n, t);
        putSource(n, t);
        ObjectNode doc = docs.comment(trees, t);
        if (doc != null) n.set("doc", doc);

        // Record components: the description comes from @param of the class comment
        if (t.getKind() == ElementKind.RECORD && !t.getRecordComponents().isEmpty()) {
            Map<String, String> paramDocs = docs.paramDescriptions(trees, t, false);
            ArrayNode arr = n.putArray("recordComponents");
            for (RecordComponentElement rc : t.getRecordComponents()) {
                ObjectNode c = arr.addObject();
                c.put("name", rc.getSimpleName().toString());
                c.put("type", rc.asType().toString());
                putAnnotations(c, rc);
                String d = paramDocs.get(rc.getSimpleName().toString());
                if (d != null && !d.isEmpty()) c.put("description", d);
            }
        }

        ArrayNode enumConstants = null;
        ArrayNode fields = null;
        ArrayNode constructors = null;
        ArrayNode methods = null;
        ArrayNode nested = null;

        for (Element e : t.getEnclosedElements()) {
            switch (e.getKind()) {
                case ENUM_CONSTANT -> {
                    if (enumConstants == null) enumConstants = n.putArray("enumConstants");
                    enumConstants.add(buildField((VariableElement) e, t));
                }
                case FIELD -> {
                    if (fields == null) fields = n.putArray("fields");
                    fields.add(buildField((VariableElement) e, t));
                }
                case CONSTRUCTOR -> {
                    if (constructors == null) constructors = n.putArray("constructors");
                    constructors.add(buildExecutable((ExecutableElement) e, t));
                }
                case METHOD -> {
                    if (methods == null) methods = n.putArray("methods");
                    methods.add(buildExecutable((ExecutableElement) e, t));
                }
                case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> {
                    if (nested == null) nested = n.putArray("nestedTypes");
                    nested.add(buildType((TypeElement) e)); // recursivo
                }
                default -> { /* RECORD_COMPONENT, etc. ya cubiertos */ }
            }
        }

        emitChunk(t, typeHeader(t), n);
        return n;
    }

    private ObjectNode buildField(VariableElement f, TypeElement owner) {
        ObjectNode n = mapper.createObjectNode();
        n.put("kind", f.getKind().name()); // FIELD | ENUM_CONSTANT
        n.put("name", f.getSimpleName().toString());
        n.put("type", f.asType().toString());
        putModifiers(n, f);
        putAnnotations(n, f);
        Object constant = f.getConstantValue();
        if (constant != null) n.put("constantValue", String.valueOf(constant));
        putDeprecation(n, f);
        putSource(n, f);
        ObjectNode doc = docs.comment(trees, f);
        if (doc != null) n.set("doc", doc);

        String header = (f.getKind() == ElementKind.ENUM_CONSTANT ? "enum constant " : "field ")
                + owner.getQualifiedName() + "." + f.getSimpleName()
                + " : " + f.asType();
        emitChunk(f, header, n);
        return n;
    }

    private ObjectNode buildExecutable(ExecutableElement m, TypeElement owner) {
        boolean ctor = m.getKind() == ElementKind.CONSTRUCTOR;
        ObjectNode n = mapper.createObjectNode();
        n.put("kind", m.getKind().name()); // METHOD | CONSTRUCTOR
        n.put("name", ctor ? owner.getSimpleName().toString() : m.getSimpleName().toString());
        putModifiers(n, m);
        putAnnotations(n, m);
        putTypeParameters(n, m.getTypeParameters());

        if (!ctor) {
            n.put("returnType", m.getReturnType().toString());
        }

        Map<String, String> paramDocs = docs.paramDescriptions(trees, m, false);
        Map<String, String> typeParamDocs = docs.paramDescriptions(trees, m, true);
        Map<String, String> throwsDocs = docs.throwsDescriptions(trees, m);

        // type parameter descriptions (@param <T> ...)
        if (!typeParamDocs.isEmpty() && n.has("typeParameters")) {
            ArrayNode tps = (ArrayNode) n.get("typeParameters");
            for (int i = 0; i < tps.size(); i++) {
                ObjectNode tp = (ObjectNode) tps.get(i);
                String d = typeParamDocs.get(tp.get("name").asString());
                if (d != null && !d.isEmpty()) tp.put("description", d);
            }
        }

        StringJoiner sig = new StringJoiner(", ", "(", ")");
        List<? extends VariableElement> params = m.getParameters();
        if (!params.isEmpty()) {
            ArrayNode arr = n.putArray("parameters");
            for (int i = 0; i < params.size(); i++) {
                VariableElement p = params.get(i);
                ObjectNode pn = arr.addObject();
                pn.put("name", p.getSimpleName().toString());
                pn.put("type", p.asType().toString());
                if (m.isVarArgs() && i == params.size() - 1) pn.put("varargs", true);
                putAnnotations(pn, p);
                String d = paramDocs.get(p.getSimpleName().toString());
                if (d != null && !d.isEmpty()) pn.put("description", d);
                sig.add(p.asType().toString());
            }
        }
        String signature = n.get("name").asString() + sig;
        n.put("signature", signature);

        if (!m.getThrownTypes().isEmpty()) {
            ArrayNode arr = n.putArray("throws");
            for (TypeMirror ex : m.getThrownTypes()) {
                ObjectNode en = arr.addObject();
                en.put("type", ex.toString());
                String d = matchThrows(throwsDocs, ex.toString());
                if (d != null && !d.isEmpty()) en.put("description", d);
            }
        }

        AnnotationValue def = m.getDefaultValue(); // miembros de @interface
        if (def != null) n.put("defaultValue", def.toString());

        putDeprecation(n, m);
        putSource(n, m);
        ObjectNode doc = docs.comment(trees, m);
        if (doc != null) n.set("doc", doc);

        String header = (ctor ? "constructor " : "method ")
                + owner.getQualifiedName() + "#" + signature
                + (ctor ? "" : " -> " + m.getReturnType());
        emitChunk(m, header, n);
        return n;
    }

    /**
     * Matches "@throws SimpleName" with the thrown qualified type.
     */
    private static String matchThrows(Map<String, String> throwsDocs, String qualified) {
        String d = throwsDocs.get(qualified);
        if (d != null) return d;
        String simple = qualified;
        int lt = simple.indexOf('<');
        if (lt >= 0) simple = simple.substring(0, lt);
        int dot = simple.lastIndexOf('.');
        if (dot >= 0) simple = simple.substring(dot + 1);
        return throwsDocs.get(simple);
    }

    private void addModifiersAnnotationsDoc(ObjectNode n, Element e) {
        putAnnotations(n, e);
        putDeprecation(n, e);
        putSource(n, e);
        ObjectNode doc = docs.comment(trees, e);
        if (doc != null) n.set("doc", doc);
    }

    private void putModifiers(ObjectNode n, Element e) {
        if (e.getModifiers().isEmpty()) return;
        ArrayNode arr = n.putArray("modifiers");
        for (Modifier m : e.getModifiers()) arr.add(m.toString());
    }

    private void putAnnotations(ObjectNode n, Element e) {
        List<? extends AnnotationMirror> mirrors = e.getAnnotationMirrors();
        if (mirrors.isEmpty()) return;
        ArrayNode arr = n.putArray("annotations");
        for (AnnotationMirror am : mirrors) {
            ObjectNode a = arr.addObject();
            a.put("type", am.getAnnotationType().toString());
            Map<? extends ExecutableElement, ? extends AnnotationValue> values = am.getElementValues();
            if (!values.isEmpty()) {
                ObjectNode vals = a.putObject("values");
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> en : values.entrySet()) {
                    vals.put(en.getKey().getSimpleName().toString(), en.getValue().toString());
                }
            }
        }
    }

    private void putTypeParameters(ObjectNode n, List<? extends TypeParameterElement> tps) {
        if (tps.isEmpty()) return;
        ArrayNode arr = n.putArray("typeParameters");
        for (TypeParameterElement tp : tps) {
            ObjectNode t = arr.addObject();
            t.put("name", tp.getSimpleName().toString());
            List<? extends TypeMirror> bounds = tp.getBounds();
            boolean onlyObject = bounds.size() == 1
                    && "java.lang.Object".equals(bounds.get(0).toString());
            if (!bounds.isEmpty() && !onlyObject) {
                ArrayNode b = t.putArray("bounds");
                for (TypeMirror bound : bounds) b.add(bound.toString());
            }
        }
    }

    /**
     * @Deprecated information (annotation) and deprecation in general.
     */
    private void putDeprecation(ObjectNode n, Element e) {
        if (!elements.isDeprecated(e)) return;
        ObjectNode d = n.putObject("deprecated");
        d.put("isDeprecated", true);
        for (AnnotationMirror am : e.getAnnotationMirrors()) {
            if ("java.lang.Deprecated".equals(am.getAnnotationType().toString())) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> en
                        : am.getElementValues().entrySet()) {
                    String name = en.getKey().getSimpleName().toString();
                    if ("since".equals(name)) d.put("since", String.valueOf(en.getValue().getValue()));
                    if ("forRemoval".equals(name)) d.put("forRemoval", Boolean.parseBoolean(
                            String.valueOf(en.getValue().getValue())));
                }
            }
        }
        String desc = docs.deprecatedDescription(trees, e);
        if (desc != null && !desc.isEmpty()) d.put("description", desc);
    }

    /**
     * File and line of the element in the source code (if available).
     */
    private void putSource(ObjectNode n, Element e) {
        try {
            TreePath path = trees.getPath(e);
            if (path == null) return;
            CompilationUnitTree cu = path.getCompilationUnit();
            ObjectNode s = n.putObject("source");
            s.put("file", cu.getSourceFile().getName());
            long pos = trees.getSourcePositions().getStartPosition(cu, path.getLeaf());
            LineMap lm = cu.getLineMap();
            if (pos >= 0 && lm != null) {
                s.put("line", lm.getLineNumber(pos));
            }
        } catch (RuntimeException ignored) {
            // position not available (e.g. synthetic elements)
        }
    }

    private String typeHeader(TypeElement t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.getKind().name().toLowerCase().replace("_", " "))
                .append(' ').append(t.getQualifiedName());
        TypeMirror sup = t.getSuperclass();
        if (sup != null && sup.getKind() != TypeKind.NONE
                && !"java.lang.Object".equals(sup.toString())) {
            sb.append(" extends ").append(sup);
        }
        if (!t.getInterfaces().isEmpty()) {
            StringJoiner sj = new StringJoiner(", ");
            for (TypeMirror i : t.getInterfaces()) sj.add(i.toString());
            sb.append(" implements ").append(sj);
        }
        return sb.toString();
    }

    /**
     * Emits a chunk for ChromaDB: text = header (signature) + full comment
     * in plain text; metadata = only primitive values (ChromaDB requirement).
     */
    private void emitChunk(Element e, String header, ObjectNode json) {
        if (chunks == null) return;

        String docText = docs.fullText(trees, e); // "" if no comment
        boolean documented = !docText.isEmpty();

        String id = chunkId(e);
        String text = documented ? header + "\n\n" + docText : header;

        ObjectNode meta = mapper.createObjectNode();
        meta.put("kind", e.getKind().name());
        meta.put("documented", documented);
        TypeElement owner = enclosingType(e);
        if (owner != null) meta.put("type", owner.getQualifiedName().toString());
        PackageElement pkg = elements.getPackageOf(e);
        if (pkg != null) meta.put("package", pkg.getQualifiedName().toString());
        ModuleElement mod = elements.getModuleOf(e);
        if (mod != null && !mod.isUnnamed()) meta.put("module", mod.getQualifiedName().toString());
        if (e instanceof ExecutableElement || e.getKind().isField()) {
            meta.put("member", e.getSimpleName().toString());
        }
        if (json.has("signature")) meta.put("signature", json.get("signature").asString());
        if (json.has("deprecated")) meta.put("deprecated", true);
        String since = docs.sinceTag(trees, e);
        if (since != null) meta.put("since", since);
        if (json.has("source")) {
            ObjectNode src = (ObjectNode) json.get("source");
            if (src.has("file")) meta.put("file", src.get("file").asString());
            if (src.has("line")) meta.put("line", src.get("line").asLong());
        }

        chunks.write(id, text, meta, documented);
    }

    private TypeElement enclosingType(Element e) {
        Element cur = e instanceof TypeElement ? e : e.getEnclosingElement();
        while (cur != null && !(cur instanceof TypeElement)) {
            cur = cur.getEnclosingElement();
        }
        return (TypeElement) cur;
    }

    private String chunkId(Element e) {
        switch (e.getKind()) {
            case MODULE:
                return "module:" + ((ModuleElement) e).getQualifiedName();
            case PACKAGE:
                return "package:" + ((PackageElement) e).getQualifiedName();
            case CLASS:
            case INTERFACE:
            case ENUM:
            case RECORD:
            case ANNOTATION_TYPE:
                return ((TypeElement) e).getQualifiedName().toString();
            case CONSTRUCTOR:
            case METHOD: {
                ExecutableElement m = (ExecutableElement) e;
                TypeElement owner = enclosingType(e);
                StringJoiner sj = new StringJoiner(",", "(", ")");
                for (VariableElement p : m.getParameters()) sj.add(p.asType().toString());
                String name = e.getKind() == ElementKind.CONSTRUCTOR
                        ? "<init>" : m.getSimpleName().toString();
                return owner.getQualifiedName() + "#" + name + sj;
            }
            default:
                TypeElement owner = enclosingType(e);
                return (owner != null ? owner.getQualifiedName() : "?")
                        + "#" + e.getSimpleName();
        }
    }
}
