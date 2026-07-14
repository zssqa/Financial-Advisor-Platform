package com.finance.advisor.portfolio.goal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * GoalService 单元测试：mock GoalRepository + JdbcTemplate。
 */
class GoalServiceTest {

    private GoalRepository goalRepository;
    private JdbcTemplate jdbcTemplate;
    private GoalService service;

    @BeforeEach
    void setUp() {
        goalRepository = mock(GoalRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new GoalService(goalRepository, jdbcTemplate);
    }

    private Goal goal(String type, String target, String current, LocalDate deadline) {
        Goal g = new Goal();
        g.setType(type);
        g.setTargetAmount(target != null ? new BigDecimal(target) : null);
        g.setCurrentAmount(current != null ? new BigDecimal(current) : null);
        g.setDeadline(deadline);
        return g;
    }

    @Test
    void list_returnsOnlyCurrentUserGoals() {
        Goal g = goal("retirement", "10000", "1000", null);
        when(goalRepository.findByUserId(1L)).thenReturn(List.of(g));
        when(goalRepository.findByUserId(2L)).thenReturn(List.of());

        assertEquals(1, service.list(1L).size());
        assertTrue(service.list(2L).isEmpty());
    }

    @Test
    void create_bindsUserIdAndResetsId() {
        Goal input = goal("retirement", "10000", "0", null);
        input.setId(888L);
        when(goalRepository.save(any(Goal.class))).thenAnswer(inv -> {
            Goal g = inv.getArgument(0);
            g.setId(5L);
            return g;
        });

        Goal created = service.create(1L, input);

        assertEquals(Long.valueOf(1L), created.getUserId());
        assertEquals(Long.valueOf(5L), created.getId());
        verify(goalRepository).save(argThat(g -> Long.valueOf(1L).equals(g.getUserId())));
    }

    @Test
    void summary_computesProgressAndMonthlyNeeded() {
        LocalDate deadline = LocalDate.now().plusMonths(10);
        Goal g = goal("retirement", "10000", "2500", deadline);
        when(goalRepository.findByUserId(1L)).thenReturn(List.of(g));

        GoalSummary summary = service.summary(1L);
        assertEquals(1, summary.getGoals().size());
        GoalSummary.GoalProgress p = summary.getGoals().get(0);

        // progressPercent = 2500/10000*100 = 25.00
        assertEquals(0, p.getProgressPercent().compareTo(new BigDecimal("25.00")));
        // remaining = 7500
        assertEquals(0, p.getRemainingAmount().compareTo(new BigDecimal("7500")));

        long expectedMonths = ChronoUnit.MONTHS.between(LocalDate.now(), deadline);
        assertEquals(expectedMonths, p.getMonthsRemaining());
        if (expectedMonths > 0) {
            BigDecimal expectedMonthly = new BigDecimal("7500")
                    .divide(BigDecimal.valueOf(expectedMonths), 2, RoundingMode.HALF_UP);
            assertEquals(0, p.getMonthlyNeeded().compareTo(expectedMonthly));
        }
    }

    @Test
    void summary_deadlinePassedAndTargetZero_doesNotThrow() {
        Goal past = goal("house", "1000", "100", LocalDate.now().minusMonths(2));
        Goal zeroTarget = goal("custom", "0", "0", null);
        when(goalRepository.findByUserId(1L)).thenReturn(List.of(past, zeroTarget));

        GoalSummary summary = assertDoesNotThrow(() -> service.summary(1L));
        assertEquals(2, summary.getGoals().size());

        // 过期 deadline：monthsRemaining=0，progress=10.00，remaining=900
        GoalSummary.GoalProgress pastP = summary.getGoals().get(0);
        assertEquals(0, pastP.getProgressPercent().compareTo(new BigDecimal("10.00")));
        assertEquals(0, pastP.getRemainingAmount().compareTo(new BigDecimal("900")));
        assertEquals(0L, pastP.getMonthsRemaining());

        // targetAmount=0：progress=0，remaining=0，不抛异常
        GoalSummary.GoalProgress zeroP = summary.getGoals().get(1);
        assertEquals(0, zeroP.getProgressPercent().compareTo(BigDecimal.ZERO));
        assertEquals(0, zeroP.getRemainingAmount().compareTo(BigDecimal.ZERO));
    }
}
