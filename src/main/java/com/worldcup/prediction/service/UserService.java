package com.worldcup.prediction.service;

import com.worldcup.prediction.domain.User;
import com.worldcup.prediction.domain.enums.UserStatus;
import com.worldcup.prediction.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public User approveUser(Long userId) {
        User user = findById(userId);
        user.setStatus(UserStatus.ACTIVE);
        log.info("Approved user id={} email={}", userId, user.getEmail());
        return userRepository.save(user);
    }

    @Transactional
    public User rejectUser(Long userId) {
        User user = findById(userId);
        user.setStatus(UserStatus.DISABLED);
        log.info("Rejected user id={} email={}", userId, user.getEmail());
        return userRepository.save(user);
    }

    @Transactional
    public User enableUser(Long userId) {
        User user = findById(userId);
        user.setStatus(UserStatus.ACTIVE);
        log.info("Enabled user id={} email={}", userId, user.getEmail());
        return userRepository.save(user);
    }

    @Transactional
    public User disableUser(Long userId) {
        User user = findById(userId);
        user.setStatus(UserStatus.DISABLED);
        log.info("Disabled user id={} email={}", userId, user.getEmail());
        return userRepository.save(user);
    }

    @Transactional
    public User setStatus(Long userId, UserStatus status) {
        User user = findById(userId);
        user.setStatus(status);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<User> findByStatus(UserStatus status) {
        return userRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public long countByStatus(UserStatus status) {
        return userRepository.countByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<User> findPendingUsers() {
        return userRepository.findByStatus(UserStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAllByOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: id=" + userId));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email);
    }
}
