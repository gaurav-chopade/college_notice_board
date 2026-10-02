package com.college.noticeboard.dto;

import java.util.List;
import java.util.Map;

public record DashboardResponse(String role,
                                Map<String, Long> counts,
                                Map<String, Long> breakdown,
                                List<NoticeDtos.NoticeSummaryResponse> recentNotices,
                                List<NoticeDtos.NoticeStatsResponse> readStatistics) {
}
