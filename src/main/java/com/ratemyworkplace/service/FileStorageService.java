package com.ratemyworkplace.service;

import com.ratemyworkplace.config.UploadProperties;
import com.ratemyworkplace.web.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Stores employment-proof uploads on the local filesystem under {@code app.upload.dir}. */
@Service
public class FileStorageService {

    /** Image types accepted for profile pictures (a subset of proof-friendly types). */
    private static final List<String> IMAGE_CONTENT_TYPES =
            List.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final UploadProperties properties;
    private Path root;

    public FileStorageService(UploadProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        try {
            root = Paths.get(properties.getDir()).toAbsolutePath().normalize();
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not initialise upload directory", e);
        }
    }

    /** Validates the upload and stores it under a random name. Returns the stored file name. */
    public String store(MultipartFile file) {
        return store(file, properties.getAllowedContentTypes(),
                "Please attach a proof document", "Only PDF, PNG or JPG files are accepted");
    }

    /** Validates an image upload (profile picture) and stores it under a random name. */
    public String storeImage(MultipartFile file) {
        return store(file, IMAGE_CONTENT_TYPES,
                "Please choose an image", "Only PNG, JPG, WEBP or GIF images are accepted");
    }

    private String store(MultipartFile file, Collection<String> allowedContentTypes,
                         String emptyMessage, String typeMessage) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest(emptyMessage);
        }
        String contentType = file.getContentType();
        if (contentType == null || !allowedContentTypes.contains(contentType)) {
            throw ApiException.badRequest(typeMessage);
        }
        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename());
        String extension = StringUtils.getFilenameExtension(original);
        String stored = UUID.randomUUID() + (extension != null ? "." + extension.toLowerCase() : "");
        try {
            Path target = root.resolve(stored).normalize();
            if (!target.startsWith(root)) {
                throw ApiException.badRequest("Invalid file path");
            }
            file.transferTo(target);
            return stored;
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store the uploaded file");
        }
    }

    public Path resolve(String storedFileName) {
        Path target = root.resolve(storedFileName).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw ApiException.notFound("File not found");
        }
        return target;
    }


    /** Best-effort delete of a stored file (e.g. a cancelled proof or a replaced avatar). */
    public void delete(String storedFileName) {
        if (storedFileName == null || storedFileName.isBlank()) {
            return;
        }
        try {
            Path target = root.resolve(storedFileName).normalize();
            if (target.startsWith(root)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException ignored) {
            // Don't fail the operation if the file is already gone.
        }
    }
}