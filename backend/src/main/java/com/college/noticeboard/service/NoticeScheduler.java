package com.college.noticeboard.service;

import com.college.noticeboard.service.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Publishes scheduled notices when due and expires published notices past their expiry date. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeScheduler {

    private final NoticeService noticeService;

    @Scheduled(fixedDelayString = "${app.scheduler.interval-ms:60000}", initialDelay = 10000)
    public void run() {
        try {
            int[] result = noticeService.processDueNotices(LocalDateTime.now());
            if (result[0] > 0 || result[1] > 0) {
                log.info("Scheduler: published {} notice(s), expired {} notice(s)", result[0], result[1]);
            }
        } catch (Exception e) {
            log.error("Notice scheduler run failed", e);
        }
    }
}
