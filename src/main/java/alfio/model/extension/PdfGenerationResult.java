package alfio.model.extension;

import lombok.Data;

@Data
public class PdfGenerationResult {
    private final String tempFilePath;

    public boolean isEmpty() {
        return tempFilePath == null || tempFilePath.isEmpty();
    }
}
