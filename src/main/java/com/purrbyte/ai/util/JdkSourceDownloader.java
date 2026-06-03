package com.purrbyte.ai.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Descarga el código fuente del JDK desde el repositorio público de GitHub
 * (https://github.com/openjdk/jdk) según la versión específica solicitada.
 * <p>
 * Soporta versiones completas como 25.0.1, 25.0.3, etc., cada una con su
 * propio tag y archivo tar.gz en los releases de GitHub.
 *
 * @author JAIDoc
 */
public final class JdkSourceDownloader {

    private static final String GITHUB_RELEASES_BASE = "https://github.com/openjdk/jdk/releases/download/";
    private static final Pattern VERSION_PATTERN = Pattern.compile("^\\d+\\.\\d+(?:\\.\\d+)?$");

    /**
     * Descarga el código fuente del JDK para la versión especificada.
     *
     * @param version         la versión completa del JDK (ej. "25.0.1", "21.0.3")
     * @param targetDirectory el directorio donde se descargará el archivo tar.gz
     * @return la ruta al archivo descargado
     * @throws JdkSourceDownloadException si la versión no es válida o la descarga falla
     */
    public Path downloadJdkSource(String version, Path targetDirectory) {
        validateVersion(version);

        Path resolvedDir = Objects.requireNonNull(targetDirectory, "targetDirectory no puede ser null");

        try {
            if (!Files.exists(resolvedDir)) {
                Files.createDirectories(resolvedDir);
            }
        } catch (IOException e) {
            throw new JdkSourceDownloadException("No se pudo crear el directorio de destino: " + resolvedDir, e);
        }

        String tag = "jdk-" + version;
        String downloadUrl = GITHUB_RELEASES_BASE + tag;
        String fileName = "jdk-" + version + "-src.tar.gz";
        Path outputPath = resolvedDir.resolve(fileName);

        if (Files.exists(outputPath)) {
            throw new JdkSourceDownloadException(
                    "El archivo ya existe: " + outputPath +
                            ". Elimínelo o especifique otro directorio de destino.");
        }

        try {
            URL url = URI.create(downloadUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(600_000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new JdkSourceDownloadException(
                        "Versión no encontrada: " + version +
                                ". El tag 'jdk-" + version + "' no existe en https://github.com/openjdk/jdk/releases");
            }

            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new JdkSourceDownloadException(
                        "Error al verificar la descarga. Código HTTP: " + responseCode +
                                ". Versión: " + version);
            }

            long totalSize = connection.getContentLengthLong();

            Files.copy(connection.getInputStream(), outputPath);

            String sizeInfo = totalSize > 0 ? " (" + formatBytes(totalSize) + ")" : "";
            return outputPath;

        } catch (JdkSourceDownloadException e) {
            throw e;
        } catch (IOException e) {
            // Limpia archivo incompleto en caso de error
            if (Files.exists(outputPath)) {
                try {
                    Files.delete(outputPath);
                } catch (IOException cleanupEx) {
                    // ignore
                }
            }
            throw new JdkSourceDownloadException(
                    "Error al descargar JDK " + version + ": " + e.getMessage(), e);
        }
    }

    /**
     * Verifica si una versión específica del JDK está disponible en GitHub.
     *
     * @param version la versión a verificar (ej. "25.0.1")
     * @return {@code true} si el tag existe en los releases de openjdk/jdk
     */
    public boolean isVersionAvailable(String version) {
        Objects.requireNonNull(version, "version no puede ser null");

        try {
            validateVersion(version);
        } catch (JdkSourceDownloadException e) {
            return false;
        }

        String tag = "jdk-" + version;
        String url = GITHUB_RELEASES_BASE + tag;

        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL()
                    .openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.connect();

            int responseCode = connection.getResponseCode();
            return responseCode == HttpURLConnection.HTTP_OK;

        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Valida el formato de una versión del JDK.
     *
     * @param version la versión a validar (ej. "25.0.1", "21.0.3")
     * @throws JdkSourceDownloadException si el formato no es válido
     */
    public void validateVersionFormat(String version) {
        validateVersion(version);
    }

    private void validateVersion(String version) {
        if (version == null) {
            throw new JdkSourceDownloadException("La versión no puede ser null.");
        }

        String trimmed = version.trim();
        if (trimmed.isEmpty()) {
            throw new JdkSourceDownloadException("La versión no puede estar vacía.");
        }

        if (!VERSION_PATTERN.matcher(trimmed).matches()) {
            throw new JdkSourceDownloadException(
                    "Formato de versión inválido: '" + version + "'. " +
                            "Use formato numérico con puntos, ej: 25.0.1, 21.0.3");
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f MB", bytes / 1024.0);
        return String.format("%.2f GB", bytes / (1024.0 * 1024));
    }

    /**
     * Excepción específica para errores de descarga del JDK.
     */
    public static final class JdkSourceDownloadException extends RuntimeException {

        JdkSourceDownloadException(String message) {
            super(message);
        }

        JdkSourceDownloadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
