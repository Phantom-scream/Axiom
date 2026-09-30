package com.axiom.application.analysis;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class LogArchiveSafetyTest {
    @Test
    void normalDirectoryEntriesRemainSupported() throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("job/"));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("job/step.txt"));
            zip.write("fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        var service = new PipelineLogAnalysisService(null, null, null, null);
        String decoded = org.springframework.test.util.ReflectionTestUtils.invokeMethod(service, "decode", output.toByteArray());
        org.assertj.core.api.Assertions.assertThat(decoded).isEqualTo("fixture\n");
    }
    @Test
    void boundsTotalExpansionAcrossEntriesAndRejectsUnsafePaths() throws Exception {
        var storage = mock(com.axiom.logstorage.LogStorage.class);
        var jdbc = mock(org.springframework.jdbc.core.JdbcTemplate.class);
        var service = new PipelineLogAnalysisService(storage, null, null, jdbc);
        UUID id = UUID.randomUUID();
        when(storage.load(id)).thenReturn(Optional.of(archive("safe.log", 13_000_000, 2)));
        assertThatThrownBy(() -> service.process(id)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expanded size limit");
        when(storage.load(id)).thenReturn(Optional.of(archive("../unsafe.log", 1, 1)));
        assertThatThrownBy(() -> service.process(id)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe entry");
        org.mockito.Mockito.verifyNoInteractions(jdbc);
    }

    private byte[] archive(String path, int size, int entries) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            byte[] block = new byte[1000];
            for (int entry = 0; entry < entries; entry++) {
                zip.putNextEntry(new ZipEntry(entry + "/" + path));
                for (int bytes = 0; bytes < size; bytes += block.length) zip.write(block, 0, Math.min(block.length, size - bytes));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
