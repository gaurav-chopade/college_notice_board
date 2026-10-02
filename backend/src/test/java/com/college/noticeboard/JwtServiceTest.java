package com.college.noticeboard;

import com.college.noticeboard.entity.Role;
import com.college.noticeboard.entity.User;
import com.college.noticeboard.config.JwtService;
import com.college.noticeboard.config.UserPrincipal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-key-that-is-definitely-longer-than-sixty-four-characters-123";

    private final UserPrincipal principal = new UserPrincipal(User.builder().id(5L).email("a@college.edu")
            .password("x").role(Role.STUDENT).active(true).build());

    @Test
    void generatedTokenValidates() {
        JwtService jwt = new JwtService(SECRET, 60_000);
        assertThat(jwt.validateAndGetSubject(jwt.generateToken(principal))).isEqualTo("a@college.edu");
    }

    @Test
    void tamperedOrForeignTokenIsRejected() {
        JwtService jwt = new JwtService(SECRET, 60_000);
        JwtService other = new JwtService(SECRET.replace('u', 'v'), 60_000);
        String token = other.generateToken(principal);
        assertThat(jwt.validateAndGetSubject(token)).isNull();
        assertThat(jwt.validateAndGetSubject(token + "x")).isNull();
        assertThat(jwt.validateAndGetSubject("garbage")).isNull();
    }

    @Test
    void expiredTokenIsRejected() throws InterruptedException {
        JwtService jwt = new JwtService(SECRET, 1);
        String token = jwt.generateToken(principal);
        Thread.sleep(1100);
        assertThat(jwt.validateAndGetSubject(token)).isNull();
    }

    @Test
    void shortSecretFailsFast() {
        assertThatThrownBy(() -> new JwtService("short", 1000)).isInstanceOf(IllegalStateException.class);
    }
}
