package com.jllado.weightcontrol.api;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VersionController {
    private final String sourceTree;

    public VersionController() throws IOException {
        sourceTree = new ClassPathResource("release-tree.txt").getContentAsString(StandardCharsets.UTF_8).strip();
    }

    @GetMapping("/api/version")
    public ResponseEntity<Map<String, String>> version() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("sourceTree", sourceTree));
    }
}
