package io.github.foundry4j.quarkforge.excel;

import io.github.foundry4j.quarkforge.web.file.FileStorage;
import io.github.foundry4j.quarkforge.web.file.StoredFile;
import org.apache.fesod.sheet.FesodSheet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/// 测试用：在内存中生成 xlsx 字节、模拟文件存储。
final class ExcelTestSupport {

    private ExcelTestSupport() {
    }

    static byte[] xlsx(Object[][] rows) {
        List<List<Object>> data = new ArrayList<>(rows.length);
        for (Object[] row : rows) {
            data.add(row == null ? List.of() : Arrays.asList(row));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FesodSheet.write(out)
                .head(new ArrayList<>())
                .sheet("Sheet1")
                .doWrite(data);
        return out.toByteArray();
    }

    /// 内存版 [FileStorage]，用于脱离 Quarkus 配置验证导出/错误明细落盘。
    static final class MemoryFileStorage implements FileStorage {

        private final Map<String, byte[]> files = new ConcurrentHashMap<>();
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public StoredFile store(InputStream in, String originalFilename, String contentType) {
            try {
                byte[] bytes = in.readAllBytes();
                String key = "file-" + sequence.incrementAndGet();
                files.put(key, bytes);
                return new StoredFile(key, originalFilename, contentType, bytes.length);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public InputStream open(String key) {
            byte[] bytes = files.get(key);
            return bytes == null ? null : new ByteArrayInputStream(bytes);
        }

        @Override
        public boolean exists(String key) {
            return files.containsKey(key);
        }

        @Override
        public void delete(String key) {
            files.remove(key);
        }

        byte[] bytes(String key) {
            return files.get(key);
        }
    }
}

