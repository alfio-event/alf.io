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
import ch.digitalfondue.basicxlsx.Style;
import com.opencsv.CSVWriter;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ExportUtils {

    private static final int[] BOM_MARKERS = new int[] {0xEF, 0xBB, 0xBF};

    private ExportUtils() {}

    public static void exportExcel(String fileName, String sheetName, String[] header, Stream<String[]> data, HttpServletResponse response) throws IOException {
        exportExcel(fileName, response, workbook -> addSheetToWorkbook(sheetName, header, data, workbook, workbook.defineStyle().font().bold(true).build()));
    }

    public static void exportExcel(String fileName,
                                   HttpServletResponse response,
                                   Consumer<StreamingWorkbook> workbookConsumer) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

        try (ServletOutputStream out = response.getOutputStream(); StreamingWorkbook workbook = new StreamingWorkbook(out)) {
            workbookConsumer.accept(workbook);
        }
    }

    public static void addSheetToWorkbook(String sheetName,
                                          String[] header,
                                          Stream<String[]> data,
                                          StreamingWorkbook workbook,
                                          Style headerStyle) {
        try {
            var headerRow = StreamingWorkbook.row(Arrays.stream(header)
                .map(v -> Cell.cell(v).withStyle(headerStyle))
                .collect(Collectors.toList()));

            var dataStream = data
                .map(rowData -> Arrays.stream(rowData).map(Cell::cell).collect(Collectors.toList()))
                .map(StreamingWorkbook::row);

            workbook.withSheet(sheetName, Stream.concat(Stream.of(headerRow), dataStream));

        } catch (IOException e) {
            throw new IllegalStateException(e);
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
