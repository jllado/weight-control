package com.jllado.weightcontrol.api;

import com.jllado.weightcontrol.service.ProgressPhotoService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chatgpt-files/progress-photos")
public class ChatGptFileController {

    private final ProgressPhotoService progressPhotoService;

    public ChatGptFileController(ProgressPhotoService progressPhotoService) {
        this.progressPhotoService = progressPhotoService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<Resource> getProgressPhoto(@PathVariable String token) {
        ProgressPhotoService.ProgressPhotoFile file = progressPhotoService.getFile(token);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(file.mediaType())
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(file.filename()).build().toString()
            )
            .body(file.resource());
    }
}
