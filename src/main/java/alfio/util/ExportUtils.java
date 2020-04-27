/**
 * This file is part of alf.io.
 *
 * alf.io is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alf.io is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with alf.io.  If not, see <http://www.gnu.org/licenses/>.
 */
package alfio.util;

import ch.digitalfondue.basicxlsx.Cell;
import ch.digitalfondue.basicxlsx.StreamingWorkbook;
import com.opencsv.CSVWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ExportUtils {

    private static final int[] BOM_MARKERS = new int[] {0xEF, 0xBB, 0xBF};

    public static void exportExcel(String fileName, String sheetName, String[] header, Stream<String[]> data, HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);


        try (ServletOutputStream out = response.getOutputStream(); StreamingWorkbook workbook = new StreamingWorkbook(out)) {
            var boldFont = workbook.defineStyle().font().bold(true).build();

            var headerRow = StreamingWorkbook.row(Arrays.stream(header)
                .map(v -> Cell.cell(v).withStyle(boldFont))
                .collect(Collectors.toList()));

            var dataStream = data
                .map(rowData -> Arrays.stream(rowData).map(Cell::cell).collect(Collectors.toList()))
                .map(StreamingWorkbook::row);

            workbook.withSheet(sheetName, Stream.concat(Stream.of(headerRow), dataStream));
        }
    }

    public static void exportCsv(String fileName, String[] header, Stream<String[]> data, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

        try (ServletOutputStream out = response.getOutputStream(); CSVWriter writer = new CSVWriter(new OutputStreamWriter(out, UTF_8))) {
            for (int marker : ExportUtils.BOM_MARKERS) {
                out.write(marker);
            }
            writer.writeNext(header);
            data.forEachOrdered(writer::writeNext);
            writer.flush();
            out.flush();
        }
    }
}
