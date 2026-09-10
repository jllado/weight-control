package com.jllado.weightcontrol.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jllado.weightcontrol.config.AppProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ExerciseImageStorageTest {
    @TempDir Path root;

    private ExerciseImageStorage storage() { return new ExerciseImageStorage(new AppProperties(null, null, new AppProperties.Storage(root), null, null, null)); }

    @Test
    void normalizesAndResizesUploadsAndCleansUpOnlyAfterCommitOrRollback() throws Exception {
        var storage = storage();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB), "PNG", bytes);
        TransactionSynchronizationManager.initSynchronization();
        String path;
        try {
            path = storage.store(3L, new MockMultipartFile("file", "../../unsafe.png", "image/png", bytes.toByteArray()));
            assertTrue(path.startsWith("exercise-images/3/"));
            var image = ImageIO.read(storage.load(path).getInputStream());
            assertEquals(1600, image.getWidth()); assertEquals(800, image.getHeight());
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        assertTrue(Files.exists(root.resolve(path)));
        TransactionSynchronizationManager.initSynchronization();
        try {
            storage.deleteAfterCommit(path);
            assertTrue(Files.exists(root.resolve(path)));
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            assertFalse(Files.exists(root.resolve(path)));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        TransactionSynchronizationManager.initSynchronization();
        try {
            String rollbackPath = storage.store(3L, new MockMultipartFile("file", bytes.toByteArray()));
            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            assertFalse(Files.exists(root.resolve(rollbackPath)));
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }

    @Test
    void rejectsEmptyOversizedAndNonImageFiles() throws Exception {
        var storage = storage();
        assertThrows(BadRequestException.class, () -> storage.store(1L, new MockMultipartFile("file", new byte[0])));
        assertThrows(BadRequestException.class, () -> storage.store(1L, new MockMultipartFile("file", new byte[10 * 1024 * 1024 + 1])));
        assertThrows(BadRequestException.class, () -> storage.store(1L, new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())));
        var gif = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "GIF", gif);
        assertThrows(BadRequestException.class, () -> storage.store(1L, new MockMultipartFile("file", gif.toByteArray())));
    }

    @Test
    void readsExifOrientationAndRemovesMetadataFromSavedJpeg() throws Exception {
        var jpeg = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 30, BufferedImage.TYPE_INT_RGB), "JPEG", jpeg);
        var encoded = jpeg.toByteArray();
        var withExif = new ByteArrayOutputStream();
        withExif.write(encoded, 0, 2);
        withExif.write(java.util.HexFormat.of().parseHex("ffe100224578696600004d4d002a00000008000101120003000000010006000000000000"));
        withExif.write(encoded, 2, encoded.length - 2);
        TransactionSynchronizationManager.initSynchronization();
        try {
            var storage = storage();
            String path = storage.store(1L, new MockMultipartFile("file", withExif.toByteArray()));
            try (var input = storage.load(path).getInputStream()) {
                var image = ImageIO.read(input); assertEquals(30, image.getWidth()); assertEquals(20, image.getHeight());
            }
            try (var input = storage.load(path).getInputStream()) {
                assertNull(com.drew.imaging.ImageMetadataReader.readMetadata(input).getFirstDirectoryOfType(com.drew.metadata.exif.ExifIFD0Directory.class));
            }
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
        assertThrows(BadRequestException.class, () -> storage().store(1L, new MockMultipartFile("file", java.util.Arrays.copyOf(encoded, 30))));
    }

    @Test
    void appliesAllExifOrientations() {
        var source = new BufferedImage(2, 3, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, 0xff0000);
        int[][] redCorner = {{0,0},{1,0},{1,2},{0,2},{0,0},{2,0},{2,1},{0,1}};
        for (int orientation = 1; orientation <= 8; orientation++) {
            var image = ExerciseImageStorage.orient(source, orientation);
            assertEquals(orientation >= 5 ? 3 : 2, image.getWidth());
            assertEquals(orientation >= 5 ? 2 : 3, image.getHeight());
            assertEquals(0xffff0000, image.getRGB(redCorner[orientation - 1][0], redCorner[orientation - 1][1]));
        }
    }
}
