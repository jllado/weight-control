package com.jllado.weightcontrol.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.jllado.weightcontrol.config.AppProperties;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExerciseImageStorage {
    private static final Logger log = LoggerFactory.getLogger(ExerciseImageStorage.class);
    private final Path root;

    public ExerciseImageStorage(AppProperties properties) {
        root = properties.storage().root().toAbsolutePath().normalize();
    }

    public String store(Long exerciseId, MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) throw new BadRequestException("Choose a JPEG or PNG picture up to 10 MB");
        try {
            byte[] bytes = file.getBytes();
            BufferedImage oriented = decode(bytes);
            double scale = Math.min(1, 1600.0 / Math.max(oriented.getWidth(), oriented.getHeight()));
            BufferedImage result = new BufferedImage(Math.max(1, (int) Math.round(oriented.getWidth() * scale)), Math.max(1, (int) Math.round(oriented.getHeight() * scale)), BufferedImage.TYPE_INT_RGB);
            var graphics = result.createGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(oriented, 0, 0, result.getWidth(), result.getHeight(), null);
            graphics.dispose();
            String relativePath = "exercise-images/" + exerciseId + "/" + UUID.randomUUID() + ".jpg";
            Path path = root.resolve(relativePath);
            Files.createDirectories(path.getParent());
            try {
                ImageIO.write(result, "JPEG", path.toFile());
            } catch (IOException e) {
                Files.deleteIfExists(path);
                throw e;
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) deleteFile(relativePath);
                }
            });
            return relativePath;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save exercise picture", e);
        }
    }

    private BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage source;
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new BadRequestException("Choose a valid JPEG or PNG picture");
                var reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) throw new BadRequestException("Choose a JPEG or PNG picture");
                    reader.setInput(input);
                    if ((long) reader.getWidth(0) * reader.getHeight(0) > 40_000_000) throw new BadRequestException("Picture must be no larger than 40 megapixels");
                    var parameters = reader.getDefaultReadParam();
                    int sample = Math.max(1, Math.max(reader.getWidth(0), reader.getHeight(0)) / 1600);
                    parameters.setSourceSubsampling(sample, sample, 0, 0);
                    source = reader.read(0, parameters);
                } finally {
                    reader.dispose();
                }
            }
            var directory = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes)).getFirstDirectoryOfType(ExifIFD0Directory.class);
            Integer orientation = directory == null ? null : directory.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
            return orient(source, orientation == null ? 1 : orientation);
        } catch (IOException | ImageProcessingException e) {
            throw new BadRequestException("Choose a valid JPEG or PNG picture");
        }
    }

    static BufferedImage orient(BufferedImage source, int orientation) {
        int width = source.getWidth(), height = source.getHeight();
        boolean swap = orientation >= 5 && orientation <= 8;
        BufferedImage result = new BufferedImage(swap ? height : width, swap ? width : height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int targetX = switch (orientation) { case 2, 3 -> width - 1 - x; case 5, 8 -> y; case 6, 7 -> height - 1 - y; default -> x; };
            int targetY = switch (orientation) { case 3, 4 -> height - 1 - y; case 5, 6 -> x; case 7, 8 -> width - 1 - x; default -> y; };
            result.setRGB(targetX, targetY, source.getRGB(x, y));
        }
        return result;
    }

    public Resource load(String path) {
        Resource resource = new FileSystemResource(root.resolve(path));
        if (!resource.exists()) throw new NotFoundException("Exercise picture not found");
        return resource;
    }

    public void deleteAfterCommit(String path) {
        if (path == null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { deleteFile(path); }
        });
    }

    private void deleteFile(String path) {
        try {
            Files.deleteIfExists(root.resolve(path));
        } catch (IOException e) {
            log.error("Could not clean up exercise picture {}", path, e);
        }
    }
}
