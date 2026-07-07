package com.ratemyworkplace.web;

import com.ratemyworkplace.domain.User;
import com.ratemyworkplace.repository.UserRepository;
import com.ratemyworkplace.service.FileStorageService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Public, read-only access to user avatars. Profile pictures are shown in the
 * site header and on profile pages, so they must be fetchable without special
 * authorization. The stored file name is never exposed — callers reference the
 * user by id and the URL rotates (via a version query param) when the image
 * changes so stale copies aren't served from cache.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public UserController(UserRepository userRepository, FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{id}/avatar")
    public ResponseEntity<Resource> avatar(@PathVariable Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getAvatarFileName() == null) {
            throw ApiException.notFound("This user has no profile picture");
        }
        Resource resource = new FileSystemResource(fileStorageService.resolve(user.getAvatarFileName()));
        MediaType contentType = user.getAvatarContentType() != null
                ? MediaType.parseMediaType(user.getAvatarContentType())
                : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(contentType)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(resource);
    }
}