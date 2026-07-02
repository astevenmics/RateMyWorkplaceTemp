package com.ratemyworkplace.web;

import com.ratemyworkplace.domain.User;
import com.ratemyworkplace.dto.DtoMapper;
import com.ratemyworkplace.dto.Requests;
import com.ratemyworkplace.dto.Responses;
import com.ratemyworkplace.service.CurrentUserService;
import com.ratemyworkplace.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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
}