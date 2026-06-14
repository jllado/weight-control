package com.jllado.weightcontrol.service;

import com.jllado.weightcontrol.config.AppProperties;
import com.jllado.weightcontrol.domain.Weight;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PhotoStorageService {

    private final Path root;

    public PhotoStorageService(AppProperties properties) throws IOException {
        this.root = properties.storage().root().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public String storeWeightPhoto(Weight weight, String side, MultipartFile file) throws IOException {
        Path path = buildWeightPhotoPath(weight, side, file.getOriginalFilename());
        Files.createDirectories(path.getParent());
        Files.copy(file.getInputStream(), path, StandardCopyOption.REPLACE_EXISTING);
        return root.relativize(path).toString().replace('\\', '/');
    }

    public String importWeightPhoto(Weight weight, String side, Path sourceFile) throws IOException {
        Path path = buildWeightPhotoPath(weight, side, sourceFile.getFileName().toString());
        Files.createDirectories(path.getParent());
        Files.copy(sourceFile, path, StandardCopyOption.REPLACE_EXISTING);
        return root.relativize(path).toString().replace('\\', '/');
    }

    public Resource load(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!Files.exists(path)) {
            throw new NotFoundException("Photo not found");
        }
        return new FileSystemResource(path);
    }

    public void delete(String relativePath) throws IOException {
        if (relativePath == null) {
            return;
        }
        Files.deleteIfExists(root.resolve(relativePath).normalize());
    }

    private Path buildWeightPhotoPath(Weight weight, String side, String originalFilename) {
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String normalizedExtension = extension == null ? "bin" : extension.toLowerCase(Locale.ROOT);
        return root
            .resolve("photos")
            .resolve(Long.toString(weight.getUser().getId()))
            .resolve("weights")
            .resolve(Long.toString(weight.getId()))
            .resolve(side + "." + normalizedExtension);
    }
}
