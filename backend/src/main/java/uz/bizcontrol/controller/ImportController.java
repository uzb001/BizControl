package uz.bizcontrol.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import uz.bizcontrol.security.BizControlPrincipal;
import uz.bizcontrol.service.ImportService;
import uz.bizcontrol.service.PermissionService;

import java.util.Map;

@RestController
@RequestMapping("/import")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService importService;
    private final PermissionService permissionService;

    /**
     * POST /api/import/preview
     * Parses the file, applies column mapping, validates rows, returns preview.
     * Does NOT create any records.
     */
    @PostMapping("/preview")
    public ResponseEntity<?> preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("module") String module,
            @RequestParam Map<String, String> allParams,
            @AuthenticationPrincipal BizControlPrincipal principal
    ) throws Exception {
        permissionService.require(principal, "import.upload");
        Map<String, String> columnMapping = extractMapping(allParams);
        var result = importService.preview(file, module, columnMapping,
                principal.getCompanyId(), principal.getUserId());
        return ResponseEntity.ok(result);
    }

    /**
     * POST /api/import/confirm
     * Re-parses the file, applies mapping, creates records in DB.
     * Returns success/failure counts.
     */
    @PostMapping("/confirm")
    public ResponseEntity<?> confirm(
            @RequestParam("file") MultipartFile file,
            @RequestParam("module") String module,
            @RequestParam("batchId") Long batchId,
            @RequestParam(value = "skipDuplicates", defaultValue = "true") boolean skipDuplicates,
            @RequestParam Map<String, String> allParams,
            @AuthenticationPrincipal BizControlPrincipal principal
    ) throws Exception {
        permissionService.require(principal, "import.upload");
        Map<String, String> columnMapping = extractMapping(allParams);
        var result = importService.confirm(batchId, file, module, columnMapping,
                skipDuplicates, principal.getCompanyId(), principal.getUserId());
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/import/history
     * Returns list of all import batches for this company.
     */
    @GetMapping("/history")
    public ResponseEntity<?> history(@AuthenticationPrincipal BizControlPrincipal principal) {
        permissionService.require(principal, "import.view");
        return ResponseEntity.ok(importService.history(principal.getCompanyId()));
    }

    /**
     * POST /api/import/{id}/rollback
     * Deletes all records created by a confirmed import batch.
     */
    @PostMapping("/{id}/rollback")
    public ResponseEntity<?> rollback(
            @PathVariable Long id,
            @AuthenticationPrincipal BizControlPrincipal principal
    ) throws Exception {
        permissionService.require(principal, "import.rollback");
        return ResponseEntity.ok(importService.rollback(id, principal.getCompanyId()));
    }

    private Map<String, String> extractMapping(Map<String, String> params) {
        Map<String, String> mapping = new java.util.LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (key.startsWith("map_")) {
                mapping.put(key.substring(4), value); // strip "map_" prefix
            }
        });
        return mapping;
    }
}
