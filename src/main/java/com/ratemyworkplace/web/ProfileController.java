package com.ratemyworkplace.web;

import com.ratemyworkplace.domain.User;
import com.ratemyworkplace.dto.DtoMapper;
import com.ratemyworkplace.dto.Requests;
import com.ratemyworkplace.dto.Responses;
import com.ratemyworkplace.service.CurrentUserService;
import com.ratemyworkplace.service.UserService;
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
}
