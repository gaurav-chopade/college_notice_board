package com.college.noticeboard.config;

import com.college.noticeboard.entity.User;
import com.college.noticeboard.exception.UnauthorizedException;
import com.college.noticeboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new UnauthorizedException("Authentication required");
        }
        return principal.getId();
    }

    /** Loads the current user fresh from the database so role/department/semester are authoritative. */
    public User currentUser() {
        return userRepository.findById(currentUserId())
                .orElseThrow(() -> new UnauthorizedException("Authentication required"));
    }
}
