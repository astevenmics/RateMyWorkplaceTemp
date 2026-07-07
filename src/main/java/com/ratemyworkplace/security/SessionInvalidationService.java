package com.ratemyworkplace.security;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

/**
 * Force-expires a user's active sessions. {@link AppUserDetails} snapshots authorities
 * and the enabled flag once, at login, and Spring Security never re-checks them against
 * the database for the lifetime of an established session — so granting/revoking
 * moderator permissions or disabling an account has no effect on a session that's
 * already logged in until it's forced to re-authenticate.
 */
@Service
public class SessionInvalidationService {

    private final SessionRegistry sessionRegistry;

    public SessionInvalidationService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void invalidateSessionsFor(String username) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof AppUserDetails details && details.getUsername().equalsIgnoreCase(username)) {
                for (SessionInformation info : sessionRegistry.getAllSessions(principal, false)) {
                    info.expireNow();
                }
            }
        }
    }
}
