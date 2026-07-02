package com.lacity.aipppc.security;

import com.lacity.aipppc.model.User;
import com.lacity.aipppc.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Loads a {@link User} by email and maps role → Spring authorities. ADMIN implies
 * STAFF so admins pass every {@code /api/staff/**} check without a second role.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        List<SimpleGrantedAuthority> authorities;
        if (user.getRole() == User.Role.ADMIN) {
            authorities = List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_STAFF"),
                new SimpleGrantedAuthority("ROLE_APPLICANT"));
        } else if (user.getRole() == User.Role.STAFF) {
            authorities = List.of(
                new SimpleGrantedAuthority("ROLE_STAFF"),
                new SimpleGrantedAuthority("ROLE_APPLICANT"));
        } else {
            authorities = List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"));
        }

        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getEmail())
            .password(user.getPasswordHash())
            .authorities(authorities)
            .disabled(!user.isEnabled())
            .build();
    }
}
