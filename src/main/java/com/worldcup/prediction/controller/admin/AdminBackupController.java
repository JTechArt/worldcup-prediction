package com.worldcup.prediction.controller.admin;

import com.worldcup.prediction.service.DatabaseBackupService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Controller
@RequestMapping("/admin/backup")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminBackupController {

    private final DatabaseBackupService backupService;

    @GetMapping
    public String backupPage(Model model) {
        model.addAttribute("isSqlite", backupService.isSqlite());
        model.addAttribute("dbPath", backupService.getDbPath());
        return "admin/backup";
    }

    @GetMapping("/download")
    public void download(HttpServletResponse response) throws Exception {
        if (!backupService.isSqlite()) {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Backup only supported for SQLite");
            return;
        }
        Path backupFile = null;
        try {
            backupFile = backupService.createBackup();
            String filename = "worldcup-backup-"
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                    + ".db";
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setContentLengthLong(Files.size(backupFile));
            Files.copy(backupFile, response.getOutputStream());
            response.getOutputStream().flush();
            log.info("Backup file sent: {}", filename);
        } finally {
            if (backupFile != null) {
                Files.deleteIfExists(backupFile);
            }
        }
    }

    @PostMapping("/restore")
    public String restore(@RequestParam MultipartFile file,
                          RedirectAttributes redirectAttributes) {
        if (!backupService.isSqlite()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Restore is only supported for SQLite databases.");
            return "redirect:/admin/backup";
        }
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please select a backup file to restore.");
            return "redirect:/admin/backup";
        }
        if (!file.getOriginalFilename().endsWith(".db")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid file type. Please upload a .db file.");
            return "redirect:/admin/backup";
        }
        try {
            backupService.restore(file.getInputStream());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Database restored successfully. Active sessions have been refreshed. "
                    + "If you experience any issues, restart the application.");
        } catch (IOException e) {
            log.error("Restore failed: invalid file", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Restore failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("Restore failed", e);
            redirectAttributes.addFlashAttribute("errorMessage", "Restore failed: " + e.getMessage());
        }
        return "redirect:/admin/backup";
    }
}
