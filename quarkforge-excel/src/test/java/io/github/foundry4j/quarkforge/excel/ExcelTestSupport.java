package io.github.foundry4j.quarkforge.excel;

import org.apache.fesod.sheet.FesodSheet;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// 测试用：在内存中生成 xlsx 字节。
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
}
