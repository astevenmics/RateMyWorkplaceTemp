package com.ratemyworkplace.security;

import com.ratemyworkplace.domain.ModeratorPermission;
import com.ratemyworkplace.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Lightweight, serializable adapter from our {@link User} entity to Spring Security.
 *
 * <p>It snapshots only the scalar fields and authorities it needs at load time (while
 * the loading transaction's session is open) rather than holding the JPA entity. This
 * keeps the principal stored in the HTTP session small, avoids dragging a detached
 * Hibernate entity (and its LAZY collections) into the session, and lets the session
 * serialize cleanly.
 */
public class AppUserDetails implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public AppUserDetails(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.enabled = user.isEnabled();
        this.authorities = new ArrayList<>();
        this.authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        for (ModeratorPermission permission : user.getModeratorPermissions()) {
            this.authorities.add(new SimpleGrantedAuthority(permission.authority()));
        }
    }

    public Long getId() {
        return id;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
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
        return enabled;
    }
}
