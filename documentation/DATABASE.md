# Database — Persistencia y búsqueda vectorial del Javadoc JSON

## Resumen

El Javadoc JSON generado por el doclet se persiste en SQLite y se indexa con Hibernate Search/Lucene para búsqueda vectorial filtrada por versión del JDK. La ingesta es un paso explícito que no modifica la generación existente.

## Esquema

```
JdkVersion (jdk_version)            1 ──< DocElement (doc_element)   [rawJson estructural por tipo/paquete/módulo]
   version UNIQUE                   1 ──< DocChunk   (doc_chunk)     [@Indexed: texto + embedding + metadata]
                                              │ ManyToOne (opcional)
                                              └──> DocElement (dueño del chunk)
```

### Tablas

| Tabla | Descripción |
|-------|-------------|
| `jdk_version` | Metadatos de la versión del JDK (versión, conteos, estado de ingesta) |
| `doc_element` | JSON estructural crudo de cada tipo/paquete/módulo |
| `doc_chunk` | Fragmentos buscables con embedding vectorial (384d) |

### Columnas principales de `doc_chunk`

| Columna | Tipo | Descripción |
|---------|------|-------------|
| `version` | TEXT | Versión del JDK (denormalizada, filtro obligatorio del kNN) |
| `chunk_id` | TEXT | Identificador único del chunk |
| `text` | TEXT | Texto del chunk |
| `embedding` | BLOB | Embedding vectorial (384 floats, serializado como BLOB) |
| `kind` | TEXT | Tipo de elemento (MODULE, PACKAGE, TYPE, etc.) |
| `qualifiedType` | TEXT | Nombre completo del tipo |
| `packageName` | TEXT | Paquete al que pertenece |
| `moduleName` | TEXT | Módulo al que pertenece |
| `member` | TEXT | Nombre del miembro (si es un miembro de tipo) |
| `signature` | TEXT | Firma del miembro |
| `deprecated` | BOOLEAN | Si está deprecado |

## Mapeo Hibernate Search

- `DocChunk` tiene `@Indexed` — se indexa automáticamente con Hibernate Search
- El campo `embedding` usa `@VectorField(dimension = 384, vectorSimilarity = COSINE)`
- Los campos textuales usan `@FullTextField` y `@KeywordField` para filtrado
- La sincronización es síncrona: `hibernate.search.default.indexing.plan.synchronization = sync`

## Directorio del índice

El índice Lucene se almacena en disco, en el directorio configurado por `SEARCH_INDEX_DIR` (por defecto `./jaidoc-index`).

## Configuración

Se configura en `configurations/search-configuration.yml`:

```yaml
spring:
  jpa:
    properties:
      hibernate.search.backend.lucene.directory-type: local-filesystem
      hibernate.search.backend.lucene.directory.root: ${SEARCH_INDEX_DIR:./jaidoc-index}
      hibernate.search.default.indexing.plan.synchronization: sync
```

## Converter — FloatArrayConverter

Convierte `float[]` ↔ `byte[]` BLOB. SQLite no tiene un tipo vector nativo, así que el embedding se serializa como un array de bytes usando `ByteBuffer`.

## EmbeddingService

Envuelve el `EmbeddingModel` de Spring AI transformer con los prefijos e5:

- `"passage: "` para embeber texto (ingesta)
- `"query: "` para embeber consultas (búsqueda)

Sin estos prefijos, el ranking se degrada silenciosamente.

## Ingesta

La ingesta es un paso explícito:

1. Endpoint REST: `POST /api/ingest?version=25.0.3`
2. Tool MCP: `ingestVersion("25.0.3")`

El proceso:
- Verifica que existe `data/<version>/index.json`
- Borra cualquier ingesta previa de esa versión (idempotente)
- Parsea el manifiesto → `JdkVersion`
- Carga JSON estructural → `DocElement`
- Lee `chunks.jsonl`, embebe y persiste → `DocChunk`

## Búsqueda

La búsqueda se restringe a una versión a la vez:

- Endpoint REST: `GET /api/search?version=25.0.3&q=consulta&topK=10`
- Tool MCP: `searchJavadoc("25.0.3", "consulta", 10)`

El kNN se filtra por `version` para garantizar aislamiento entre versiones.

## Notas sobre `ddl-auto`

- `create-only` (por defecto): las tablas se crean en el arranque pero no se actualizan después. Necesita borrar `jaidoc.sqlite` entre iteraciones de desarrollo.
- `update`: Hibernate Search/ORM actualiza las tablas automáticamente durante el desarrollo.

## Dependencias

- Spring Data JPA
- Hibernate Search 8.4 (mapper-orm + backend-lucene)
- SQLite JDBC
- Hibernate community dialects
