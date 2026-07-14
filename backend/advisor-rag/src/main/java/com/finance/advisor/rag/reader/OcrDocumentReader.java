package com.finance.advisor.rag.reader;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.core.io.Resource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;

/**
 * OCR 图片文字识别读取器
 *
 * 使用 Tess4J 进行 OCR 识别，如果 Tess4J 不可用则返回提示信息。
 */
public class OcrDocumentReader implements DocumentReader {

    private final Resource resource;

    public OcrDocumentReader(Resource resource) {
        this.resource = resource;
    }

    @Override
    public List<Document> get() {
        try (InputStream is = resource.getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                return List.of(new Document("无法解析图片: " + resource.getFilename()));
            }

            String text;
            try {
                // 尝试使用 Tess4J 进行 OCR
                text = performOcr(image);
            } catch (NoClassDefFoundError e) {
                text = "[OCR 引擎未安装] 图片 " + resource.getFilename()
                    + " 包含 " + image.getWidth() + "x" + image.getHeight()
                    + " 像素，" + "需要使用 OCR 引擎提取文字。\n"
                    + "请安装 Tesseract OCR 引擎并配置 tessdata 路径。";
            }

            Document doc = new Document(text);
            doc.getMetadata().put("source", resource.getFilename());
            doc.getMetadata().put("format", "image");
            doc.getMetadata().put("width", image.getWidth());
            doc.getMetadata().put("height", image.getHeight());

            return List.of(doc);
        } catch (Exception e) {
            return List.of(new Document("读取图片失败: " + e.getMessage()));
        }
    }

    private String performOcr(BufferedImage image) {
        try {
            Class<?> tesseractClass = Class.forName("net.sourceforge.tess4j.Tesseract");
            Object tesseract = tesseractClass.getDeclaredConstructor().newInstance();
            tesseractClass.getMethod("setDatapath", String.class)
                    .invoke(tesseract, System.getenv().getOrDefault("TESSDATA_PREFIX", "./tessdata"));
            tesseractClass.getMethod("setLanguage", String.class).invoke(tesseract, "chi_sim+eng");
            Object result = tesseractClass.getMethod("doOCR", BufferedImage.class).invoke(tesseract, image);
            return (String) result;
        } catch (Exception e) {
            return "[OCR 识别失败: " + e.getMessage() + "]";
        }
    }
}
