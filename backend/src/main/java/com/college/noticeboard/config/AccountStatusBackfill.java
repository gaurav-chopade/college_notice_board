package com.college.noticeboard.config;

import com.college.noticeboard.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time upgrade for databases created before accounts had a status: active users become
 * APPROVED and inactive ones DEACTIVATED. Does nothing once every row has a status.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AccountStatusBackfill implements ApplicationRunner {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int n = userRepository.backfillStatus();
        if (n > 0) {
            log.info("Set account status on {} existing user(s)", n);
        }
    }
}
