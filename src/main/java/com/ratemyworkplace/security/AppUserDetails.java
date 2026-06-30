package com.ratemyworkplace.security;

import com.ratemyworkplace.domain.ModeratorPermission;
import com.ratemyworkplace.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Adapts our {@link User} entity to Spring Security. */
public class AppUserDetails implements UserDetails {

    private final User user;
    /**
     * Authorities are snapshotted at construction (while the loading transaction's
     * session is still open) so authorization checks on later requests never touch
     * the now-LAZY, detached {@code moderatorPermissions} collection.
     */
    private final List<GrantedAuthority> authorities;

    public AppUserDetails(User user) {
        this.user = user;
        this.authorities = new ArrayList<>();
        this.authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        for (ModeratorPermission permission : user.getModeratorPermissions()) {
            this.authorities.add(new SimpleGrantedAuthority(permission.authority()));
        }
    }

    public User getUser() {
        return user;
    }

    public Long getId() {
        return user.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }
}
