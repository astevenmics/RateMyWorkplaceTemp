package com.ratemyworkplace.service;

import com.ratemyworkplace.config.UploadProperties;
import com.ratemyworkplace.web.ApiException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
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
        // The browser-supplied Content-Type is just a request header the caller controls;
        // trusting it alone would let someone upload an HTML/script file labelled
        // "image/png" and have it served back with that (spoofed) content type later.
        // Cross-checking the file's actual magic bytes closes that gap.
        String contentType = file.getContentType();
        if (contentType == null || !allowedContentTypes.contains(contentType)
                || !matchesSignature(peekHeader(file), contentType)) {
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

    private byte[] peekHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = new byte[12];
            int read = in.readNBytes(header, 0, header.length);
            return read == header.length ? header : Arrays.copyOf(header, read);
        } catch (IOException e) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read the uploaded file");
        }
    }

    /** Checks the file's magic bytes against the signature expected for the declared content type. */
    private boolean matchesSignature(byte[] h, String contentType) {
        return switch (contentType) {
            case "image/png" -> startsWith(h, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/jpeg" -> startsWith(h, 0xFF, 0xD8, 0xFF);
            case "image/gif" -> (startsWith(h, 'G', 'I', 'F', '8', '7', 'a') || startsWith(h, 'G', 'I', 'F', '8', '9', 'a'));
            case "image/webp" -> h.length >= 12 && startsWith(h, 'R', 'I', 'F', 'F')
                    && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
            case "application/pdf" -> startsWith(h, '%', 'P', 'D', 'F');
            default -> false;
        };
    }

    private boolean startsWith(byte[] header, int... expected) {
        if (header.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
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
