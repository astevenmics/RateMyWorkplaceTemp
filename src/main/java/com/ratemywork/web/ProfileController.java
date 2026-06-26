package com.ratemywork.web;

import com.ratemywork.domain.User;
import com.ratemywork.dto.DtoMapper;
import com.ratemywork.dto.Requests;
import com.ratemywork.dto.Responses;
import com.ratemywork.service.CurrentUserService;
import com.ratemywork.service.UserService;
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
