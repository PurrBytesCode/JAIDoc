package com.purrbyte.ai.util;

import com.purrbyte.ai.model.dto.Progress;
import com.purrbyte.ai.test.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class SpringBootArtifactDownloaderTest extends UnitTest {

    @TempDir
    Path tempDir;

    private RestClient restClient;
    private SpringBootArtifactDownloader downloader;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = new JsonMapper();
        restClient = mock(RestClient.class);
        downloader = new SpringBootArtifactDownloader(tempDir.toString(), restClient, jsonMapper);
    }

    private void stubMetadataResponse(String xml) {
        var spec = mock(RestClient.RequestHeadersUriSpec.class);
        var retrieve = mock(RestClient.ResponseSpec.class);
        lenient().when(restClient.get()).thenReturn(spec);
        lenient().when(spec.uri(any(URI.class))).thenReturn(spec);
        lenient().when(retrieve.body(String.class)).thenReturn(xml);
        lenient().when(spec.retrieve()).thenReturn(retrieve);
    }

    private void stubSolrResponse(String json) {
        var spec = mock(RestClient.RequestHeadersUriSpec.class);
        var retrieve = mock(RestClient.ResponseSpec.class);
        lenient().when(restClient.get()).thenReturn(spec);
        lenient().when(spec.uri(any(URI.class))).thenReturn(spec);
        lenient().when(retrieve.body(String.class)).thenReturn(json);
        lenient().when(spec.retrieve()).thenReturn(retrieve);
    }

    @SuppressWarnings("resource")
    private void stubExchangeSuccess(long contentLength, byte[] body) {
        var clientResponse = mock(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse.class);
        HttpHeaders headers = mock(HttpHeaders.class);
        lenient().when(clientResponse.getHeaders()).thenReturn(headers);
        lenient().when(headers.getContentLength()).thenReturn(contentLength);
        try {
            lenient().when(clientResponse.getBody()).thenReturn(new ByteArrayInputStream(body));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        var exchangeFunction = mock(RestClient.RequestHeadersSpec.ExchangeFunction.class);
        try {
            lenient().when(exchangeFunction.exchange(any(), any()))
                    .thenReturn(contentLength);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        var spec = mock(RestClient.RequestHeadersUriSpec.class);
        lenient().when(restClient.get()).thenReturn(spec);
        lenient().when(spec.uri(any(URI.class))).thenAnswer(_ -> {
            var exchangeSpec = mock(RestClient.RequestHeadersSpec.class);
            lenient().when(exchangeSpec.exchange(any())).thenReturn(contentLength);
            return exchangeSpec;
        });
    }

    @Nested
    class ResolveLatestVersionTest {

        @Test
        void resolvesFromMetadataXml() {
            // Metadata XML contains <latest> tag — should be extracted directly
            String metadata = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <metadata>
                      <versioning>
                        <latest>3.4.2</latest>
                        <release>3.4.2</release>
                      </versioning>
                    </metadata>""";
            stubMetadataResponse(metadata);
            Optional<String> result = downloader.resolveLatestVersion();
            assertThat(result).isPresent().hasValue("3.4.2");
        }

        @Test
        void resolvesLatestOverRelease() {
            // When <latest> and <release> differ, <latest> wins
            String metadata = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <metadata>
                      <versioning>
                        <latest>3.5.0-SNAPSHOT</latest>
                        <release>3.4.2</release>
                      </versioning>
                    </metadata>""";
            stubMetadataResponse(metadata);
            Optional<String> result = downloader.resolveLatestVersion();
            assertThat(result).hasValue("3.5.0-SNAPSHOT");
        }

        @Test
        void fallsBackToSolrApiWhenMetadataBlank() {
            // Blank metadata triggers Solr fallback
            stubMetadataResponse("");
            stubSolrResponse("""
                    {"response":{"docs":[{"v":"3.4.2"}]}}""");
            Optional<String> result = downloader.resolveLatestVersion();
            assertThat(result).hasValue("3.4.2");
        }

        @Test
        void fallsBackToSolrApiWhenNoLatestTag() {
            // Metadata without <latest> tag triggers Solr fallback
            String metadata = """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <metadata>
                      <versioning>
                        <release>3.4.2</release>
                      </versioning>
                    </metadata>""";
            stubMetadataResponse(metadata);
            stubSolrResponse("""
                    {"response":{"docs":[{"v":"3.4.2"}]}}""");
            Optional<String> result = downloader.resolveLatestVersion();
            assertThat(result).hasValue("3.4.2");
        }

        @Test
        void returnsEmptyWhenAllSourcesFail() {
            // Both metadata and Solr return nothing — should return empty
            stubMetadataResponse("");
            stubSolrResponse(
                    """
                            {"response":{"docs":[]}}"""
            );
            Optional<String> result = downloader.resolveLatestVersion();
            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyWhenSolrResponseIsNull() {
            // Null body from Solr — should return empty
            stubMetadataResponse("");
            var spec = mock(RestClient.RequestHeadersUriSpec.class);
            var retrieve = mock(RestClient.ResponseSpec.class);
            lenient().when(restClient.get()).thenReturn(spec);
            lenient().when(spec.uri(any(URI.class))).thenReturn(spec);
            lenient().when(retrieve.body(String.class)).thenReturn(null);
            lenient().when(spec.retrieve()).thenReturn(retrieve);
            Optional<String> result = downloader.resolveLatestVersion();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class DownloadArtifactTest {

        @Test
        void downloadsJarToTargetDirectory() throws Exception {
            // Downloads a jar and writes it to the target directory
            byte[] jarContent = "fake-jar-content".getBytes(StandardCharsets.UTF_8);
            stubExchangeSuccess(jarContent.length, jarContent);
            CompletableFuture<Path> future = downloader.downloadArtifact("3.4.2", "jar", null);
            Path result = future.get();
            assertThat(result).isNotNull();
            assertThat(Files.exists(result)).isTrue();
            assertThat(Files.readString(result)).isEqualTo("fake-jar-content");
        }

        @Test
        void downloadsSourcesJarWithSuffix() throws Exception {
            // Source artifact should produce a -sources.jar file
            byte[] sourcesContent = "fake-sources-content".getBytes(StandardCharsets.UTF_8);
            stubExchangeSuccess(sourcesContent.length, sourcesContent);
            CompletableFuture<Path> future = downloader.downloadArtifact("3.4.2", "sources", null);
            Path result = future.get();
            assertThat(result.getFileName().toString()).isEqualTo("spring-boot-3.4.2-sources.jar");
        }

        @Test
        void reportsProgressDuringDownload() throws Exception {
            // Progress callback should be invoked with percentage updates during download
            byte[] content = "x".repeat(1000).getBytes(StandardCharsets.UTF_8);
            stubExchangeSuccess(content.length, content);
            List<Progress> received = new ArrayList<>();
            downloader.downloadArtifact("3.4.2", "jar", received::add).get();
            assertThat(received).isNotEmpty();
            Progress last = received.getLast();
            assertThat(last.getPercentage()).isCloseTo(100.0, org.assertj.core.data.Percentage.withPercentage(1));
            assertThat(last.getModule()).isEqualTo(Progress.MODULE_DOWNLOAD);
        }

        @Test
        void reports100ProgressWhenContentLengthIsZero() throws Exception {
            // Chunked transfer (no Content-Length) should report 100% at the end
            byte[] content = "chunked".getBytes(StandardCharsets.UTF_8);
            stubExchangeSuccess(0, content);
            List<Progress> received = new ArrayList<>();
            downloader.downloadArtifact("3.4.2", "jar", received::add).get();
            assertThat(received).isNotEmpty();
            Progress last = received.getLast();
            assertThat(last.getPercentage()).isCloseTo(100.0, org.assertj.core.data.Percentage.withPercentage(1));
        }

        @Test
        void usesCachedFileWhenAlreadyExists() throws Exception {
            // If the target file already exists, skip download entirely
            Path existing = tempDir.resolve("spring-boot-3.4.2.jar");
            Files.writeString(existing, "cached-content");
            CompletableFuture<Path> future = downloader.downloadArtifact("3.4.2", "jar", null);
            Path result = future.get();
            assertThat(result).isEqualTo(existing);
            assertThat(Files.readString(result)).isEqualTo("cached-content");
            verify(restClient, never()).get();
        }

        @Test
        void failsForNullVersion() {
            // Null version should fail in the future with IllegalArgumentException
            CompletableFuture<Path> future = downloader.downloadArtifact(null, "jar", null);
            assertThatThrownBy(future::get).hasCauseInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void failsForBlankVersion() {
            // Blank version should fail in the future with IllegalArgumentException
            CompletableFuture<Path> future = downloader.downloadArtifact("  ", "jar", null);
            assertThatThrownBy(future::get).hasCauseInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void failsForUnknownArtifactType() {
            // Unknown artifact type should fail in the future with IllegalArgumentException
            CompletableFuture<Path> future = downloader.downloadArtifact("3.4.2", "debug", null);

            assertThatThrownBy(future::get).hasCauseInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void handlesDownloadFailureGracefully() {
            // Network failure should clean up partial file and fail in the future
            var spec = mock(RestClient.RequestHeadersUriSpec.class);
            lenient().when(restClient.get()).thenReturn(spec);
            lenient().when(spec.uri(any(URI.class))).thenAnswer(_ -> {
                var exchangeSpec = mock(RestClient.RequestHeadersSpec.class);
                lenient().when(exchangeSpec.exchange(any()))
                        .thenThrow(new RuntimeException("Connection refused"));
                return exchangeSpec;
            });
            CompletableFuture<Path> future = downloader.downloadArtifact("3.4.2", "jar", null);
            assertThatThrownBy(future::get).hasCauseInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    class ConvenienceMethodTest {

        @Test
        void downloadMainJarDelegatesToDownloadArtifact() throws Exception {
            byte[] content = "main-jar".getBytes(StandardCharsets.UTF_8);
            stubExchangeSuccess(content.length, content);
            List<Progress> received = new ArrayList<>();
            CompletableFuture<Path> future = downloader.downloadMainJar("3.4.2", received::add);
            Path result = future.get();
            assertThat(result.getFileName().toString()).isEqualTo("spring-boot-3.4.2.jar");
            assertThat(received).isNotEmpty();
        }

        @Test
        void downloadSourcesJarDelegatesToDownloadArtifact() throws Exception {
            byte[] content = "sources-jar".getBytes(StandardCharsets.UTF_8);
            stubExchangeSuccess(content.length, content);
            List<Progress> received = new ArrayList<>();
            CompletableFuture<Path> future = downloader.downloadSourcesJar("3.4.2", received::add);
            Path result = future.get();
            assertThat(result.getFileName().toString()).isEqualTo("spring-boot-3.4.2-sources.jar");
            assertThat(received).isNotEmpty();
        }
    }
}
