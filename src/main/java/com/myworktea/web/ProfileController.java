package com.myworktea.web;

import com.myworktea.domain.User;
import com.myworktea.dto.DtoMapper;
import com.myworktea.dto.Requests;
import com.myworktea.dto.Responses;
import com.myworktea.service.CurrentUserService;
import com.myworktea.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final CurrentUserService currentUserService;
    private final UserService userService;

    public ProfileController(CurrentUserService currentUserService, UserService userService) {
        this.currentUserService = currentUserService;
        this.userService = userService;
    }

    @GetMapping
    public Responses.UserDto me() {
        return DtoMapper.user(currentUserService.require());
    }

    @PutMapping
    public Responses.UserDto update(@Valid @RequestBody Requests.UpdateProfileRequest request) {
        User user = currentUserService.require();
        return DtoMapper.user(userService.updateProfile(user, request));
    }

    /** Upload or replace the current user's profile picture (PNG/JPG/WEBP/GIF). */
    @PostMapping(value = "/avatar", consumes = {"multipart/form-data"})
    public Responses.UserDto uploadAvatar(@RequestParam("file") MultipartFile file) {
        User user = currentUserService.require();
        return DtoMapper.user(userService.updateAvatar(user, file));
    }

    /** Remove the current user's profile picture. */
    @DeleteMapping("/avatar")
    public Responses.UserDto removeAvatar() {
        User user = currentUserService.require();
        return DtoMapper.user(userService.removeAvatar(user));
    }

    /** Deactivates the caller's own account. Feedback and posts stay visible; they just can't sign in. */
    @PostMapping("/disable")
    public Responses.SimpleMessage disable(@Valid @RequestBody Requests.AccountActionRequest request) {
        User user = currentUserService.require();
        userService.disableAccount(user, request.password());
        return Responses.SimpleMessage.ok("Your account has been disabled.");
    }

    /** Schedules the caller's own account for permanent deletion after the grace period. */
    @PostMapping("/delete")
    public Responses.SimpleMessage delete(@Valid @RequestBody Requests.AccountActionRequest request) {
        User user = currentUserService.require();
        userService.requestAccountDeletion(user, request.password());
        return Responses.SimpleMessage.ok(
                "Your account has been disabled and will be permanently deleted in " + User.DELETION_GRACE_DAYS + " days.");
    }
}