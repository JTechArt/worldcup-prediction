package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.domain.enums.SchedulerJobStatus;
import com.worldcup.prediction.domain.enums.SchedulerJobType;
import com.worldcup.prediction.service.SchedulerLogService;
import com.worldcup.prediction.service.SchedulerRunnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/schedulers")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminSchedulerController {

    private final SchedulerLogService logService;
    private final SchedulerRunnerService runnerService;

    @GetMapping
    public String schedulerPage(
            @RequestParam(required = false) String job,
            @RequestParam(required = false) String status,
            Model model) {
        SchedulerJobStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try { statusFilter = SchedulerJobStatus.valueOf(status); } catch (IllegalArgumentException ignored) {}
        }
        model.addAttribute("cards", logService.buildCards());
        model.addAttribute("logs", logService.findAll(job, statusFilter));
        model.addAttribute("jobTypes", SchedulerJobType.values());
        model.addAttribute("statuses", SchedulerJobStatus.values());
        model.addAttribute("jobFilter", job);
        model.addAttribute("statusFilter", status);
        return "admin/schedulers";
    }

    @PostMapping("/run/{jobName}")
    public String runJob(@PathVariable String jobName, RedirectAttributes ra) {
        try {
            SchedulerJobType jobType = SchedulerJobType.valueOf(jobName);
            String result = runnerService.run(jobType);
            ra.addFlashAttribute("successMessage", "Triggered " + jobType.getDisplayName() + " — " + result);
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("errorMessage", "Unknown job: " + jobName);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Job failed: " + e.getMessage());
        }
        return "redirect:/admin/schedulers";
    }
}
