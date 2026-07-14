package com.finance.advisor.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试：使用 Mockito mock UserRepository，真实 BCryptPasswordEncoder。
 */
class UserServiceTest {

    private UserRepository userRepository;
    private BCryptPasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void register_success_encryptsPasswordAndDefaultsRiskLevelR3() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        User user = userService.register("alice", "pw123");

        assertEquals("alice", user.getUsername());
        assertEquals("R3", user.getRiskLevel());
        assertNotNull(user.getCreatedAt());
        assertTrue(user.getCreatedAt() > 0);
        assertNotNull(user.getPasswordHash());
        assertNotEquals("pw123", user.getPasswordHash());
        assertTrue(passwordEncoder.matches("pw123", user.getPasswordHash()));
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicateUsername_throwsConflict() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.register("alice", "pw"));
        assertEquals(HttpStatus.CONFLICT.value(), ex.getStatusCode().value());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_correctCredentials_returnsUser() {
        User stored = new User();
        stored.setId(1L);
        stored.setUsername("alice");
        stored.setPasswordHash(passwordEncoder.encode("pw123"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(stored));

        User logged = userService.login("alice", "pw123");
        assertEquals(Long.valueOf(1L), logged.getId());
        assertEquals("alice", logged.getUsername());
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        User stored = new User();
        stored.setUsername("alice");
        stored.setPasswordHash(passwordEncoder.encode("pw123"));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(stored));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.login("alice", "wrong"));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), ex.getStatusCode().value());
    }

    @Test
    void login_unknownUser_throwsUnauthorized() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.login("ghost", "pw"));
        assertEquals(HttpStatus.UNAUTHORIZED.value(), ex.getStatusCode().value());
    }

    @Test
    void findById_exists_returnsUser() {
        User stored = new User();
        stored.setId(1L);
        stored.setUsername("alice");
        when(userRepository.findById(1L)).thenReturn(Optional.of(stored));

        User found = userService.findById(1L);
        assertEquals(Long.valueOf(1L), found.getId());
        assertEquals("alice", found.getUsername());
    }

    @Test
    void findById_notExists_throwsNotFound() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.findById(2L));
        assertEquals(HttpStatus.NOT_FOUND.value(), ex.getStatusCode().value());
    }

    @Test
    void updateRiskLevel_success_callsRepository() {
        User stored = new User();
        stored.setId(1L);
        stored.setUsername("alice");
        stored.setRiskLevel("R3");
        when(userRepository.findById(1L)).thenReturn(Optional.of(stored));

        userService.updateRiskLevel(1L, "R4");

        verify(userRepository).updateRiskLevel(1L, "R4");
    }
}
